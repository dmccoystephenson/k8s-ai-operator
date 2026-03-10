package com.stephenson.k8saioperator.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Placeholder configuration that activates when the {@code local} Spring profile is
 * active.  No AWS SDK client beans are registered here — the application uses
 * no-op, in-process substitutes for all AWS-backed services so that it can run
 * entirely without AWS credentials.
 *
 * <p>Activate the local profile with:
 * <pre>
 *   java -jar app.jar --spring.profiles.active=local
 *   # or via environment variable:
 *   SPRING_PROFILES_ACTIVE=local java -jar app.jar
 * </pre>
 */
@Configuration
@Profile("local")
public class LocalAwsConfig {
}
