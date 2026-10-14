package com.ruoyi.web.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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

import static org.junit.jupiter.api.Assertions.*;

/** 实际应用、HTTP、MySQL、Redis 登录验收；只使用 runner 创建的临时实例。 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LoginFlowIT
{
    @TempDir static Path temporary;
    private ConfigurableApplicationContext application;
    private String baseUrl;
    private String databasePassword;
    private String schema;
    private String databaseUrl;
    private String endpoint;
    private RedisCache cache;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final String USERNAME = "admin";
    private static final String TEST_PASSWORD = "ERP-test-valid";
    private static final String FAILURE_KEY = CacheConstants.PWD_ERR_CNT_KEY + USERNAME;

    @BeforeAll
    void startApplication() throws Exception
    {
        baseUrl = System.getenv("ERP_MIGRATION_TEST_URL");
        databasePassword = System.getenv("ERP_MIGRATION_TEST_PASSWORD");
        assertNotNull(baseUrl);
        assertTrue(baseUrl.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/mysql\\?.*"));
        String redisPort = System.getenv("ERP_LOGIN_TEST_REDIS_PORT");
        assertNotNull(redisPort);
        schema = "erp_login_it_" + UUID.randomUUID().toString().replace("-", "");
        execute(baseUrl, "CREATE DATABASE " + schema + " CHARACTER SET utf8mb4");
        databaseUrl = baseUrl.replace("/mysql?", "/" + schema + "?");
        org.flywaydb.core.api.configuration.FluentConfiguration configuration =
                org.flywaydb.core.Flyway.configure().locations("classpath:db/migration");
        new DatabaseMigrationConfig().masterDatabaseMigration(
                new DriverManagerDataSource(databaseUrl, "root", databasePassword)).customize(configuration);
        configuration.load().migrate();
        try (var connection = DriverManager.getConnection(databaseUrl, "root", databasePassword);
                var statement = connection.prepareStatement("UPDATE sys_user SET password=? WHERE user_id=1"))
        {
            statement.setString(1, SecurityUtils.encryptPassword(TEST_PASSWORD));
            statement.executeUpdate();
        }
        execute(databaseUrl, "UPDATE sys_job SET status='1'");
        execute(databaseUrl, "UPDATE sys_user SET nick_name='ERP-TEST-tie' WHERE user_id IN(1,2)");
        try (var connection = DriverManager.getConnection(databaseUrl, "root", databasePassword);
                var statement = connection.prepareStatement("INSERT INTO sys_user "
                        + "(user_name,nick_name,password,status,del_flag) VALUES(?,'ERP-TEST-tie',?,'0','0')"))
        {
            String fixtureHash = SecurityUtils.encryptPassword(TEST_PASSWORD);
            for (int i = 0; i < 101; i++)
            {
                statement.setString(1, "ERP-TEST-" + i);
                statement.setString(2, fixtureHash);
                statement.addBatch();
            }
            statement.executeBatch();
        }
        execute(databaseUrl, "INSERT INTO sys_role(role_name,role_key,role_sort,data_scope,status) "
                + "VALUES('ERP-TEST-reader','erp_viewer',99,'1','0')");
        execute(databaseUrl, "UPDATE sys_user_role SET role_id=(SELECT role_id FROM sys_role "
                + "WHERE role_key='erp_viewer') WHERE user_id=2");
        try (var connection = DriverManager.getConnection(databaseUrl, "root", databasePassword);
                var statement = connection.prepareStatement(
                        "UPDATE sys_user SET user_name='erp-reader',password=? WHERE user_id=2"))
        {
            statement.setString(1, SecurityUtils.encryptPassword(TEST_PASSWORD));
            statement.executeUpdate();
        }
        var properties = new HashMap<String, Object>();
        properties.put("spring.datasource.druid.master.url", databaseUrl);
        properties.put("spring.datasource.druid.master.username", "root");
        properties.put("spring.datasource.druid.master.password", databasePassword);
        properties.put("spring.datasource.druid.initialSize", 1);
        properties.put("spring.datasource.druid.minIdle", 1);
        properties.put("spring.datasource.druid.stat-view-servlet.enabled", false);
        properties.put("spring.flyway.enabled", true);
        properties.put("spring.data.redis.host", "127.0.0.1");
        properties.put("spring.data.redis.port", redisPort);
        properties.put("spring.data.redis.database", 0);
        properties.put("spring.data.redis.password", "");
        byte[] secret = new byte[64];
        new java.security.SecureRandom().nextBytes(secret);
        properties.put("token.secret", Base64.getEncoder().encodeToString(secret));
        properties.put("server.address", "127.0.0.1");
        properties.put("server.port", 0);
        properties.put("user.password.lockTime", 1);
        properties.put("ruoyi.profile", temporary.toString());
        properties.put("logging.level.com.ruoyi", "INFO");
        properties.put("spring.devtools.restart.enabled", false);
        properties.put("spring.main.banner-mode", "off");
        // 用高优先级属性，不能被仓库 YAML 中的默认值覆盖。
        application = new SpringApplicationBuilder(RuoYiApplication.class).initializers(context ->
                context.getEnvironment().getPropertySources().addFirst(
                        new org.springframework.core.env.MapPropertySource("isolated-login-test", properties)))
                .run();
        endpoint = "http://127.0.0.1:" + ((WebServerApplicationContext) application).getWebServer().getPort();
        cache = application.getBean(RedisCache.class);
    }

    @AfterAll
    void stopApplication() throws Exception
    {
        if (application != null)
        {
            application.close();
        }
        if (schema != null)
        {
            execute(baseUrl, "DROP DATABASE IF EXISTS " + schema);
        }
    }

    @Test
    void captchaLockExpirySuccessfulResetAndDisabledAccountAreEnforced() throws Exception
    {
        // 错误或缺失验证码不改变真实 Redis 密码失败计数。
        assertEquals(500, login(TEST_PASSWORD, "wrong", false).getIntValue("code"));
        assertNull(cache.getCacheObject(FAILURE_KEY));
        assertEquals(500, login(TEST_PASSWORD, null, false).getIntValue("code"));
        assertNull(cache.getCacheObject(FAILURE_KEY));
        verifyCaptchaCannotBeReusedConcurrently();
        cache.deleteObject(FAILURE_KEY); // 独立场景的合成测试状态，随后从零验证五次锁定。
        for (int attempt = 1; attempt <= 5; attempt++)
        {
            assertEquals(500, loginAs(attempt % 2 == 0 ? "ADMIN" : USERNAME,
                    "ERP-test-wrong", null, true).getIntValue("code"));
            assertEquals(attempt, ((Number) cache.getCacheObject(FAILURE_KEY)).intValue());
        }
        Long ttl = cache.getExpire(FAILURE_KEY);
        assertTrue(ttl > 0 && ttl <= 60);
        assertEquals(500, login(TEST_PASSWORD, null, true).getIntValue("code"));
        // 使用真实 Redis 到期机制缩短测试等待，不修改应用的业务锁定配置。
        cache.expire(FAILURE_KEY, 1, TimeUnit.SECONDS);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (cache.getCacheObject(FAILURE_KEY) != null && System.nanoTime() < deadline)
        {
            Thread.sleep(50);
        }
        assertNull(cache.getCacheObject(FAILURE_KEY));
        JSONObject successful = login(TEST_PASSWORD, null, true);
        assertEquals(200, successful.getIntValue("code"));
        assertNotNull(successful.getString("token"));
        HttpResponse<String> info = http.send(HttpRequest.newBuilder(URI.create(endpoint + "/getInfo"))
                .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + successful.getString("token"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, JSON.parseObject(info.body()).getIntValue("code"));
        verifyPagedQueries(successful.getString("token"));
        assertEquals(500, login("ERP-test-wrong", null, true).getIntValue("code"));
        assertEquals(1, ((Number) cache.getCacheObject(FAILURE_KEY)).intValue());
        assertEquals(200, login(TEST_PASSWORD, null, true).getIntValue("code"));
        assertNull(cache.getCacheObject(FAILURE_KEY));
        verifyConcurrentFailureCounting();
        assertFalse(cache.deleteBelowLimit(FAILURE_KEY, 5));
        String viewerToken = loginAs("erp-reader", TEST_PASSWORD, null, true).getString("token");
        assertNotNull(viewerToken);
        assertEquals(403, unlock(viewerToken, 1).getIntValue("code"));
        assertEquals(5, ((Number) cache.getCacheObject(FAILURE_KEY)).intValue());
        String adminToken = successful.getString("token");
        assertEquals(200, unlock(adminToken, 1).getIntValue("code"));
        assertNull(cache.getCacheObject(FAILURE_KEY));
        assertEquals(200, unlock(adminToken, 1).getIntValue("code"));
        execute(databaseUrl, "UPDATE sys_user SET status='1' WHERE user_id=2");
        String viewerFailureKey = CacheConstants.PWD_ERR_CNT_KEY + "erp-reader";
        cache.setCacheObject(viewerFailureKey, 5, 1, TimeUnit.MINUTES);
        assertEquals(200, unlock(adminToken, 2).getIntValue("code"));
        assertEquals(500, loginAs("erp-reader", TEST_PASSWORD, null, true).getIntValue("code"));
        assertNull(cache.getCacheObject(viewerFailureKey));
        try (var connection = DriverManager.getConnection(databaseUrl, "root", databasePassword);
                var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT status FROM sys_user WHERE user_id=2"))
        {
            assertTrue(result.next());
            assertEquals("1", result.getString(1));
        }
        assertEquals(500, unlock(adminToken, Long.MAX_VALUE).getIntValue("code"));
        long auditDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        int auditCount = 0;
        while (auditCount == 0 && System.nanoTime() < auditDeadline)
        {
            try (var connection = DriverManager.getConnection(databaseUrl, "root", databasePassword);
                    var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT COUNT(*) FROM sys_oper_log "
                            + "WHERE title='解除账号临时锁定' AND oper_name='admin' AND status=0"))
            {
                assertTrue(result.next());
                auditCount = result.getInt(1);
            }
            if (auditCount == 0)
            {
                Thread.sleep(50);
            }
        }
        assertTrue(auditCount > 0, "Successful administrator unlock must be audited");
    }

    @Test
    void administratorResetRejectsInvalidPasswordsWithoutWritingAndValidBoundariesCanLogin() throws Exception
    {
        cache.deleteObject(FAILURE_KEY);
        String adminToken = login(TEST_PASSWORD, null, true).getString("token");
        long userId = fixtureUserId("ERP-TEST-0");
        String originalHash = passwordHash(userId);
        for (String invalid : new String[] {null, "", "abcd", "x".repeat(21)})
        {
            assertEquals(500, resetPassword(adminToken, userId, invalid).getIntValue("code"));
            assertEquals(originalHash, passwordHash(userId), "拒绝的密码不得写入数据库");
        }
        for (String valid : new String[] {"a\"<>|", "v".repeat(20), " a b "})
        {
            assertEquals(200, resetPassword(adminToken, userId, valid).getIntValue("code"));
            assertTrue(SecurityUtils.matchesPassword(valid, passwordHash(userId)));
            JSONObject loggedIn = loginAs("ERP-TEST-0", valid, null, true);
            assertEquals(200, loggedIn.getIntValue("code"));
            assertNotNull(loggedIn.getString("token"));
            assertEquals(403, resetPassword(loggedIn.getString("token"), userId, TEST_PASSWORD)
                    .getIntValue("code"), "无系统重置权限不能通过直接HTTP改密码");
        }
    }

    @Test
    void selfPasswordChangeUsesLoginLengthRulesAndPreservesPasswordOnFailure() throws Exception
    {
        long userId = fixtureUserId("ERP-TEST-1");
        String token = loginAs("ERP-TEST-1", TEST_PASSWORD, null, true).getString("token");
        String originalHash = passwordHash(userId);
        for (String invalid : new String[] {null, "", "abcd", "x".repeat(21)})
        {
            assertEquals(500, changeOwnPassword(token, TEST_PASSWORD, invalid).getIntValue("code"));
            assertEquals(originalHash, passwordHash(userId));
        }
        assertEquals(500, changeOwnPassword(token, "wrong-old", "valid").getIntValue("code"));
        assertEquals(500, changeOwnPassword(token, TEST_PASSWORD, TEST_PASSWORD).getIntValue("code"));
        assertEquals(originalHash, passwordHash(userId));
        String current = TEST_PASSWORD;
        for (String valid : new String[] {"a\"<>|", "v".repeat(20)})
        {
            assertEquals(200, changeOwnPassword(token, current, valid).getIntValue("code"));
            assertTrue(SecurityUtils.matchesPassword(valid, passwordHash(userId)));
            JSONObject loggedIn = loginAs("ERP-TEST-1", valid, null, true);
            assertEquals(200, loggedIn.getIntValue("code"));
            token = loggedIn.getString("token");
            current = valid;
        }
    }

    private long fixtureUserId(String username) throws Exception
    {
        try (var connection = DriverManager.getConnection(databaseUrl, "root", databasePassword);
                var statement = connection.prepareStatement("SELECT user_id FROM sys_user WHERE user_name=?"))
        {
            statement.setString(1, username);
            try (var result = statement.executeQuery())
            {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private String passwordHash(long userId) throws Exception
    {
        try (var connection = DriverManager.getConnection(databaseUrl, "root", databasePassword);
                var statement = connection.prepareStatement("SELECT password FROM sys_user WHERE user_id=?"))
        {
            statement.setLong(1, userId);
            try (var result = statement.executeQuery())
            {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private JSONObject resetPassword(String token, long userId, String password) throws Exception
    {
        JSONObject body = new JSONObject();
        body.put("userId", userId);
        body.put("password", password);
        return put(token, "/system/user/resetPwd", body);
    }

    private JSONObject changeOwnPassword(String token, String oldPassword, String newPassword) throws Exception
    {
        JSONObject body = new JSONObject();
        body.put("oldPassword", oldPassword);
        body.put("newPassword", newPassword);
        return put(token, "/system/user/profile/updatePwd", body);
    }

    private JSONObject put(String token, String path, JSONObject body) throws Exception
    {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(endpoint + path))
                .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body.toJSONString())).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        return JSON.parseObject(response.body());
    }

    private JSONObject login(String password, String code, boolean solveCaptcha) throws Exception
    {
        return loginAs(USERNAME, password, code, solveCaptcha);
    }

    private JSONObject loginAs(String username, String password, String code, boolean solveCaptcha) throws Exception
    {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(endpoint + "/captchaImage"))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
        JSONObject captcha = JSON.parseObject(response.body());
        assertEquals(200, captcha.getIntValue("code"));
        String uuid = captcha.getString("uuid");
        assertNotNull(uuid);
        if (solveCaptcha)
        {
            code = cache.getCacheObject(CacheConstants.CAPTCHA_CODE_KEY + uuid);
        }
        JSONObject request = new JSONObject();
        request.put("username", username);
        request.put("password", password);
        request.put("code", code);
        request.put("uuid", uuid);
        response = http.send(HttpRequest.newBuilder(URI.create(endpoint + "/login"))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(request.toJSONString())).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        return JSON.parseObject(response.body());
    }

    private void execute(String url, String sql) throws Exception
    {
        try (var connection = DriverManager.getConnection(url, "root", databasePassword);
                var statement = connection.createStatement())
        {
            statement.execute(sql);
        }
    }

    private JSONObject unlock(String token, long userId) throws Exception
    {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(
                endpoint + "/system/user/unlock/" + userId)).timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token).PUT(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        return JSON.parseObject(response.body());
    }

    private JSONObject get(String token, String path) throws Exception
    {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(endpoint + path))
                .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + token)
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        JSONObject body = JSON.parseObject(response.body());
        assertEquals(200, body.getIntValue("code"), "Read endpoint failed: " + path);
        return body;
    }

    private void verifyPagedQueries(String token) throws Exception
    {
        JSONObject first = get(token, "/system/user/list?pageNum=1&pageSize=1&orderByColumn=nickName&isAsc=ascending");
        JSONObject second = get(token, "/system/user/list?pageNum=2&pageSize=1&orderByColumn=nickName&isAsc=ascending");
        assertEquals(1, first.getJSONArray("rows").getJSONObject(0).getLongValue("userId"));
        assertEquals(2, second.getJSONArray("rows").getJSONObject(0).getLongValue("userId"));
        assertEquals(100, get(token, "/system/user/list?pageSize=1000").getJSONArray("rows").size());
        assertEquals(10, get(token, "/system/user/list?pageSize=0").getJSONArray("rows").size());
        for (String path : new String[] {"/system/role/list", "/system/role/authUser/allocatedList?roleId=1",
                "/system/role/authUser/unallocatedList?roleId=1", "/system/post/list", "/system/notice/list",
                "/system/notice/readUsers/list?noticeId=1", "/system/dict/type/list",
                "/system/dict/data/list?dictType=sys_normal_disable", "/system/config/list",
                "/monitor/operlog/list", "/monitor/logininfor/list", "/monitor/job/list", "/monitor/jobLog/list",
                "/tool/gen/list", "/tool/gen/db/list"})
        {
            assertNotNull(get(token, path).getJSONArray("rows"));
        }
    }

    private void verifyConcurrentFailureCounting() throws Exception
    {
        var user = application.getBean(com.ruoyi.system.service.ISysUserService.class).selectUserById(1L);
        var passwords = application.getBean(com.ruoyi.framework.web.service.SysPasswordService.class);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(8);
        var ready = new java.util.concurrent.CountDownLatch(8);
        var start = new java.util.concurrent.CountDownLatch(1);
        var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
        try
        {
            for (int i = 0; i < 8; i++)
            {
                tasks.add(executor.submit(() -> {
                    ready.countDown();
                    try
                    {
                        assertTrue(start.await(5, TimeUnit.SECONDS));
                        com.ruoyi.framework.security.context.AuthenticationContextHolder.setContext(
                                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                        "ADMIN", "ERP-test-wrong"));
                        assertThrows(com.ruoyi.common.exception.user.UserException.class,
                                () -> passwords.validate(user));
                    }
                    catch (InterruptedException exception)
                    {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(exception);
                    }
                    finally
                    {
                        com.ruoyi.framework.security.context.AuthenticationContextHolder.clearContext();
                    }
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (var task : tasks)
            {
                task.get(10, TimeUnit.SECONDS);
            }
            assertEquals(5, ((Number) cache.getCacheObject(FAILURE_KEY)).intValue());
            assertNull(cache.getCacheObject(CacheConstants.PWD_ERR_CNT_KEY + "ADMIN"));
        }
        finally
        {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private void verifyCaptchaCannotBeReusedConcurrently() throws Exception
    {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(endpoint + "/captchaImage"))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
        JSONObject captcha = JSON.parseObject(response.body());
        String uuid = captcha.getString("uuid");
        assertNotNull(uuid);
        JSONObject body = new JSONObject();
        body.put("username", USERNAME);
        body.put("password", "ERP-test-wrong");
        body.put("uuid", uuid);
        body.put("code", cache.getCacheObject(CacheConstants.CAPTCHA_CODE_KEY + uuid));
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint + "/login"))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString())).build();
        var executor = java.util.concurrent.Executors.newFixedThreadPool(8);
        var start = new java.util.concurrent.CountDownLatch(1);
        var tasks = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
        try
        {
            for (int i = 0; i < 8; i++)
            {
                tasks.add(executor.submit(() -> {
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return JSON.parseObject(http.send(request, HttpResponse.BodyHandlers.ofString()).body())
                            .getIntValue("code");
                }));
            }
            start.countDown();
            for (var task : tasks)
            {
                assertEquals(500, task.get(15, TimeUnit.SECONDS));
            }
            assertEquals(1, ((Number) cache.getCacheObject(FAILURE_KEY)).intValue());
            assertNull(cache.getCacheObject(CacheConstants.CAPTCHA_CODE_KEY + uuid));
        }
        finally
        {
            start.countDown();
            executor.shutdownNow();
        }
    }
}
