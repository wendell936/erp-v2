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
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.RuoYiApplication;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.erp.catalog.service.SkuNumberService;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.ruoyi.common.exception.ServiceException;

import static org.junit.jupiter.api.Assertions.*;

/** P1-02.a/P1-03.a/b：实际权限、HTTP、MyBatis、事务与并发，仅 runner 的临时 loopback 实例。 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductTypeFlowIT
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
        schema = "erp_type_it_" + UUID.randomUUID().toString().replace("-", "");
        execute(baseUrl, "CREATE DATABASE " + schema + " CHARACTER SET utf8mb4");
        url = baseUrl.replace("/mysql?", "/" + schema + "?");
        // 先恢复上一交付版本，再由真实应用执行本批升级及角色授权。
        Flyway.configure().dataSource(url, "root", password).locations("classpath:db/migration")
                .target("2026101105").load().migrate();
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
                        new org.springframework.core.env.MapPropertySource("isolated-type-test", properties))).run();
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
            assertTrue(routes.contains("erp/product-type/index"));
        }
    }

    @AfterAll
    void stop() throws Exception
    {
        if (application != null) application.close();
        if (schema != null) execute(baseUrl, "DROP DATABASE IF EXISTS " + schema);
    }

    @Test
    void permissionsUniquenessValidationAndAuditAreEnforced() throws Exception
    {
        assertEquals(401, call(null, "GET", "/erp/product-type/list", null).getIntValue("code"));
        JSONObject body = fixture();
        body.put("createBy", "forged"); body.put("lastNumber", 800); body.put("status", "1");
        assertEquals(403, call(viewer, "POST", "/erp/product-type", body).getIntValue("code"));
        JSONObject saved = ok(operator, "POST", "/erp/product-type", body).getJSONObject("data");
        long id = saved.getLongValue("typeId");
        assertEquals("0", saved.getString("lastNumber"));
        assertEquals("0", saved.getString("status"));
        assertEquals("erp-op", saved.getString("createBy"));
        assertInstanceOf(String.class, saved.get("typeId"));
        assertFalse(saved.containsKey("requestHash")); assertFalse(saved.containsKey("requestId"));
        assertEquals(200, call(viewer,"GET","/erp/product-type/"+id,null).getIntValue("code"));
        assertEquals(200, call(admin,"GET","/erp/product-type/list",null).getIntValue("code"));
        JSONObject duplicate=JSON.parseObject(body.toJSONString()); duplicate.put("requestId", UUID.randomUUID().toString());
        assertEquals(500, call(operator,"POST","/erp/product-type",duplicate).getIntValue("code"));
        duplicate.put("typeCode",body.getString("typeCode").toLowerCase(Locale.ROOT));
        assertEquals(500, call(operator,"POST","/erp/product-type",duplicate).getIntValue("code"));
        body.put("version",0); body.put("reason","ERP-TEST correction");
        assertEquals(403, call(viewer,"PUT","/erp/product-type/"+id,body).getIntValue("code"));
        JSONObject status=JSON.parseObject("{\"status\":\"1\",\"version\":0,\"reason\":\"ERP-TEST stop\"}");
        assertEquals(403,call(viewer,"PUT","/erp/product-type/"+id+"/status",status).getIntValue("code"));
        ok(operator,"PUT","/erp/product-type/"+id+"/status",status);
        assertEquals(2,ok(viewer,"GET","/erp/product-type/"+id+"/changes",null).getIntValue("total"));
        assertEquals(500,call(operator,"PUT","/erp/product-type/"+id+"/status",status).getIntValue("code"));
        for (String code : List.of("中文", "X/Y", "X Y", "X&Y", "X-1", "-X", "X-", "X_1", "X".repeat(41)))
        { JSONObject invalid=fixture(); invalid.put("typeCode",code); assertEquals(500,call(operator,"POST","/erp/product-type",invalid).getIntValue("code")); }
        JSONObject invalid=fixture(); invalid.put("typeName"," ");
        assertEquals(500,call(operator,"POST","/erp/product-type",invalid).getIntValue("code"));
        assertEquals(500,call(viewer,"GET","/erp/product-type/list?status=9",null).getIntValue("code"));
        assertEquals(0,scalar("SELECT COUNT(*) FROM sys_oper_log WHERE oper_url LIKE '/erp/product-type%' AND (COALESCE(oper_param,'')<>'' OR COALESCE(json_result,'')<>'')"));
    }

    @Test
    void typeCreateRetriesAndCompetingUpdatesRemainConsistent() throws Exception
    {
        JSONObject body=fixture();
        List<JSONObject> results=concurrently(6,()->call(operator,"POST","/erp/product-type",body));
        Set<Long> ids=new HashSet<>();
        for(JSONObject r:results){ assertEquals(200,r.getIntValue("code"),r.toJSONString());ids.add(r.getJSONObject("data").getLongValue("typeId")); }
        assertEquals(1,ids.size());long id=ids.iterator().next();
        JSONObject edit=JSON.parseObject(body.toJSONString()); edit.put("typeName","ERP-TEST edited");edit.put("version",0);edit.put("reason","ERP-TEST edit");
        results=concurrently(2,()->call(operator,"PUT","/erp/product-type/"+id,edit));
        assertEquals(1,results.stream().filter(r->r.getIntValue("code")==200).count());
        assertEquals(1,results.stream().filter(r->r.getIntValue("code")==500).count());
        assertEquals(2,scalar("SELECT COUNT(*) FROM erp_product_type_change WHERE type_id="+id));
        assertEquals("ERP-TEST edited",ok(operator,"POST","/erp/product-type",body).getJSONObject("data").getString("typeName"));
        assertEquals(500,call(admin,"POST","/erp/product-type",body).getIntValue("code"));
        body.put("typeName","different");assertEquals(500,call(operator,"POST","/erp/product-type",body).getIntValue("code"));
    }

    @Test
    void auditFailureRollsBackCreateEditAndStatus() throws Exception
    {
        JSONObject body=fixture();long id=create(body);
        execute(url,"CREATE TRIGGER type_audit_failure BEFORE INSERT ON erp_product_type_change FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ERP-TEST audit failure'");
        JSONObject newBody=fixture();
        try
        {
            assertEquals(500,call(operator,"POST","/erp/product-type",newBody).getIntValue("code"));
            assertEquals(0,scalar("SELECT COUNT(*) FROM erp_product_type WHERE request_id='"+newBody.getString("requestId")+"'"));
            body.put("version",0);body.put("reason","ERP-TEST rollback");body.put("typeName","ERP-TEST rollback value");
            assertEquals(500,call(operator,"PUT","/erp/product-type/"+id,body).getIntValue("code"));
            JSONObject status=JSON.parseObject("{\"status\":\"1\",\"version\":0,\"reason\":\"ERP-TEST rollback\"}");
            assertEquals(500,call(operator,"PUT","/erp/product-type/"+id+"/status",status).getIntValue("code"));
            assertEquals(0,scalar("SELECT version FROM erp_product_type WHERE type_id="+id));
            assertEquals(0,scalar("SELECT status FROM erp_product_type WHERE type_id="+id));
            assertEquals(1,scalar("SELECT COUNT(*) FROM erp_product_type_change WHERE type_id="+id));
        }
        finally {execute(url,"DROP TRIGGER type_audit_failure");}
        create(newBody);
    }

    @Test
    void mainAndVariantNumbersAreSerializedIdempotentAndDoNotReuse() throws Exception
    {
        JSONObject body=fixture();long id=create(body);String code=body.getString("typeCode");
        var service=application.getBean(SkuNumberService.class);
        String key=UUID.randomUUID().toString();
        List<JSONObject> results=concurrently(8,()->asOperator(()->JSON.parseObject(JSON.toJSONString(service.allocateMain(id,key)))));
        Set<Long> ids=new HashSet<>();for(JSONObject r:results){ids.add(r.getLongValue("numberId"));assertEquals(code+"-1",r.getString("skuCode"));}
        assertEquals(1,ids.size());long main=ids.iterator().next();
        results=concurrently(8,()->asOperator(()->JSON.parseObject(JSON.toJSONString(service.allocateMain(id,UUID.randomUUID().toString())))));
        Set<String> codes=new HashSet<>();for(JSONObject r:results)codes.add(r.getString("skuCode"));assertEquals(8,codes.size());
        assertEquals(9,scalar("SELECT last_number FROM erp_product_type WHERE type_id="+id));
        String variantKey=UUID.randomUUID().toString();
        results=concurrently(6,()->asOperator(()->JSON.parseObject(JSON.toJSONString(service.allocateVariant(main,variantKey)))));
        ids.clear();for(JSONObject r:results){ids.add(r.getLongValue("numberId"));assertEquals(code+"-1-1",r.getString("skuCode"));}
        assertEquals(1,ids.size());
        assertThrows(ServiceException.class,()->asOperator(()->service.allocateVariant(ids.iterator().next(),UUID.randomUUID().toString())));
        assertEquals(code+"-1-2",asOperator(()->service.allocateVariant(main,UUID.randomUUID().toString())).getSkuCode());
        // 模拟未来商品解除对编号的使用：编号记录仍保留，不通过删除重置流水。
        assertEquals(code+"-10",asOperator(()->service.allocateMain(id,UUID.randomUUID().toString())).getSkuCode());
        JSONObject edit=JSON.parseObject(body.toJSONString());edit.put("version",0);edit.put("reason","ERP-TEST code change");edit.put("typeCode","CHANGED");
        assertEquals(500,call(operator,"PUT","/erp/product-type/"+id,edit).getIntValue("code"));
        JSONObject status=JSON.parseObject("{\"status\":\"1\",\"version\":0,\"reason\":\"ERP-TEST stop\"}");
        ok(operator,"PUT","/erp/product-type/"+id+"/status",status);
        assertEquals(code+"-1",asOperator(()->service.allocateMain(id,key)).getSkuCode());
        assertThrows(ServiceException.class,()->asOperator(()->service.allocateMain(id,UUID.randomUUID().toString())));
        assertThrows(ServiceException.class,()->asOperator(()->service.allocateVariant(main,UUID.randomUUID().toString())));
        assertThrows(ServiceException.class,()->asOperator(()->service.allocateMain(id,variantKey)));
    }

    @Test
    void allocationFailureAndOuterRollbackRestoreCounters() throws Exception
    {
        long id=create(fixture());var service=application.getBean(SkuNumberService.class);String key=UUID.randomUUID().toString();
        execute(url,"CREATE TRIGGER number_failure BEFORE INSERT ON erp_sku_number FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='ERP-TEST insert failure'");
        try {assertThrows(ServiceException.class,()->asOperator(()->service.allocateMain(id,key)));}
        finally {execute(url,"DROP TRIGGER number_failure");}
        assertEquals(0,scalar("SELECT last_number FROM erp_product_type WHERE type_id="+id));
        var main=asOperator(()->service.allocateMain(id,key));assertEquals(1,main.getSequenceNumber());
        var transaction=new TransactionTemplate(application.getBean(PlatformTransactionManager.class));
        transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        assertThrows(IllegalStateException.class,()->asOperator(()->transaction.execute(s->{service.allocateVariant(main.getNumberId(),UUID.randomUUID().toString());throw new IllegalStateException("ERP-TEST downstream failure");})));
        assertEquals(0,scalar("SELECT last_variant_number FROM erp_sku_number WHERE number_id="+main.getNumberId()));
        assertEquals(1,asOperator(()->service.allocateVariant(main.getNumberId(),UUID.randomUUID().toString())).getSequenceNumber());
        assertThrows(ServiceException.class,()->asOperator(()->service.allocateMain(id,"invalid")));
        execute(url,"UPDATE erp_product_type SET last_number=9223372036854775807 WHERE type_id="+id);
        assertThrows(ServiceException.class,()->asOperator(()->service.allocateMain(id,UUID.randomUUID().toString())));
    }

    @Test
    void paginationKeywordSearchAndDatabaseConstraintsAreEnforced() throws Exception
    {
        JSONObject chinese = fixture();
        chinese.put("typeName", "ERP-TEST-中文类型");
        chinese.put("keywords", "包装材料");
        long chineseId = create(chinese);
        for (String term : List.of("中文类型", "包装材料", chinese.getString("typeCode")))
        {
            String encoded = java.net.URLEncoder.encode(term, java.nio.charset.StandardCharsets.UTF_8);
            JSONObject found = ok(viewer, "GET", "/erp/product-type/list?typeName=" + encoded, null);
            assertEquals(1, found.getIntValue("total"));
            assertEquals(chineseId, found.getJSONArray("rows").getJSONObject(0).getLongValue("typeId"));
        }
        for(int i=0;i<105;i++)execute(url,"INSERT INTO erp_product_type(type_code,type_name,keywords,request_id,request_hash,create_by,create_time) VALUES('PAGE"+i+"','ERP-TEST-page','needle','"+UUID.randomUUID()+"',REPEAT('0',64),'fixture',UTC_TIMESTAMP(3))");
        String path="/erp/product-type/list?typeName=needle";
        assertEquals(10,ok(viewer,"GET",path,null).getJSONArray("rows").size());
        assertEquals(100,ok(viewer,"GET",path+"&pageSize=1000",null).getJSONArray("rows").size());
        assertEquals(10,ok(viewer,"GET",path+"&pageSize=0",null).getJSONArray("rows").size());
        String sort="&pageSize=1&orderByColumn=typeName&isAsc=ascending";
        long a=ok(viewer,"GET",path+sort+"&pageNum=1",null).getJSONArray("rows").getJSONObject(0).getLongValue("typeId");
        long b=ok(viewer,"GET",path+sort+"&pageNum=2",null).getJSONArray("rows").getJSONObject(0).getLongValue("typeId");assertTrue(a<b);
        assertThrows(java.sql.SQLException.class,()->execute(url,"UPDATE erp_product_type SET status='9' WHERE type_id="+a));
        assertThrows(java.sql.SQLException.class,()->execute(url,"UPDATE erp_product_type SET last_number=-1 WHERE type_id="+a));
        assertEquals(0,Flyway.configure().dataSource(url,"root",password).locations("classpath:db/migration").load().migrate().migrationsExecuted);
    }

    @Test
    void distinctVariantRequestsAndOverlappingTypeCodesPreserveGlobalUniqueness() throws Exception
    {
        JSONObject body = fixture();
        long typeId = create(body);
        var service = application.getBean(SkuNumberService.class);
        var main = asOperator(() -> service.allocateMain(typeId, UUID.randomUUID().toString()));
        List<JSONObject> results = concurrently(8, () -> asOperator(() -> JSON.parseObject(JSON.toJSONString(
                service.allocateVariant(main.getNumberId(), UUID.randomUUID().toString())))));
        Set<String> codes = new HashSet<>();
        for (JSONObject result : results)
        {
            codes.add(result.getString("skuCode"));
        }
        assertEquals(8, codes.size());
        assertEquals(8, scalar("SELECT last_variant_number FROM erp_sku_number WHERE number_id=" + main.getNumberId()));

        // 已产生变体后也应在类型入口拒绝旧的重叠格式，不能等到分配时碰撞。
        JSONObject overlapping = fixture();
        overlapping.put("typeCode", body.getString("typeCode") + "-1");
        assertEquals(500, call(operator,"POST","/erp/product-type",overlapping).getIntValue("code"));
        assertEquals(0,scalar("SELECT COUNT(*) FROM erp_product_type WHERE request_id='"+overlapping.getString("requestId")+"'"));
        long unused = create(fixture());
        overlapping.put("version", 0);
        overlapping.put("reason", "ERP-TEST invalid rename");
        assertEquals(500, call(operator,"PUT","/erp/product-type/"+unused,overlapping).getIntValue("code"));
        assertEquals(0,scalar("SELECT version FROM erp_product_type WHERE type_id="+unused));
        assertEquals(1,scalar("SELECT COUNT(*) FROM erp_product_type_change WHERE type_id="+unused));
        assertThrows(java.sql.SQLException.class, () -> execute(url,
                "UPDATE erp_product_type SET type_code='"+overlapping.getString("typeCode")+"' WHERE type_id="+unused));
        assertEquals(body.getString("typeCode")+"-1-9",asOperator(() ->
                service.allocateVariant(main.getNumberId(),UUID.randomUUID().toString())).getSkuCode());
    }

    @Test
    void alphanumericNamespacesRemainDistinctInBothCreationOrdersAndConcurrentCaseDuplicates() throws Exception
    {
        var service=application.getBean(SkuNumberService.class);
        for (boolean reversed : List.of(false,true))
        {
            String prefix="NS"+UUID.randomUUID().toString().substring(0,8);
            JSONObject base=fixture();base.put("typeCode",prefix);
            JSONObject suffix=fixture();suffix.put("typeCode",prefix+"1");
            JSONObject invalid=fixture();invalid.put("typeCode",prefix+"-1");
            if(reversed) assertEquals(500,call(operator,"POST","/erp/product-type",invalid).getIntValue("code"));
            long first=create(reversed?suffix:base);
            long second=create(reversed?base:suffix);
            long baseId=reversed?second:first;
            long suffixId=reversed?first:second;
            var baseMain=asOperator(()->service.allocateMain(baseId,UUID.randomUUID().toString()));
            var suffixMain=asOperator(()->service.allocateMain(suffixId,UUID.randomUUID().toString()));
            // 两合法类型均已使用，旧重叠输入仍应拒绝；原主号与变体不受影响。
            for(int attempt=0;attempt<3;attempt++)
                assertEquals(500,call(operator,"POST","/erp/product-type",invalid).getIntValue("code"));
            var baseVariant=asOperator(()->service.allocateVariant(baseMain.getNumberId(),UUID.randomUUID().toString()));
            var suffixVariant=asOperator(()->service.allocateVariant(suffixMain.getNumberId(),UUID.randomUUID().toString()));
            assertEquals(Set.of(prefix+"-1",prefix+"1-1",prefix+"-1-1",prefix+"1-1-1"),
                    Set.of(baseMain.getSkuCode(),suffixMain.getSkuCode(),baseVariant.getSkuCode(),suffixVariant.getSkuCode()));
            assertEquals(0,scalar("SELECT COUNT(*) FROM erp_product_type WHERE request_id='"+invalid.getString("requestId")+"'"));
        }
        JSONObject boundary=fixture();boundary.put("typeCode","B"+UUID.randomUUID().toString().replace("-","")+"1234567");
        assertEquals(40,boundary.getString("typeCode").length());
        create(boundary);
        JSONObject competitor=JSON.parseObject(boundary.toJSONString());
        competitor.put("typeCode",boundary.getString("typeCode").toLowerCase(Locale.ROOT));
        competitor.put("requestId",UUID.randomUUID().toString());
        assertEquals(500,call(operator,"POST","/erp/product-type",competitor).getIntValue("code"));
        JSONObject racing=fixture();
        List<JSONObject> results=concurrently(6,()->{
            JSONObject attempt=JSON.parseObject(racing.toJSONString());
            attempt.put("requestId",UUID.randomUUID().toString());
            attempt.put("typeCode",ThreadLocalRandom.current().nextBoolean()?racing.getString("typeCode").toLowerCase(Locale.ROOT):racing.getString("typeCode"));
            return call(operator,"POST","/erp/product-type",attempt);
        });
        assertEquals(1,results.stream().filter(r->r.getIntValue("code")==200).count());
        assertEquals(5,results.stream().filter(r->r.getIntValue("code")==500).count());
    }

    @Test
    void legacyHyphenTypeCannotAllocateNewNumbersButExistingRetryRemainsStable() throws Exception
    {
        // 仅本次一次性夹具模拟未升级的旧库；正式迁移不禁用约束或删除编号。
        String code="LEGACY-"+UUID.randomUUID().toString().substring(0,8);
        String request=UUID.randomUUID().toString();
        int legacyId=0;
        execute(url,"ALTER TABLE erp_product_type ALTER CHECK ck_product_type_code NOT ENFORCED");
        try
        {
            execute(url,"INSERT INTO erp_product_type(type_code,type_name,last_number,request_id,request_hash,create_by,create_time) VALUES('"
                    +code+"','ERP-TEST legacy',1,'"+UUID.randomUUID()+"',REPEAT('0',64),'erp-op',UTC_TIMESTAMP(3))");
            legacyId=scalar("SELECT type_id FROM erp_product_type WHERE type_code='"+code+"'");
            final long typeId=legacyId;
            execute(url,"INSERT INTO erp_sku_number(type_id,sequence_number,sku_code,request_id,create_by,create_time) VALUES("
                    +typeId+",1,'"+code+"-1','"+request+"','erp-op',UTC_TIMESTAMP(3))");
            var service=application.getBean(SkuNumberService.class);
            var existing=asOperator(()->service.allocateMain(typeId,request));
            assertEquals(code+"-1",existing.getSkuCode());
            assertThrows(ServiceException.class,()->asOperator(()->service.allocateMain(typeId,UUID.randomUUID().toString())));
            assertThrows(ServiceException.class,()->asOperator(()->service.allocateVariant(existing.getNumberId(),UUID.randomUUID().toString())));
            assertEquals(1,scalar("SELECT last_number FROM erp_product_type WHERE type_id="+typeId));
            assertEquals(0,scalar("SELECT last_variant_number FROM erp_sku_number WHERE type_id="+typeId));
            assertEquals(1,scalar("SELECT COUNT(*) FROM erp_sku_number WHERE type_id="+typeId));
        }
        finally
        {
            if(legacyId>0){execute(url,"DELETE FROM erp_sku_number WHERE type_id="+legacyId);execute(url,"DELETE FROM erp_product_type WHERE type_id="+legacyId);}
            execute(url,"ALTER TABLE erp_product_type ALTER CHECK ck_product_type_code ENFORCED");
        }
    }

    @Test
    void incompatibleOuterTransactionCannotPartiallyAllocate() throws Exception
    {
        long typeId = create(fixture());
        var service = application.getBean(SkuNumberService.class);
        var transaction = new TransactionTemplate(application.getBean(PlatformTransactionManager.class));
        transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        assertThrows(ServiceException.class, () -> asOperator(() -> transaction.execute(status ->
                service.allocateMain(typeId, UUID.randomUUID().toString()))));
        assertEquals(0, scalar("SELECT last_number FROM erp_product_type WHERE type_id=" + typeId));
        assertEquals(0, scalar("SELECT COUNT(*) FROM erp_sku_number WHERE type_id=" + typeId));
    }

    private long create(JSONObject body) throws Exception {return ok(operator,"POST","/erp/product-type",body).getJSONObject("data").getLongValue("typeId");}
    private JSONObject fixture()
    {
        JSONObject body=new JSONObject();body.put("typeCode","TEST"+UUID.randomUUID().toString().substring(0,8));
        body.put("typeName","ERP-TEST type");body.put("keywords","synthetic");body.put("requestId",UUID.randomUUID().toString());return body;
    }
    private <T> T asOperator(Callable<T> action) throws Exception
    {
        var context=SecurityContextHolder.createEmptyContext();var user=new SysUser();user.setUserName("erp-op");
        context.setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser(3L,0L,user,Set.of()),null,List.of()));
        SecurityContextHolder.setContext(context);try{return action.call();}finally{SecurityContextHolder.clearContext();}
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
