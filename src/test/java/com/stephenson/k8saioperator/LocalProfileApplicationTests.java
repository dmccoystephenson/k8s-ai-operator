package com.stephenson.k8saioperator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Validates that the Spring application context loads successfully when the
 * {@code local} profile is active (no AWS credentials or SDK clients required).
 */
@SpringBootTest
@ActiveProfiles("local")
class LocalProfileApplicationTests {

    @Test
    void contextLoadsWithLocalProfile() {
        // If the context starts without errors the test passes.
        // This verifies that all local no-op beans wire together correctly
        // without any AWS SDK clients being instantiated.
    }
}
