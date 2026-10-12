package com.ruoyi.web.config;

import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.RuoYiApplication;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.erp.finance.service.ExchangeRateService;

import static org.junit.jupiter.api.Assertions.*;

/** P1-18.a：实际权限、十进制、DATE、HTTP、MyBatis、事务与并发，仅限临时loopback实例。 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExchangeRateFlowIT
{
    @TempDir static Path temporary;
    private String baseUrl;
    private String password;
    private String schema;
    private String url;
    private String endpoint;
    private String admin;
    private String operator;
    private String viewer;
    private ConfigurableApplicationContext application;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeAll
    void start() throws Exception
    {
        baseUrl = System.getenv("ERP_MIGRATION_TEST_URL");
        password = System.getenv("ERP_MIGRATION_TEST_PASSWORD");
        assertNotNull(baseUrl);
        assertNotNull(password);
        assertTrue(baseUrl.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/mysql\\?.*"));
        schema = "erp_rate_it_" + UUID.randomUUID().toString().replace("-", "");
        execute(baseUrl, "CREATE DATABASE " + schema + " CHARACTER SET utf8mb4");
        url = baseUrl.replace("/mysql?", "/" + schema + "?");
        // 先恢复上一交付版本，再由真实应用执行本批升级及角色授权。
        Flyway.configure().dataSource(url, "root", password).locations("classpath:db/migration")
                .target("2026101103").load().migrate();
        execute(url, "INSERT INTO sys_role(role_id,role_name,role_key,role_sort,status) VALUES"
                + "(3,'ERP-TEST-operator','erp_operator',3,'0'),(4,'ERP-TEST-viewer','erp_viewer',4,'0')");
        try (var connection = DriverManager.getConnection(url, "root", password);
                var statement = connection.prepareStatement("INSERT INTO sys_user"
                        + "(user_id,user_name,nick_name,password,status,del_flag) VALUES(?,?,?,?,'0','0')"))
        {
            for (int id = 3; id <= 4; id++)
            {
                statement.setInt(1, id);
                statement.setString(2, id == 3 ? "erp-op" : "erp-view");
                statement.setString(3, "ERP-TEST");
                statement.setString(4, SecurityUtils.encryptPassword("ERP-test-valid"));
                statement.executeUpdate();
            }
        }
        execute(url, "INSERT INTO sys_user_role VALUES(3,3),(4,4)");
        execute(url, "UPDATE sys_job SET status='1'");
        Map<String, Object> properties = new HashMap<>();
        // 故意使用非UTC连接时区，验证专用UTC DATETIME handler。
        properties.put("spring.datasource.druid.master.url", url.replace("serverTimezone=UTC", "serverTimezone=Asia/Tokyo"));
        properties.put("spring.datasource.druid.master.username", "root");
        properties.put("spring.datasource.druid.master.password", password);
        properties.put("spring.flyway.enabled", true);
        properties.put("spring.data.redis.host", "127.0.0.1");
        String redisPort = System.getenv("ERP_LOGIN_TEST_REDIS_PORT");
        assertNotNull(redisPort);
        properties.put("spring.data.redis.port", redisPort);
        byte[] secret = new byte[64];
        new java.security.SecureRandom().nextBytes(secret);
        properties.put("token.secret", Base64.getEncoder().encodeToString(secret));
        properties.put("server.address", "127.0.0.1");
        properties.put("server.port", 0);
        properties.put("ruoyi.profile", temporary.toString());
        properties.put("logging.level.com.ruoyi", "INFO");
        properties.put("spring.devtools.restart.enabled", false);
        properties.put("spring.main.banner-mode", "off");
        application = new SpringApplicationBuilder(RuoYiApplication.class).initializers(context ->
                context.getEnvironment().getPropertySources().addFirst(
                        new org.springframework.core.env.MapPropertySource("isolated-exchange-rate-test", properties))).run();
        var stat = application.getBean("masterDataSource", com.alibaba.druid.pool.DruidDataSource.class)
                .getProxyFilters().stream().filter(com.alibaba.druid.filter.stat.StatFilter.class::isInstance)
                .map(com.alibaba.druid.filter.stat.StatFilter.class::cast).findFirst().orElseThrow();
        assertFalse(stat.isLogSlowSql(), "慢SQL日志不得记录联系方式等绑定参数");
        endpoint = "http://127.0.0.1:" + ((WebServerApplicationContext) application).getWebServer().getPort();
        // 管理员使用标准基线密码，其他两个为独立合成业务账号。
        admin = login("admin", "admin123");
        operator = login("erp-op", "ERP-test-valid");
        viewer = login("erp-view", "ERP-test-valid");
        for (String token : List.of(admin, operator, viewer))
        {
            String routes = ok(token, "GET", "/getRouters", null).getJSONArray("data").toJSONString();
            assertTrue(routes.contains("erp/exchange-rate/index"));
        }
    }

    @AfterAll
    void stop() throws Exception
    {
        if (application != null) application.close();
        if (schema != null) execute(baseUrl, "DROP DATABASE IF EXISTS " + schema);
    }

    @Test
    void permissionsExactDateAndStringDecimalsAreEnforced() throws Exception
    {
        assertEquals(401, call(null, "GET", "/erp/exchange-rate/list", null).getIntValue("code"));
        JSONObject body = fixture("2040-01-01", "7.50000000");
        body.put("createBy", "forged");
        assertEquals(403, call(viewer, "POST", "/erp/exchange-rate", body).getIntValue("code"));
        JSONObject created = ok(operator, "POST", "/erp/exchange-rate", body).getJSONObject("data");
        long id = created.getLongValue("rateId");
        assertEquals("erp-op", created.getString("createBy"));
        assertEquals("2040-01-01", created.getString("rateDate"));
        assertInstanceOf(String.class, created.get("rateId"));
        assertInstanceOf(String.class, created.get("version"));
        assertInstanceOf(String.class, created.get("usdCny"));
        assertEquals("7.50000000", created.getString("usdCny"));
        assertFalse(created.containsKey("requestId"));
        assertFalse(created.containsKey("requestHash"));
        ok(admin, "GET", "/erp/exchange-rate/list", null);
        ok(viewer, "GET", "/erp/exchange-rate/" + id, null);
        JSONObject preview = conversion("USD", "0.01", "2040-01-01");
        JSONObject snapshot = ok(viewer, "POST", "/erp/exchange-rate/preview", preview).getJSONObject("data");
        assertEquals("0.08", snapshot.getString("cnyAmount"));
        assertInstanceOf(String.class, snapshot.get("cnyAmount"));
        assertEquals("0.01", snapshot.getString("originalAmount"));
        assertEquals("0", snapshot.getString("rateVersion"));
        preview.put("rateDate", "2040-01-02");
        JSONObject missing = call(viewer, "POST", "/erp/exchange-rate/preview", preview);
        assertEquals(500, missing.getIntValue("code"));
        assertTrue(missing.getString("msg").contains("2040-01-02"));
        assertEquals("-1.23", ok(viewer, "POST", "/erp/exchange-rate/preview",
                conversion("CNY", "-1.23", null)).getJSONObject("data").getString("cnyAmount"));
        body.put("version", "0");
        body.put("reason", "ERP-TEST edit");
        assertEquals(403, call(viewer, "PUT", "/erp/exchange-rate/" + id, body).getIntValue("code"));
        body.put("rateDate", "2040-01-02");
        assertEquals(500, call(operator, "PUT", "/erp/exchange-rate/" + id, body).getIntValue("code"));
        body.put("rateDate", "2040-01-01");
        body.put("reason", " ");
        assertEquals(500, call(operator, "PUT", "/erp/exchange-rate/" + id, body).getIntValue("code"));
        body.put("reason", "<script>ERP-TEST</script>");
        assertEquals(500, call(operator, "PUT", "/erp/exchange-rate/" + id, body).getIntValue("code"));
        body.put("reason", "ERP-TEST");
        body.put("version", Long.MAX_VALUE);
        assertEquals(500, call(operator, "PUT", "/erp/exchange-rate/" + id, body).getIntValue("code"));
        assertEquals(500, call(viewer, "GET", "/erp/exchange-rate/list?from=2040-02-30", null).getIntValue("code"));
        assertEquals(500, call(viewer, "GET", "/erp/exchange-rate/list?from=2040-02-01&to=2040-01-01", null).getIntValue("code"));
        for (String invalid : List.of("0", "-1", "7.123456789", "1e2", "100000000"))
        {
            assertEquals(500, call(operator, "POST", "/erp/exchange-rate", fixture("2040-01-02", invalid)).getIntValue("code"));
        }
        assertEquals(1, scalar("SELECT COUNT(*) FROM erp_exchange_rate WHERE rate_date='2040-01-01'"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM erp_exchange_rate_revision WHERE rate_id=" + id));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (scalar("SELECT COUNT(*) FROM sys_oper_log WHERE oper_url='/erp/exchange-rate' AND status=0") == 0
                && System.nanoTime() < deadline) Thread.sleep(50);
        assertTrue(scalar("SELECT COUNT(*) FROM sys_oper_log WHERE oper_url='/erp/exchange-rate' AND status=0") > 0);
        assertEquals(0, scalar("SELECT COUNT(*) FROM sys_oper_log WHERE oper_url LIKE '/erp/exchange-rate%' "
                + "AND (COALESCE(oper_param,'')<>'' OR COALESCE(json_result,'')<>'')"));
    }

    @Test
    void concurrentCreationRetriesAndDateUniquenessKeepOneOriginalRevision() throws Exception
    {
        JSONObject body = fixture("2050-01-01", "7.12");
        List<JSONObject> results = concurrently(6, () -> call(operator, "POST", "/erp/exchange-rate", body));
        Set<Long> ids = new HashSet<>();
        for (JSONObject result : results)
        {
            assertEquals(200, result.getIntValue("code"), result.getString("msg"));
            ids.add(result.getJSONObject("data").getLongValue("rateId"));
        }
        assertEquals(1, ids.size());
        long id = ids.iterator().next();
        JSONObject edit = JSON.parseObject(body.toJSONString());
        edit.put("usdCny", "8");
        edit.put("version", "0");
        edit.put("reason", "ERP-TEST correction");
        ok(operator, "PUT", "/erp/exchange-rate/" + id, edit);
        assertEquals("8.00000000", ok(operator, "POST", "/erp/exchange-rate", body).getJSONObject("data").getString("usdCny"));
        assertEquals(2, scalar("SELECT COUNT(*) FROM erp_exchange_rate_revision WHERE rate_id=" + id));
        JSONObject original = ok(viewer, "GET", "/erp/exchange-rate/" + id + "/revisions?pageSize=100", null)
                .getJSONArray("rows").getJSONObject(1);
        assertEquals("7.12000000", original.getString("usdCny"));
        assertEquals("0", original.getString("version"));
        assertEquals(500, call(admin, "POST", "/erp/exchange-rate", body).getIntValue("code"));
        body.put("usdCny", "9");
        assertEquals(500, call(operator, "POST", "/erp/exchange-rate", body).getIntValue("code"));
        body.put("rateDate", "2050-01-03");
        assertEquals(500, call(operator, "POST", "/erp/exchange-rate", body).getIntValue("code"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM erp_exchange_rate WHERE rate_date='2050-01-03'"));
        List<JSONObject> dateRace = concurrently(2, () -> call(operator, "POST", "/erp/exchange-rate", fixture("2050-01-02", "7")));
        assertEquals(1, dateRace.stream().filter(result -> result.getIntValue("code") == 200).count());
        assertEquals(1, dateRace.stream().filter(result -> result.getIntValue("code") == 500).count());
        assertEquals(1, scalar("SELECT COUNT(*) FROM erp_exchange_rate_revision WHERE rate_date='2050-01-02'"));
    }

    @Test
    void competingEditsPreserveSnapshotsAndCompleteHistory() throws Exception
    {
        JSONObject body = fixture("2051-01-01", "7.25");
        long id = ok(operator, "POST", "/erp/exchange-rate", body).getJSONObject("data").getLongValue("rateId");
        var service = application.getBean(ExchangeRateService.class);
        var original = service.snapshot(new com.ruoyi.erp.finance.domain.ConversionRequest("USD", "100", "2051-01-01"));
        body.put("usdCny", "8");
        body.put("version", "0");
        body.put("reason", "ERP-TEST concurrent correction");
        List<JSONObject> results = concurrently(2, () -> call(operator, "PUT", "/erp/exchange-rate/" + id, body));
        assertEquals(1, results.stream().filter(result -> result.getIntValue("code") == 200).count());
        assertEquals(1, results.stream().filter(result -> result.getIntValue("code") == 500).count());
        assertEquals(new java.math.BigDecimal("725.00"), original.cnyAmount());
        assertEquals(0L, original.rateVersion());
        var latest = service.snapshot(new com.ruoyi.erp.finance.domain.ConversionRequest("USD", "100", "2051-01-01"));
        assertEquals(new java.math.BigDecimal("800.00"), latest.cnyAmount());
        assertEquals(1L, latest.rateVersion());
        assertEquals(2, scalar("SELECT COUNT(*) FROM erp_exchange_rate_revision WHERE rate_id=" + id));
        assertEquals(1, scalar("SELECT version FROM erp_exchange_rate WHERE rate_id=" + id));
        assertEquals(500, call(operator, "PUT", "/erp/exchange-rate/" + id, body).getIntValue("code"));
    }

    @Test
    void failedRevisionRollsBackCreationAndEditAndSameRequestCanRecover() throws Exception
    {
        JSONObject edit = fixture("2052-01-01", "7");
        long id = ok(operator, "POST", "/erp/exchange-rate", edit).getJSONObject("data").getLongValue("rateId");
        edit.put("usdCny", "8");
        edit.put("version", "0");
        edit.put("reason", "ERP-TEST rollback");
        JSONObject create = fixture("2052-01-02", "7.1");
        execute(url, "CREATE TRIGGER rate_revision_failure BEFORE INSERT ON erp_exchange_rate_revision FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ERP-TEST revision unavailable'");
        try
        {
            JSONObject failure = call(operator, "POST", "/erp/exchange-rate", create);
            assertEquals(500, failure.getIntValue("code"));
            assertEquals("汇率数据操作失败，请稍后重试或联系管理员", failure.getString("msg"));
            assertEquals(500, call(operator, "PUT", "/erp/exchange-rate/" + id, edit).getIntValue("code"));
            assertEquals(0, scalar("SELECT COUNT(*) FROM erp_exchange_rate WHERE rate_date='2052-01-02'"));
            assertEquals(0, scalar("SELECT version FROM erp_exchange_rate WHERE rate_id=" + id));
            assertEquals("7.00000000", ok(viewer, "GET", "/erp/exchange-rate/" + id, null).getJSONObject("data").getString("usdCny"));
            assertEquals(1, scalar("SELECT COUNT(*) FROM erp_exchange_rate_revision WHERE rate_id=" + id));
        }
        finally
        {
            execute(url, "DROP TRIGGER rate_revision_failure");
        }
        ok(operator, "POST", "/erp/exchange-rate", create);
        ok(operator, "PUT", "/erp/exchange-rate/" + id, edit);
        assertEquals(1, scalar("SELECT COUNT(*) FROM erp_exchange_rate_revision WHERE rate_date='2052-01-02'"));
        assertEquals(2, scalar("SELECT COUNT(*) FROM erp_exchange_rate_revision WHERE rate_id=" + id));
    }

    @Test
    void dateUtcPaginationAndMigrationConstraintsAreVerified() throws Exception
    {
        long before = System.currentTimeMillis();
        long id = ok(operator, "POST", "/erp/exchange-rate", fixture("2053-01-01", "0.00000001"))
                .getJSONObject("data").getLongValue("rateId");
        var rate = application.getBean(ExchangeRateService.class).get(id);
        assertEquals(java.time.LocalDate.of(2053, 1, 1), rate.getRateDate());
        assertTrue(rate.getCreateTime().getTime() >= before && rate.getCreateTime().getTime() <= System.currentTimeMillis());
        assertTrue(scalar("SELECT ABS(TIMESTAMPDIFF(SECOND,create_time,UTC_TIMESTAMP())) FROM erp_exchange_rate WHERE rate_id=" + id) < 5);
        for (int index = 0; index < 105; index++)
        {
            execute(url, "INSERT INTO erp_exchange_rate(rate_date,usd_cny,request_id,request_hash,create_by,create_time) "
                    + "VALUES(DATE_ADD('2030-01-01',INTERVAL " + index + " DAY),7,'" + UUID.randomUUID() + "',REPEAT('0',64),'fixture',UTC_TIMESTAMP(3))");
        }
        String path = "/erp/exchange-rate/list?from=2030-01-01&to=2030-12-31";
        assertEquals(105, ok(viewer, "GET", path, null).getIntValue("total"));
        assertEquals(10, ok(viewer, "GET", path, null).getJSONArray("rows").size());
        assertEquals(100, ok(viewer, "GET", path + "&pageSize=1000", null).getJSONArray("rows").size());
        assertEquals(10, ok(viewer, "GET", path + "&pageSize=0", null).getJSONArray("rows").size());
        String sort = "&pageSize=1&orderByColumn=usdCny&isAsc=ascending";
        long first = ok(viewer, "GET", path + sort + "&pageNum=1", null).getJSONArray("rows").getJSONObject(0).getLongValue("rateId");
        long second = ok(viewer, "GET", path + sort + "&pageNum=2", null).getJSONArray("rows").getJSONObject(0).getLongValue("rateId");
        assertTrue(first < second);
        assertThrows(java.sql.SQLException.class, () -> execute(url, "UPDATE erp_exchange_rate SET usd_cny=0 WHERE rate_id=" + id));
        assertThrows(java.sql.SQLException.class, () -> execute(url, "UPDATE erp_exchange_rate SET version=-1 WHERE rate_id=" + id));
        assertEquals(0, Flyway.configure().dataSource(url, "root", password).locations("classpath:db/migration").load().migrate().migrationsExecuted);
    }

    private JSONObject fixture(String date, String rate)
    {
        JSONObject body = new JSONObject();
        body.put("rateDate", date);
        body.put("usdCny", rate);
        body.put("requestId", UUID.randomUUID().toString());
        return body;
    }

    private JSONObject conversion(String currency, String amount, String date)
    {
        JSONObject body = new JSONObject();
        body.put("currency", currency);
        body.put("originalAmount", amount);
        body.put("rateDate", date);
        return body;
    }

    private String login(String username, String userPassword) throws Exception
    {
        JSONObject captcha = call(null, "GET", "/captchaImage", null);
        String uuid = captcha.getString("uuid");
        JSONObject body = new JSONObject();
        body.put("username", username);
        body.put("password", userPassword);
        body.put("uuid", uuid);
        body.put("code", application.getBean(RedisCache.class).getCacheObject(CacheConstants.CAPTCHA_CODE_KEY + uuid));
        String token = ok(null, "POST", "/login", body).getString("token");
        assertNotNull(token);
        return token;
    }

    private JSONObject ok(String token, String method, String path, JSONObject body) throws Exception
    {
        JSONObject result = call(token, method, path, body);
        assertEquals(200, result.getIntValue("code"), result.getString("msg"));
        return result;
    }

    private JSONObject call(String token, String method, String path, JSONObject body) throws Exception
    {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint + path)).timeout(Duration.ofSeconds(15));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body.toJSONString()));
        return JSON.parseObject(http.send(builder.build(), HttpResponse.BodyHandlers.ofString()).body());
    }

    private List<JSONObject> concurrently(int count, Callable<JSONObject> action) throws Exception
    {
        var executor = Executors.newFixedThreadPool(count);
        var start = new CountDownLatch(1);
        try
        {
            List<Future<JSONObject>> tasks = new ArrayList<>();
            for (int index = 0; index < count; index++)
            {
                tasks.add(executor.submit(() -> { assertTrue(start.await(5, TimeUnit.SECONDS)); return action.call(); }));
            }
            start.countDown();
            List<JSONObject> results = new ArrayList<>();
            for (var task : tasks) results.add(task.get(20, TimeUnit.SECONDS));
            return results;
        }
        finally
        {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private void execute(String jdbcUrl, String sql) throws Exception
    {
        try (var connection = DriverManager.getConnection(jdbcUrl, "root", password); var statement = connection.createStatement())
        {
            statement.execute(sql);
        }
    }

    private int scalar(String sql) throws Exception
    {
        try (var connection = DriverManager.getConnection(url, "root", password); var statement = connection.createStatement(); var result = statement.executeQuery(sql))
        {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }
}
