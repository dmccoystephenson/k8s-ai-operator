package com.stephenson.k8saioperator;

import com.stephenson.k8saioperator.service.VerbGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the verb hard-blocklist is enforced unconditionally.
 */
class VerbGuardTest {

    private VerbGuard verbGuard;

    @BeforeEach
    void setUp() {
        verbGuard = new VerbGuard("get,apply", "delete,exec,scale,patch");
    }

    // --- Allowed verbs ---

    @Test
    void get_isAllowed() {
        assertTrue(verbGuard.isAllowed("get"));
    }

    @Test
    void apply_isAllowed() {
        assertTrue(verbGuard.isAllowed("apply"));
    }

    // --- Disallowed verbs ---

    @Test
    void delete_isBlocked() {
        assertFalse(verbGuard.isAllowed("delete"));
        assertTrue(verbGuard.isDisallowed("delete"));
    }

    @Test
    void exec_isBlocked() {
        assertFalse(verbGuard.isAllowed("exec"));
        assertTrue(verbGuard.isDisallowed("exec"));
    }

    @Test
    void scale_isBlocked() {
        assertFalse(verbGuard.isAllowed("scale"));
        assertTrue(verbGuard.isDisallowed("scale"));
    }

    @Test
    void patch_isBlocked() {
        assertFalse(verbGuard.isAllowed("patch"));
        assertTrue(verbGuard.isDisallowed("patch"));
    }

    // --- Edge cases ---

    @Test
    void null_isBlocked() {
        assertFalse(verbGuard.isAllowed(null));
        assertTrue(verbGuard.isDisallowed(null));
    }

    @Test
    void unknownVerb_isNotAllowed() {
        assertFalse(verbGuard.isAllowed("create"));
    }

    @Test
    void verbCheck_isCaseInsensitive() {
        assertTrue(verbGuard.isAllowed("GET"));
        assertTrue(verbGuard.isAllowed("Apply"));
        assertFalse(verbGuard.isAllowed("DELETE"));
    }
}

