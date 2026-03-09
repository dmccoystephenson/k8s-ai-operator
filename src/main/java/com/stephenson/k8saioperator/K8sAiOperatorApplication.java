package com.stephenson.k8saioperator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class K8sAiOperatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(K8sAiOperatorApplication.class, args);
        log.info("k8s-ai-operator started");
    }
}
