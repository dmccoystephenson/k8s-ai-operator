package com.stephenson.k8saioperator.config;

import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Re-enables JPA and DataSource autoconfiguration exclusively for the {@code local}
 * Spring profile. In all other profiles these autoconfiguration classes are excluded
 * by {@link com.stephenson.k8saioperator.K8sAiOperatorApplication} so the application
 * can start without a DataSource (e.g. when using DynamoDB as the audit store on AWS).
 *
 * <p>The actual datasource connection is configured in {@code application-local.yml}.
 */
@Configuration
@Profile("local")
@ImportAutoConfiguration({
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class,
        SqlInitializationAutoConfiguration.class
})
public class LocalDatabaseConfig {
}
