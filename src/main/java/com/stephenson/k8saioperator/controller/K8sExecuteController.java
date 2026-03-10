package com.stephenson.k8saioperator.controller;

import com.stephenson.k8saioperator.metrics.MetricsEmitter;
import com.stephenson.k8saioperator.model.ExecuteRequest;
import com.stephenson.k8saioperator.model.ExecuteResponse;
import com.stephenson.k8saioperator.model.ParsedCommand;
import com.stephenson.k8saioperator.service.AuditPort;
import com.stephenson.k8saioperator.service.CommandParser;
import com.stephenson.k8saioperator.service.K8sClientAdapter;
import com.stephenson.k8saioperator.service.VerbGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes POST /k8s/execute.
 *
 * Request flow:
 * 1. Parse user prompt via Bedrock
 * 2. Enforce verb allowlist (VerbGuard)
 * 3. Execute against mock K8s client
 * 4. Persist audit record in DynamoDB
 * 5. Emit CloudWatch metrics
 */
@Slf4j
@RestController
@RequestMapping("/k8s")
@RequiredArgsConstructor
public class K8sExecuteController {

    private final CommandParser commandParser;
    private final VerbGuard verbGuard;
    private final K8sClientAdapter k8sClientAdapter;
    private final AuditPort auditService;
    private final MetricsEmitter metricsEmitter;

    @PostMapping("/execute")
    public ResponseEntity<ExecuteResponse> execute(@RequestBody ExecuteRequest request) {
        long startTime = System.currentTimeMillis();

        ParsedCommand command = null;
        try {
            // Step 1 — parse (user prompt is never logged)
            command = commandParser.parse(request.getUserPrompt());

            // Step 2 — enforce verb allowlist
            if (!verbGuard.isAllowed(command.getVerb())) {
                String reason = String.format("Verb '%s' is not permitted", command.getVerb());
                long latency = System.currentTimeMillis() - startTime;
                auditService.recordBlocked(request.getRequestId(), command, reason, latency);
                metricsEmitter.emitBlockedCommand();
                metricsEmitter.emitLatency(latency);

                return ResponseEntity.badRequest().body(ExecuteResponse.builder()
                        .requestId(request.getRequestId())
                        .allowed(false)
                        .reason(reason)
                        .build());
            }

            // Step 3 — execute
            String result = k8sClientAdapter.execute(command);
            long latency = System.currentTimeMillis() - startTime;

            // Step 4 — audit
            auditService.recordAllowed(request.getRequestId(), command, latency);

            // Step 5 — metrics
            metricsEmitter.emitAllowedCommand();
            metricsEmitter.emitLatency(latency);

            return ResponseEntity.ok(ExecuteResponse.builder()
                    .requestId(request.getRequestId())
                    .command(command)
                    .result(result)
                    .allowed(true)
                    .build());

        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("Execution failed for request_id={}: {}", request.getRequestId(), e.getMessage());
            auditService.recordBlocked(request.getRequestId(), command, e.getMessage(), latency);
            metricsEmitter.emitBlockedCommand();
            metricsEmitter.emitLatency(latency);

            return ResponseEntity.badRequest().body(ExecuteResponse.builder()
                    .requestId(request.getRequestId())
                    .allowed(false)
                    .reason(e.getMessage())
                    .build());
        }
    }
}

