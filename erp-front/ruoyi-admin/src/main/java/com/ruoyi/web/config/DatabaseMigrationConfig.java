package com.ruoyi.web.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 迁移固定使用物理主库，不受业务线程的动态数据源选择影响。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "true")
public class DatabaseMigrationConfig
{
    @Bean
    public FlywayConfigurationCustomizer masterDatabaseMigration(
            @Qualifier("masterDataSource") DataSource masterDataSource)
    {
        return configuration -> configuration.dataSource(masterDataSource)
                .baselineOnMigrate(false).cleanDisabled(true).validateOnMigrate(true)
                .validateMigrationNaming(true).outOfOrder(false).ignoreMigrationPatterns(new String[0]);
    }
}
