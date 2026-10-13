package com.stephenson.k8saioperator;

import com.stephenson.k8saioperator.model.ParsedCommand;
import com.stephenson.k8saioperator.service.DynamoDbAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Validates that DynamoDbAuditService builds and writes the expected DynamoDB item
 * for both allowed and blocked commands, omits absent fields rather than writing
 * nulls, and never propagates a DynamoDB failure to the caller.
 */
@ExtendWith(MockitoExtension.class)
class DynamoDbAuditServiceTest {

    private static final String TABLE_NAME = "K8sAgentExecutions-test";

    @Mock
    private DynamoDbClient dynamoDbClient;

    private DynamoDbAuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new DynamoDbAuditService(dynamoDbClient, TABLE_NAME);
    }

    private PutItemRequest capturePutItem() {
        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient).putItem(captor.capture());
        return captor.getValue();
    }

    // --- Allowed ---

    @Test
    void recordAllowed_writesItemWithAllowedTrue() {
        ParsedCommand command = ParsedCommand.builder()
                .verb("get").resource("pods").namespace("production").build();

        auditService.recordAllowed("req-001", command, 42L);

        PutItemRequest request = capturePutItem();
        assertEquals(TABLE_NAME, request.tableName());

        Map<String, AttributeValue> item = request.item();
        assertEquals("req-001", item.get("request_id").s());
        assertTrue(item.get("allowed").bool());
        assertEquals("get", item.get("parsed_verb").s());
        assertEquals("pods", item.get("parsed_resource").s());
        assertEquals("production", item.get("parsed_namespace").s());
        assertEquals("42", item.get("execution_latency_ms").n());
        assertFalse(item.containsKey("block_reason"));
        assertDoesNotThrow(() -> Instant.parse(item.get("timestamp").s()));
    }

    @Test
    void recordAllowed_withNullCommand_omitsParsedFields() {
        auditService.recordAllowed("req-002", null, 5L);

        Map<String, AttributeValue> item = capturePutItem().item();
        assertEquals(
                Set.of("request_id", "timestamp", "execution_latency_ms", "allowed"),
                item.keySet());
        assertTrue(item.get("allowed").bool());
    }

    @Test
    void recordAllowed_withPartialCommand_omitsOnlyNullFields() {
        ParsedCommand command = ParsedCommand.builder().verb("get").resource("nodes").build();

        auditService.recordAllowed("req-003", command, 7L);

        Map<String, AttributeValue> item = capturePutItem().item();
        assertEquals("get", item.get("parsed_verb").s());
        assertEquals("nodes", item.get("parsed_resource").s());
        assertFalse(item.containsKey("parsed_namespace"));
    }

    // --- Blocked ---

    @Test
    void recordBlocked_writesItemWithAllowedFalseAndReason() {
        ParsedCommand command = ParsedCommand.builder()
                .verb("delete").resource("pods").namespace("default").build();

        auditService.recordBlocked("req-004", command, "Verb 'delete' is not permitted", 10L);

        Map<String, AttributeValue> item = capturePutItem().item();
        assertEquals("req-004", item.get("request_id").s());
        assertFalse(item.get("allowed").bool());
        assertEquals("Verb 'delete' is not permitted", item.get("block_reason").s());
        assertEquals("delete", item.get("parsed_verb").s());
        assertEquals("10", item.get("execution_latency_ms").n());
    }

    @Test
    void recordBlocked_withNullReason_omitsBlockReason() {
        auditService.recordBlocked("req-005", null, null, 1L);

        Map<String, AttributeValue> item = capturePutItem().item();
        assertFalse(item.get("allowed").bool());
        assertFalse(item.containsKey("block_reason"));
    }

    @Test
    void recordBlocked_withBlankReason_omitsBlockReason() {
        auditService.recordBlocked("req-006", null, "   ", 1L);

        Map<String, AttributeValue> item = capturePutItem().item();
        assertFalse(item.containsKey("block_reason"));
    }

    // --- Failure handling ---

    @Test
    void recordAllowed_shouldNotThrowWhenDynamoDbFails() {
        ParsedCommand command = ParsedCommand.builder()
                .verb("get").resource("pods").namespace("default").build();
        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenThrow(new RuntimeException("DynamoDB unavailable"));

        assertDoesNotThrow(() -> auditService.recordAllowed("req-007", command, 1L));
    }

    @Test
    void recordBlocked_shouldNotThrowWhenDynamoDbFails() {
        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenThrow(new RuntimeException("DynamoDB unavailable"));

        assertDoesNotThrow(() -> auditService.recordBlocked("req-008", null, "blocked", 1L));
    }
}
