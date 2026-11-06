package com.stephenson.k8saioperator;

import com.stephenson.k8saioperator.model.ExecuteRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that printing a request cannot put the user prompt into a log line.
 */
class ExecuteRequestTest {

    @Test
    void toString_leavesOutThePromptAndKeepsTheRequestId() {
        ExecuteRequest request = new ExecuteRequest();
        request.setRequestId("sentinel-request-id");
        request.setUserPrompt("sentinel-user-prompt");

        String printed = request.toString();

        assertFalse(printed.contains("sentinel-user-prompt"), printed);
        assertTrue(printed.contains("sentinel-request-id"), printed);
    }
}
