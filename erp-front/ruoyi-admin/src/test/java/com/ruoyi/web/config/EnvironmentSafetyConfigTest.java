package com.ruoyi.web.config;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentSafetyConfigTest
{
    private MockEnvironment completeEnvironment()
    {
        return new MockEnvironment()
                .withProperty("spring.datasource.druid.master.url", "jdbc:mysql://localhost/erp_test")
                .withProperty("spring.datasource.druid.master.username", "erp_test")
                .withProperty("spring.datasource.druid.master.password", "synthetic-test-password")
                .withProperty("token.secret", Base64.getEncoder().encodeToString(new byte[64]));
    }

    @Test
    void completeCredentialsAndDisabledMonitorAreAccepted()
    {
        assertDoesNotThrow(() -> EnvironmentSafetyConfig.requiredEnvironmentCredentials(
                completeEnvironment()).postProcessBeanFactory(null));
        String secret = completeEnvironment().getProperty("token.secret");
        String token = io.jsonwebtoken.Jwts.builder().setSubject("ERP-TEST")
                .signWith(io.jsonwebtoken.SignatureAlgorithm.HS512, secret).compact();
        assertEquals("ERP-TEST", io.jsonwebtoken.Jwts.parser().setSigningKey(secret)
                .parseClaimsJws(token).getBody().getSubject());
    }

    @Test
    void missingDatabaseCredentialsFailWithoutDisclosingOtherSecrets()
    {
        MockEnvironment environment = completeEnvironment();
        environment.setProperty("spring.datasource.druid.master.password", " ");
        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                EnvironmentSafetyConfig.requiredEnvironmentCredentials(environment).postProcessBeanFactory(null));
        assertTrue(failure.getMessage().contains("master.password"));
        assertFalse(failure.getMessage().contains(environment.getProperty("token.secret")));
    }

    @Test
    void weakOrMalformedTokenSecretIsRejectedWithoutDisclosingItsValue()
    {
        for (String secret : new String[] {"synthetic!invalid", Base64.getEncoder().encodeToString(new byte[32])})
        {
            MockEnvironment environment = completeEnvironment().withProperty("token.secret", secret);
            IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                    EnvironmentSafetyConfig.requiredEnvironmentCredentials(environment).postProcessBeanFactory(null));
            assertFalse(failure.getMessage().contains(secret));
        }
    }

    @Test
    void enabledMonitorRequiresItsOwnCredentials()
    {
        MockEnvironment environment = completeEnvironment().withProperty(
                "spring.datasource.druid.stat-view-servlet.enabled", "true");
        assertThrows(IllegalStateException.class, () -> EnvironmentSafetyConfig
                .requiredEnvironmentCredentials(environment).postProcessBeanFactory(null));
        environment.withProperty("spring.datasource.druid.stat-view-servlet.login-username", "erp_test")
                .withProperty("spring.datasource.druid.stat-view-servlet.login-password", "synthetic-monitor-password");
        assertDoesNotThrow(() -> EnvironmentSafetyConfig
                .requiredEnvironmentCredentials(environment).postProcessBeanFactory(null));
    }
}
