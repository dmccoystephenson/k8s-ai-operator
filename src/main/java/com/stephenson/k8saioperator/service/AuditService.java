package com.stephenson.k8saioperator.service;

import com.stephenson.k8saioperator.model.ParsedCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Writes a full audit record to DynamoDB for every request — allowed or blocked.
 * Raw user prompts are NEVER stored.
 */
@Slf4j
@Service
public class AuditService {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public AuditService(
            DynamoDbClient dynamoDbClient,
            @Value("${aws.dynamodb.table-name}") String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    /**
     * Persists an allowed execution to the audit table.
     */
    public void recordAllowed(String requestId, ParsedCommand command, long latencyMs) {
        Map<String, AttributeValue> item = baseItem(requestId, command, latencyMs);
        item.put("allowed", AttributeValue.builder().bool(true).build());
        persist(requestId, item);
    }

    /**
     * Persists a blocked execution to the audit table, including the block reason.
     */
    public void recordBlocked(String requestId, ParsedCommand command, String blockReason, long latencyMs) {
        Map<String, AttributeValue> item = baseItem(requestId, command, latencyMs);
        item.put("allowed", AttributeValue.builder().bool(false).build());
        item.put("block_reason", AttributeValue.builder().s(blockReason).build());
        persist(requestId, item);
    }

    private Map<String, AttributeValue> baseItem(String requestId, ParsedCommand command, long latencyMs) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("request_id", AttributeValue.builder().s(requestId).build());
        item.put("timestamp", AttributeValue.builder().s(Instant.now().toString()).build());
        item.put("execution_latency_ms", AttributeValue.builder().n(String.valueOf(latencyMs)).build());

        if (command != null) {
            if (command.getVerb() != null) {
                item.put("parsed_verb", AttributeValue.builder().s(command.getVerb()).build());
            }
            if (command.getResource() != null) {
                item.put("parsed_resource", AttributeValue.builder().s(command.getResource()).build());
            }
            if (command.getNamespace() != null) {
                item.put("parsed_namespace", AttributeValue.builder().s(command.getNamespace()).build());
            }
        }

        return item;
    }

    private void persist(String requestId, Map<String, AttributeValue> item) {
        try {
            dynamoDbClient.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .build());
            log.debug("Audit record written for request_id={}", requestId);
        } catch (Exception e) {
            log.error("Failed to write audit record for request_id={}: {}", requestId, e.getMessage());
        }
    }
}

