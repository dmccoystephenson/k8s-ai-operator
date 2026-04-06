package com.stephenson.k8saioperator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;

/**
 * JPA-related autoconfiguration is excluded here so the application starts
 * without a DataSource in the {@code aws} (default) profile. The {@code local}
 * profile re-enables it via {@link com.stephenson.k8saioperator.config.LocalDatabaseConfig}.
 */
@Slf4j
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class,
        SqlInitializationAutoConfiguration.class
})
public class K8sAiOperatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(K8sAiOperatorApplication.class, args);
        log.info("k8s-ai-operator started");
    }
}
