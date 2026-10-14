package com.ruoyi.web.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.junit.jupiter.api.Assertions.*;

/** 显式 migration-it profile；仅连接 runner 创建的 loopback 临时 MySQL。 */
class DatabaseMigrationIT
{
    @TempDir Path temporary;
    private String baseUrl;
    private String password;
    private String schema;

    @BeforeEach
    void createIsolatedSchema() throws Exception
    {
        baseUrl = required("ERP_MIGRATION_TEST_URL");
        password = required("ERP_MIGRATION_TEST_PASSWORD");
        // 不允许把开发测试命令误指向真实远程数据库。
        assertTrue(baseUrl.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/mysql\\?.*"));
        schema = "erp_migration_it_" + UUID.randomUUID().toString().replace("-", "");
        execute(baseUrl, "CREATE DATABASE " + schema + " CHARACTER SET utf8mb4");
    }

    @AfterEach
    void removeIsolatedSchema() throws Exception
    {
        if (schema != null)
        {
            execute(baseUrl, "DROP DATABASE IF EXISTS " + schema);
        }
    }

    @Test
    void failedTransactionDoesNotCommitPartialWrite() throws Exception
    {
        execute(url(), "CREATE TABLE transaction_probe(id INT PRIMARY KEY, amount DECIMAL(18,2)) ENGINE=InnoDB");
        try (Connection connection = DriverManager.getConnection(url(), "root", password);
                Statement statement = connection.createStatement())
        {
            connection.setAutoCommit(false);
            statement.executeUpdate("INSERT INTO transaction_probe VALUES(1,12.34)");
            assertThrows(java.sql.SQLException.class, () -> statement.executeUpdate(
                    "INSERT INTO transaction_probe VALUES(1,56.78)"));
            connection.rollback();
            assertEquals(0, scalar("SELECT COUNT(*) FROM transaction_probe"));
            statement.executeUpdate("INSERT INTO transaction_probe VALUES(2,12.34)");
            connection.commit();
        }
        assertEquals(1, scalar("SELECT COUNT(*) FROM transaction_probe"));
    }

    @Test
    void bootMigratesPhysicalMasterBeforeMyBatisUsesDatabase()
    {
        com.alibaba.druid.pool.DruidDataSource master = new com.alibaba.druid.pool.DruidDataSource();
        master.setUrl(url());
        master.setUsername("root");
        master.setPassword(password);
        java.util.Map<Object, Object> targets = new java.util.HashMap<>();
        targets.put("MASTER", master);
        com.ruoyi.framework.datasource.DynamicDataSource routing =
                new com.ruoyi.framework.datasource.DynamicDataSource(master, targets);
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                        org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration.class))
                .withUserConfiguration(DatabaseMigrationConfig.class,
                        com.ruoyi.framework.config.MyBatisConfig.class)
                .withBean("masterDataSource", javax.sql.DataSource.class, () -> master)
                .withBean("dynamicDataSource", javax.sql.DataSource.class, () -> routing,
                        definition -> definition.setPrimary(true))
                .withPropertyValues("spring.flyway.enabled=true",
                        "spring.flyway.locations=classpath:db/migration",
                        "mybatis.typeAliasesPackage=com.ruoyi.**.domain",
                        "mybatis.mapperLocations=classpath*:mapper/system/*.xml",
                        "mybatis.configLocation=classpath:mybatis/mybatis-config.xml")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertSame(master, context.getBean(Flyway.class).getConfiguration().getDataSource());
                    try (var session = context.getBean(org.apache.ibatis.session.SqlSessionFactory.class)
                            .openSession())
                    {
                        assertNotNull(session.getMapper(com.ruoyi.system.mapper.SysUserMapper.class)
                                .selectUserById(1L));
                    }
                });
    }

    @Test
    void emptyDatabaseInitializesAndRepeatedMigrationPreservesData() throws Exception
    {
        Flyway flyway = flyway().load();
        assertEquals(10, flyway.migrate().migrationsExecuted);
        assertEquals(10, scalar("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1"));
        assertEquals(2, scalar("SELECT COUNT(*) FROM sys_user"));
        assertEquals(11, scalar("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='"
                + schema + "' AND table_name LIKE 'QRTZ_%'"));
        execute(url(), "UPDATE sys_user SET nick_name='ERP-TEST-preserved' WHERE user_id=1");
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals(1, scalar("SELECT COUNT(*) FROM sys_user WHERE nick_name='ERP-TEST-preserved'"));
        assertThrows(FlywayException.class, flyway::clean);
    }

    @Test
    void unknownExistingDatabaseIsRejectedWithoutChangingItsData() throws Exception
    {
        execute(url(), "CREATE TABLE existing_record(id INT PRIMARY KEY)");
        execute(url(), "INSERT INTO existing_record VALUES(42)");
        assertThrows(FlywayException.class, () -> flyway().load().migrate());
        assertEquals(42, scalar("SELECT id FROM existing_record"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='"
                + schema + "' AND table_name='sys_user'"));
    }

    @Test
    void alphanumericTypeUpgradePreservesAllocatedCodesAndEnforcesNewDatabaseRule() throws Exception
    {
        assertEquals(9,flyway().target("2026101202").load().migrate().migrationsExecuted);
        insertTypeWithMainNumber("UPgrade1");
        execute(url(),"UPDATE erp_sku_number SET last_variant_number=1 WHERE sku_code='UPgrade1-1'");
        execute(url(),"INSERT INTO erp_sku_number(type_id,main_number_id,sequence_number,sku_code,request_id,create_by,create_time) "
                +"SELECT type_id,number_id,1,'UPgrade1-1-1','"+UUID.randomUUID()+"','fixture',UTC_TIMESTAMP(3) FROM erp_sku_number WHERE sku_code='UPgrade1-1'");
        assertEquals(1,flyway().load().migrate().migrationsExecuted);
        assertEquals(1,scalar("SELECT COUNT(*) FROM erp_product_type WHERE BINARY type_code=BINARY 'UPgrade1' AND last_number=1 AND version=0"));
        assertEquals(1,scalar("SELECT COUNT(*) FROM erp_sku_number WHERE BINARY sku_code=BINARY 'UPgrade1-1' AND last_variant_number=1"));
        assertEquals(1,scalar("SELECT COUNT(*) FROM erp_sku_number WHERE BINARY sku_code=BINARY 'UPgrade1-1-1'"));
        assertThrows(java.sql.SQLException.class,()->execute(url(),"UPDATE erp_product_type SET type_code='UPgrade1-1' WHERE type_code='UPgrade1'"));
        assertThrows(java.sql.SQLException.class,()->insertTypeWithMainNumber("INVALID-1"));
        assertEquals(1,scalar("SELECT COUNT(*) FROM erp_product_type"));
        assertEquals(2,scalar("SELECT COUNT(*) FROM erp_sku_number"));
        assertEquals(0,flyway().load().migrate().migrationsExecuted);
    }

    @Test
    void legacyOverlappingTypesBlockUpgradeWithoutRenumberingOrDroppingOldConstraint() throws Exception
    {
        flyway().target("2026101202").load().migrate();
        insertTypeWithMainNumber("OLD");
        insertTypeWithMainNumber("OLD-1");
        assertThrows(FlywayException.class,()->flyway().load().migrate());
        assertEquals(2,scalar("SELECT COUNT(*) FROM erp_product_type WHERE last_number=1 AND version=0"));
        assertEquals(2,scalar("SELECT COUNT(*) FROM erp_sku_number WHERE sku_code IN('OLD-1','OLD-1-1')"));
        assertEquals(9,scalar("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1"));
        assertEquals(1,scalar("SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='"+schema
                +"' AND table_name='erp_product_type' AND constraint_name='ck_product_type_code' AND enforced='YES'"));
        // 原约束仍允许连字符，但拒绝其他非法字符，单条失败ALTER未留下约束空窗。
        execute(url(),"UPDATE erp_product_type SET type_code='OLD-2' WHERE type_code='OLD-1'");
        assertThrows(java.sql.SQLException.class,()->execute(url(),"UPDATE erp_product_type SET type_code='OLD_2' WHERE type_code='OLD-2'"));
        assertEquals(2,scalar("SELECT COUNT(*) FROM erp_sku_number WHERE sku_code IN('OLD-1','OLD-1-1')"));
    }

    private void insertTypeWithMainNumber(String code) throws Exception
    {
        execute(url(),"INSERT INTO erp_product_type(type_code,type_name,last_number,request_id,request_hash,create_by,create_time) VALUES('"
                +code+"','ERP-TEST migration',1,'"+UUID.randomUUID()+"',REPEAT('0',64),'fixture',UTC_TIMESTAMP(3))");
        execute(url(),"INSERT INTO erp_sku_number(type_id,sequence_number,sku_code,request_id,create_by,create_time) "
                +"SELECT type_id,1,CONCAT(type_code,'-1'),'"+UUID.randomUUID()+"','fixture',UTC_TIMESTAMP(3) FROM erp_product_type WHERE type_code='"+code+"'");
    }

    @Test
    void reviewedExistingBaselineCanBeAdoptedAndUpgraded() throws Exception
    {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url(), "root", password);
        new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V2026041701__ruoyi_baseline.sql"),
                new ClassPathResource("db/migration/V2026041702__quartz_baseline.sql")).execute(dataSource);
        execute(url(), "UPDATE sys_user SET nick_name='ERP-TEST-existing' WHERE user_id=1");
        // 仅此测试已知精确基线；部署既有库必须另行备份、比对结构并人工授权。
        Flyway adopted = flyway().baselineVersion("2026041702").load();
        adopted.baseline();
        assertEquals(8, adopted.migrate().migrationsExecuted);
        Path increment = migrationDirectory("CREATE TABLE migration_probe(id INT PRIMARY KEY);", null);
        assertEquals(1, flyway(increment).load().migrate().migrationsExecuted);
        assertEquals(1, scalar("SELECT COUNT(*) FROM sys_user WHERE nick_name='ERP-TEST-existing'"));
    }

    @Test
    void checksumChangesAndFutureDatabaseVersionBlockMigration() throws Exception
    {
        Path migrations = temporary.resolve("all");
        Files.createDirectory(migrations);
        for (String name : new String[] {"V2026041701__ruoyi_baseline.sql", "V2026041702__quartz_baseline.sql"})
        {
            try (var input = new ClassPathResource("db/migration/" + name).getInputStream())
            {
                Files.copy(input, migrations.resolve(name));
            }
        }
        Flyway original = Flyway.configure().dataSource(url(), "root", password)
                .locations("filesystem:" + migrations).ignoreMigrationPatterns(new String[0]).load();
        original.migrate();
        Path first = migrations.resolve("V2026041701__ruoyi_baseline.sql");
        String contents = Files.readString(first);
        Files.writeString(first, contents.replace("若依科技", "ERP-TEST-checksum"));
        assertThrows(FlywayException.class, original::migrate);
        Files.writeString(first, contents);
        Path increment = migrationDirectory("CREATE TABLE future_probe(id INT PRIMARY KEY);", null);
        flyway(increment).load().migrate();
        assertThrows(FlywayException.class, () -> flyway().load().migrate());
    }

    @Test
    void failedDdlBlocksSuccessorsAndBackupRestorePreservesData() throws Exception
    {
        flyway().load().migrate();
        execute(url(), "UPDATE sys_user SET nick_name='ERP-TEST-backup' WHERE user_id=1");
        Path backup = temporary.resolve("before.sql");
        dockerDatabaseCommand(backup, false);
        Path increment = migrationDirectory("CREATE TABLE partial_probe(id INT PRIMARY KEY);\n"
                + "INSERT INTO table_that_does_not_exist VALUES(1);",
                "CREATE TABLE successor_probe(id INT PRIMARY KEY);");
        Flyway failing = flyway(increment).load();
        assertThrows(FlywayException.class, failing::migrate);
        assertEquals(1, scalar("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='"
                + schema + "' AND table_name='partial_probe'"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM flyway_schema_history WHERE success=0"));
        assertThrows(FlywayException.class, failing::migrate);
        assertEquals(0, scalar("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='"
                + schema + "' AND table_name='successor_probe'"));
        // 实际恢复备份，不自动 repair，也不把部分 DDL 当成事务已回滚。
        execute(baseUrl, "DROP DATABASE " + schema);
        execute(baseUrl, "CREATE DATABASE " + schema + " CHARACTER SET utf8mb4");
        dockerDatabaseCommand(backup, true);
        assertEquals(0, flyway().load().migrate().migrationsExecuted);
        assertEquals(1, scalar("SELECT COUNT(*) FROM sys_user WHERE nick_name='ERP-TEST-backup'"));
        assertEquals(0, scalar("SELECT COUNT(*) FROM flyway_schema_history WHERE success=0"));
        Files.writeString(increment.resolve("V9999010101__test_change.sql"),
                "CREATE TABLE partial_probe(id INT PRIMARY KEY);");
        assertEquals(2, flyway(increment).load().migrate().migrationsExecuted);
    }

    private FluentConfiguration flyway(Path... increment)
    {
        String[] locations = increment.length == 0 ? new String[] {"classpath:db/migration"}
                : new String[] {"classpath:db/migration", "filesystem:" + increment[0]};
        FluentConfiguration configuration = Flyway.configure().locations(locations);
        new DatabaseMigrationConfig().masterDatabaseMigration(
                new DriverManagerDataSource(url(), "root", password)).customize(configuration);
        return configuration;
    }

    private Path migrationDirectory(String first, String second) throws Exception
    {
        Path directory = temporary.resolve("increment");
        Files.createDirectory(directory);
        Files.writeString(directory.resolve("V9999010101__test_change.sql"), first);
        if (second != null)
        {
            Files.writeString(directory.resolve("V9999010102__test_successor.sql"), second);
        }
        return directory;
    }

    private String url()
    {
        return baseUrl.replace("/mysql?", "/" + schema + "?");
    }

    private void execute(String jdbcUrl, String sql) throws Exception
    {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "root", password);
                Statement statement = connection.createStatement())
        {
            statement.execute(sql);
        }
    }

    private int scalar(String sql) throws Exception
    {
        try (Connection connection = DriverManager.getConnection(url(), "root", password);
                Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql))
        {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private void dockerDatabaseCommand(Path backup, boolean restore) throws Exception
    {
        String container = required("ERP_MIGRATION_TEST_CONTAINER");
        assertTrue(container.matches("erp-v2-migration-it-[a-zA-Z0-9-]+"));
        String dockerHost = System.getenv("ERP_TEST_DOCKER_HOST");
        if (dockerHost == null || dockerHost.isBlank())
        {
            dockerHost = "unix:///var/run/docker.sock";
        }
        // 测试备份只能调用本机 Docker，不接受远程 daemon。
        assertTrue(dockerHost.equals("unix:///var/run/docker.sock")
                || dockerHost.equals("npipe:////./pipe/dockerDesktopLinuxEngine"));
        ProcessBuilder builder = restore
                ? new ProcessBuilder("docker", "--host=" + dockerHost, "exec", "-i",
                        "-e", "MYSQL_PWD", container, "mysql", "-uroot", schema)
                : new ProcessBuilder("docker", "--host=" + dockerHost, "exec",
                        "-e", "MYSQL_PWD", container, "mysqldump", "-uroot", "--single-transaction",
                        "--skip-lock-tables", "--set-gtid-purged=OFF", "--no-tablespaces", schema);
        builder.environment().put("MYSQL_PWD", password);
        builder.redirectError(temporary.resolve(restore ? "restore.err" : "backup.err").toFile());
        if (restore)
        {
            builder.redirectInput(backup.toFile());
            builder.redirectOutput(temporary.resolve("restore.out").toFile());
        }
        else
        {
            builder.redirectOutput(backup.toFile());
        }
        Process process = builder.start();
        if (!process.waitFor(30, TimeUnit.SECONDS))
        {
            process.destroyForcibly();
            fail("Database backup/restore timed out");
        }
        assertEquals(0, process.exitValue(), "Database backup/restore must succeed");
    }

    private static String required(String variable)
    {
        String value = System.getenv(variable);
        assertNotNull(value, "Missing test configuration: " + variable);
        assertFalse(value.isBlank(), "Missing test configuration: " + variable);
        return value;
    }
}
