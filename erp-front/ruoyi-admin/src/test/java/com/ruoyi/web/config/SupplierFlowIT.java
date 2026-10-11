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
import com.ruoyi.erp.supplier.service.SupplierService;

import static org.junit.jupiter.api.Assertions.*;

/** P1-08.a/P1-09.a：实际权限、HTTP、MyBatis、事务与并发，限 runner 的临时 loopback 实例。 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SupplierFlowIT
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
        schema = "erp_supplier_it_" + UUID.randomUUID().toString().replace("-", "");
        execute(baseUrl, "CREATE DATABASE " + schema + " CHARACTER SET utf8mb4");
        url = baseUrl.replace("/mysql?", "/" + schema + "?");
        // 先恢复上一交付版本，再由真实应用执行本批升级及角色授权。
        Flyway.configure().dataSource(url, "root", password).locations("classpath:db/migration")
                .target("2026101101").load().migrate();
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
                        new org.springframework.core.env.MapPropertySource("isolated-supplier-test", properties))).run();
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
            assertTrue(routes.contains("erp/supplier/index"));
        }
    }

    @AfterAll
    void stop() throws Exception
    {
        if (application != null) application.close();
        if (schema != null) execute(baseUrl, "DROP DATABASE IF EXISTS " + schema);
    }

    @Test
    void permissionsValidationAndDisabledRecordsAreEnforced() throws Exception
    {
        assertEquals(401, call(null, "GET", "/erp/supplier/list", null).getIntValue("code"));
        JSONObject body = fixture("ERP-TEST-contact");
        body.put("createBy", "forged");
        body.put("status", "1");
        body.put("contactPhone", "ERP-TEST-private-contact");
        assertEquals(403, call(viewer, "POST", "/erp/supplier", body).getIntValue("code"));
        JSONObject created = ok(operator, "POST", "/erp/supplier", body).getJSONObject("data");
        long id = created.getLongValue("supplierId");
        assertEquals("erp-op", created.getString("createBy"));
        assertEquals("0", created.getString("status"));
        assertFalse(created.containsKey("requestId"));
        assertFalse(created.containsKey("requestHash"));
        assertEquals(200, call(viewer, "GET", "/erp/supplier/" + id, null).getIntValue("code"));
        assertEquals(200, call(admin, "GET", "/erp/supplier/list", null).getIntValue("code"));
        body.put("version", 0);
        body.put("reason", "ERP-TEST update");
        assertEquals(403, call(viewer, "PUT", "/erp/supplier/" + id, body).getIntValue("code"));
        JSONObject status = JSON.parseObject("{\"status\":\"1\",\"version\":0,\"reason\":\"ERP-TEST stop\"}");
        assertEquals(403, call(viewer, "PUT", "/erp/supplier/" + id + "/status", status).getIntValue("code"));
        assertEquals("1", ok(operator, "PUT", "/erp/supplier/" + id + "/status", status)
                .getJSONObject("data").getString("status"));
        assertEquals("1", ok(viewer, "GET", "/erp/supplier/" + id, null).getJSONObject("data").getString("status"));
        assertEquals(2, ok(viewer, "GET", "/erp/supplier/" + id + "/changes", null).getIntValue("total"));
        assertEquals(500, call(operator, "PUT", "/erp/supplier/" + id + "/status", status).getIntValue("code"));
        status.put("version", 1);
        status.put("status", "0");
        assertEquals("0", ok(operator, "PUT", "/erp/supplier/" + id + "/status", status)
                .getJSONObject("data").getString("status"));
        body.put("supplierName", " ");
        assertEquals(500, call(operator, "POST", "/erp/supplier", body).getIntValue("code"));
        body.put("supplierName", "x".repeat(201));
        assertEquals(500, call(operator, "POST", "/erp/supplier", body).getIntValue("code"));
        status.put("status", "9");
        assertEquals(500, call(operator, "PUT", "/erp/supplier/" + id + "/status", status).getIntValue("code"));
        assertEquals(500, call(viewer, "GET", "/erp/supplier/9223372036854775807", null).getIntValue("code"));
        assertEquals(500, call(viewer, "GET", "/erp/supplier/list?status=9", null).getIntValue("code"));
        status.put("status", "0");
        status.put("version", Long.MAX_VALUE);
        assertEquals(500, call(operator, "PUT", "/erp/supplier/" + id + "/status", status).getIntValue("code"));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (scalar("SELECT COUNT(*) FROM sys_oper_log WHERE oper_url='/erp/supplier' AND status=0") == 0
                && System.nanoTime() < deadline) Thread.sleep(50);
        assertTrue(scalar("SELECT COUNT(*) FROM sys_oper_log WHERE oper_url='/erp/supplier' AND status=0") > 0);
        assertEquals(0, scalar("SELECT COUNT(*) FROM sys_oper_log WHERE oper_url LIKE '/erp/supplier%' "
                + "AND (COALESCE(oper_param,'')<>'' OR COALESCE(json_result,'')<>'' "
                + "OR error_msg LIKE '%ERP-TEST-private-contact%')"));
    }

    @Test
    void concurrentCreateAndRetryAfterEditDoNotDuplicateDataOrAudit() throws Exception
    {
        JSONObject body = fixture("ERP-TEST-concurrent");
        List<JSONObject> results = concurrently(6, () -> call(operator, "POST", "/erp/supplier", body));
        Set<Long> ids = new HashSet<>();
        for (JSONObject result : results)
        {
            assertEquals(200, result.getIntValue("code"), result.getString("msg"));
            ids.add(result.getJSONObject("data").getLongValue("supplierId"));
        }
        assertEquals(1, ids.size());
        long id = ids.iterator().next();
        assertEquals(1, scalar("SELECT COUNT(*) FROM erp_supplier_change WHERE supplier_id=" + id));
        JSONObject edit = JSON.parseObject(body.toJSONString());
        edit.put("supplierName", "ERP-TEST-edited");
        edit.put("version", 0);
        edit.put("reason", "ERP-TEST correction");
        ok(operator, "PUT", "/erp/supplier/" + id, edit);
        assertEquals("ERP-TEST-edited", ok(operator, "POST", "/erp/supplier", body).getJSONObject("data").getString("supplierName"));
        assertEquals(2, scalar("SELECT COUNT(*) FROM erp_supplier_change WHERE supplier_id=" + id));
        body.put("supplierName", "ERP-TEST-conflicting-request");
        assertEquals(500, call(operator, "POST", "/erp/supplier", body).getIntValue("code"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM erp_supplier WHERE supplier_name='ERP-TEST-conflicting-request'"));
    }

    @Test
    void competingEditsHaveOneWinnerAndOneAudit() throws Exception
    {
        JSONObject body = fixture("ERP-TEST-version");
        long id = ok(operator, "POST", "/erp/supplier", body).getJSONObject("data").getLongValue("supplierId");
        body.put("supplierName", "ERP-TEST-version-new");
        body.put("version", 0);
        body.put("reason", "ERP-TEST concurrent edit");
        List<JSONObject> results = concurrently(2, () -> call(operator, "PUT", "/erp/supplier/" + id, body));
        assertEquals(1, results.stream().filter(result -> result.getIntValue("code") == 200).count());
        assertEquals(1, results.stream().filter(result -> result.getIntValue("code") == 500).count());
        assertEquals(1, scalar("SELECT version FROM erp_supplier WHERE supplier_id=" + id));
        assertEquals(2, scalar("SELECT COUNT(*) FROM erp_supplier_change WHERE supplier_id=" + id));
    }

    @Test
    void auditFailureRollsBackBusinessChange() throws Exception
    {
        JSONObject body = fixture("ERP-TEST-rollback");
        long id = ok(operator, "POST", "/erp/supplier", body).getJSONObject("data").getLongValue("supplierId");
        execute(url, "CREATE TRIGGER supplier_audit_failure BEFORE INSERT ON erp_supplier_change FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ERP-TEST audit unavailable'");
        try
        {
            body.put("supplierName", "ERP-TEST-should-rollback");
            body.put("version", 0);
            body.put("reason", "ERP-TEST rollback");
            JSONObject failure = call(operator, "PUT", "/erp/supplier/" + id, body);
            assertEquals(500, failure.getIntValue("code"));
            assertEquals("供应商数据操作失败，请稍后重试或联系管理员", failure.getString("msg"));
            JSONObject newSupplier = fixture("ERP-TEST-create-rollback");
            assertEquals(500, call(operator, "POST", "/erp/supplier", newSupplier).getIntValue("code"));
            assertEquals(0, scalar("SELECT COUNT(*) FROM erp_supplier WHERE supplier_name='ERP-TEST-create-rollback'"));
            JSONObject status = JSON.parseObject("{\"status\":\"1\",\"version\":0,\"reason\":\"ERP-TEST rollback\"}");
            assertEquals(500, call(operator, "PUT", "/erp/supplier/" + id + "/status", status).getIntValue("code"));
            assertEquals("ERP-TEST-rollback", ok(viewer, "GET", "/erp/supplier/" + id, null).getJSONObject("data").getString("supplierName"));
            assertEquals(0, scalar("SELECT status FROM erp_supplier WHERE supplier_id=" + id));
            assertEquals(0, scalar("SELECT version FROM erp_supplier WHERE supplier_id=" + id));
            assertEquals(1, scalar("SELECT COUNT(*) FROM erp_supplier_change WHERE supplier_id=" + id));
        }
        finally
        {
            execute(url, "DROP TRIGGER supplier_audit_failure");
        }
        ok(operator, "PUT", "/erp/supplier/" + id, body);
    }

    @Test
    void utcStorageDoesNotFollowConnectionTimezone() throws Exception
    {
        long before = System.currentTimeMillis();
        long id = ok(operator, "POST", "/erp/supplier", fixture("ERP-TEST-time")).getJSONObject("data").getLongValue("supplierId");
        long actual = application.getBean(SupplierService.class).get(id).getCreateTime().getTime();
        assertTrue(actual >= before && actual <= System.currentTimeMillis());
        assertTrue(scalar("SELECT ABS(TIMESTAMPDIFF(SECOND,create_time,UTC_TIMESTAMP())) FROM erp_supplier WHERE supplier_id=" + id) < 5);
    }

    @Test
    void paginationAndDatabaseConstraintsAreEnforced() throws Exception
    {
        for (int index = 0; index < 105; index++)
        {
            execute(url, "INSERT INTO erp_supplier(supplier_name,request_id,request_hash,create_by,create_time) VALUES"
                    + "('ERP-TEST-page','" + UUID.randomUUID() + "',REPEAT('0',64),'fixture',UTC_TIMESTAMP(3))");
        }
        String path = "/erp/supplier/list?supplierName=ERP-TEST-page";
        assertEquals(10, ok(viewer, "GET", path, null).getJSONArray("rows").size());
        assertEquals(100, ok(viewer, "GET", path + "&pageSize=1000", null).getJSONArray("rows").size());
        assertEquals(10, ok(viewer, "GET", path + "&pageSize=0", null).getJSONArray("rows").size());
        String sort = "&pageSize=1&orderByColumn=supplierName&isAsc=ascending";
        long first = ok(viewer, "GET", path + sort + "&pageNum=1", null).getJSONArray("rows").getJSONObject(0).getLongValue("supplierId");
        long second = ok(viewer, "GET", path + sort + "&pageNum=2", null).getJSONArray("rows").getJSONObject(0).getLongValue("supplierId");
        assertTrue(first < second);
        assertThrows(java.sql.SQLException.class, () -> execute(url, "UPDATE erp_supplier SET status='9' WHERE supplier_id=" + first));
        assertThrows(java.sql.SQLException.class, () -> execute(url, "UPDATE erp_supplier SET version=-1 WHERE supplier_id=" + first));
        assertEquals(0, Flyway.configure().dataSource(url, "root", password).locations("classpath:db/migration").load().migrate().migrationsExecuted);
    }

    private JSONObject fixture(String name)
    {
        JSONObject body = new JSONObject();
        body.put("requestId", UUID.randomUUID().toString());
        body.put("supplierName", name);
        body.put("settlementTerms", "ERP-TEST facts only");
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
