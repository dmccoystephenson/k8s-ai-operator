package com.stephenson.k8saioperator;

import com.stephenson.k8saioperator.model.ParsedCommand;
import com.stephenson.k8saioperator.service.NoOpAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Unit tests for {@link NoOpAuditService}.
 * Verifies that both audit record methods complete without error and handle
 * null {@link ParsedCommand} gracefully.
 */
class NoOpAuditServiceTest {

    private NoOpAuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new NoOpAuditService();
    }

    @Test
    void recordAllowed_completesWithoutException() {
        ParsedCommand command = ParsedCommand.builder()
                .verb("get")
                .resource("pods")
                .namespace("production")
                .build();

        assertDoesNotThrow(() -> auditService.recordAllowed("req-001", command, 5L));
    }

    @Test
    void recordAllowed_handlesNullCommand() {
        assertDoesNotThrow(() -> auditService.recordAllowed("req-002", null, 3L));
    }

    @Test
    void recordBlocked_completesWithoutException() {
        ParsedCommand command = ParsedCommand.builder()
                .verb("delete")
                .resource("pods")
                .namespace("default")
                .build();

        assertDoesNotThrow(() -> auditService.recordBlocked("req-003", command, "Verb 'delete' is not permitted", 2L));
    }

    @Test
    void recordBlocked_handlesNullCommand() {
        assertDoesNotThrow(() -> auditService.recordBlocked("req-004", null, "parse error", 1L));
    }
}
