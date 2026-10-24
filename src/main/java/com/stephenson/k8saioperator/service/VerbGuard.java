package com.stephenson.k8saioperator.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Enforces the verb allowlist at the service layer.
 * Disallowed verbs are rejected in code regardless of what Bedrock returns.
 */
@Service
public class VerbGuard {

    private final List<String> allowedVerbs;
    private final List<String> disallowedVerbs;

    public VerbGuard(
            @Value("${k8s.allowed-verbs}") String allowedVerbsCsv,
            @Value("${k8s.disallowed-verbs}") String disallowedVerbsCsv) {
        this.allowedVerbs = parseVerbs(allowedVerbsCsv);
        this.disallowedVerbs = parseVerbs(disallowedVerbsCsv);
    }

    /**
     * Returns true when the verb is on the explicit allowlist and not on the disallowlist.
     * The disallowlist always wins, so a verb listed in both is rejected.
     */
    public boolean isAllowed(String verb) {
        if (verb == null) return false;
        if (isDisallowed(verb)) return false;
        return allowedVerbs.contains(verb.trim().toLowerCase());
    }

    /**
     * Returns true when the verb is on the hard-blocked disallowlist.
     * A verb may be neither allowed nor disallowed — it is still rejected.
     */
    public boolean isDisallowed(String verb) {
        if (verb == null) return true;
        return disallowedVerbs.contains(verb.trim().toLowerCase());
    }

    public List<String> getAllowedVerbs() {
        return allowedVerbs;
    }

    /**
     * Splits a comma-separated verb list, trimming and lowercasing each entry
     * and dropping blanks, so "get, Apply" matches the same verbs as "get,apply".
     */
    private static List<String> parseVerbs(String csv) {
        return Arrays.stream(csv.split(","))
                .map(v -> v.trim().toLowerCase())
                .filter(v -> !v.isEmpty())
                .toList();
    }
}

