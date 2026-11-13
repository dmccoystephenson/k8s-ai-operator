package com.stephenson.k8saioperator;

import com.stephenson.k8saioperator.model.AuditRecord;
import com.stephenson.k8saioperator.model.ParsedCommand;
import com.stephenson.k8saioperator.repository.AuditRecordRepository;
import com.stephenson.k8saioperator.service.PostgresAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Validates that PostgresAuditService correctly builds and persists AuditRecord
 * entities for both allowed and blocked commands: absent command fields are stored as
 * null, a null or blank block reason is stored as null, and repository failures on
 * either path are swallowed.
 */
@ExtendWith(MockitoExtension.class)
class PostgresAuditServiceTest {

    @Mock
    private AuditRecordRepository repository;

    private PostgresAuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new PostgresAuditService(repository);
    }

    // --- Allowed ---

    @Test
    void recordAllowed_savesRecordWithAllowedTrue() {
        ParsedCommand command = ParsedCommand.builder()
                .verb("get").resource("pods").namespace("production").build();

        auditService.recordAllowed("req-001", command, 42L);

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(repository).save(captor.capture());

        AuditRecord saved = captor.getValue();
        assertEquals("req-001", saved.getRequestId());
        assertTrue(saved.getAllowed());
        assertEquals("get", saved.getParsedVerb());
        assertEquals("pods", saved.getParsedResource());
        assertEquals("production", saved.getParsedNamespace());
        assertEquals(42L, saved.getExecutionLatencyMs());
        assertNull(saved.getBlockReason());
        assertNotNull(saved.getTimestamp());
        assertInstanceOf(Instant.class, saved.getTimestamp());
    }

    @Test
    void recordAllowed_withNullCommand_savesRecordWithNullFields() {
        auditService.recordAllowed("req-003", null, 5L);

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(repository).save(captor.capture());

        AuditRecord saved = captor.getValue();
        assertEquals("req-003", saved.getRequestId());
        assertTrue(saved.getAllowed());
        assertNull(saved.getParsedVerb());
        assertNull(saved.getParsedResource());
        assertNull(saved.getParsedNamespace());
    }

    @Test
    void recordAllowed_withPartialCommand_leavesOnlyMissingFieldsNull() {
        ParsedCommand command = ParsedCommand.builder().verb("get").resource("nodes").build();

        auditService.recordAllowed("req-005", command, 7L);

        AuditRecord saved = captureSavedRecord();
        assertEquals("get", saved.getParsedVerb());
        assertEquals("nodes", saved.getParsedResource());
        assertNull(saved.getParsedNamespace());
    }

    // --- Blocked ---

    @Test
    void recordBlocked_savesRecordWithAllowedFalseAndReason() {
        ParsedCommand command = ParsedCommand.builder()
                .verb("delete").resource("pods").namespace("default").build();

        auditService.recordBlocked("req-002", command, "Verb 'delete' is not permitted", 10L);

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(repository).save(captor.capture());

        AuditRecord saved = captor.getValue();
        assertEquals("req-002", saved.getRequestId());
        assertFalse(saved.getAllowed());
        assertEquals("Verb 'delete' is not permitted", saved.getBlockReason());
        assertEquals("delete", saved.getParsedVerb());
        assertEquals(10L, saved.getExecutionLatencyMs());
    }

    @Test
    void recordBlocked_withNullCommand_savesRecordWithNullFieldsAndReason() {
        auditService.recordBlocked("req-006", null, "Could not parse the prompt", 3L);

        AuditRecord saved = captureSavedRecord();
        assertEquals("req-006", saved.getRequestId());
        assertFalse(saved.getAllowed());
        assertEquals("Could not parse the prompt", saved.getBlockReason());
        assertNull(saved.getParsedVerb());
        assertNull(saved.getParsedResource());
        assertNull(saved.getParsedNamespace());
        assertEquals(3L, saved.getExecutionLatencyMs());
        assertNotNull(saved.getTimestamp());
    }

    @Test
    void recordBlocked_withNullReason_savesNullBlockReason() {
        auditService.recordBlocked("req-007", null, null, 1L);

        AuditRecord saved = captureSavedRecord();
        assertFalse(saved.getAllowed());
        assertNull(saved.getBlockReason());
    }

    @Test
    void recordBlocked_withBlankReason_savesNullBlockReason() {
        auditService.recordBlocked("req-008", null, "   ", 1L);

        AuditRecord saved = captureSavedRecord();
        assertFalse(saved.getAllowed());
        assertNull(saved.getBlockReason());
    }

    // --- Failure handling ---

    @Test
    void recordAllowed_shouldNotThrowWhenRepositoryFails() {
        ParsedCommand command = ParsedCommand.builder()
                .verb("get").resource("pods").namespace("default").build();
        doThrow(new RuntimeException("DB connection failed")).when(repository).save(any());

        // Should not propagate the exception
        assertDoesNotThrow(() -> auditService.recordAllowed("req-004", command, 1L));
    }

    @Test
    void recordBlocked_shouldNotThrowWhenRepositoryFails() {
        doThrow(new RuntimeException("DB connection failed")).when(repository).save(any());

        assertDoesNotThrow(() -> auditService.recordBlocked("req-009", null, "blocked", 1L));
        verify(repository).save(any(AuditRecord.class));
    }

    private AuditRecord captureSavedRecord() {
        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
