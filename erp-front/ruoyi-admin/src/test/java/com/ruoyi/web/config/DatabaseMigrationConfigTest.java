package com.ruoyi.web.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseMigrationConfigTest
{
    @Test
    void disabledMigrationDoesNotRequireOrOpenDatabase()
    {
        new ApplicationContextRunner().withUserConfiguration(DatabaseMigrationConfig.class)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(0, context.getBeansOfType(FlywayConfigurationCustomizer.class).size());
                });
    }

    @Test
    void enabledMigrationUsesPhysicalMasterAndRejectsUnsafeDefaults()
    {
        DriverManagerDataSource master = new DriverManagerDataSource();
        new ApplicationContextRunner().withUserConfiguration(DatabaseMigrationConfig.class)
                .withPropertyValues("spring.flyway.enabled=true")
                .withBean("masterDataSource", javax.sql.DataSource.class, () -> master)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    FluentConfiguration configuration = new FluentConfiguration();
                    context.getBean(FlywayConfigurationCustomizer.class).customize(configuration);
                    assertSame(master, configuration.getDataSource());
                    assertFalse(configuration.isBaselineOnMigrate());
                    assertTrue(configuration.isCleanDisabled());
                    assertTrue(configuration.isValidateOnMigrate());
                    assertTrue(configuration.isValidateMigrationNaming());
                    assertFalse(configuration.isOutOfOrder());
                    assertEquals(0, configuration.getIgnoreMigrationPatterns().length);
                });
    }
}
