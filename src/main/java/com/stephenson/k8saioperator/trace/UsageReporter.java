package com.stephenson.k8saioperator.trace;

import com.stephenson.k8saioperator.K8sAiOperatorApplication;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Reports a single {@code startup} event to the trace usage-tracking service once the
 * application is ready, and nothing else.
 *
 * <p>What is sent: the program name ({@value #APPLICATION}), the event name, the operator's own
 * version (the Maven project version, or the jar's {@code Implementation-Version} when the
 * property is absent), the static tag {@code service=true}, which marks it as a self-hosted
 * service, and a random per-installation ID as the tag {@code install} (see {@link TraceInstallId}),
 * so running deployments can be counted rather than events. Nothing per request, and nothing about prompts, commands, clusters, namespaces,
 * users, hosts or addresses is ever included.
 *
 * <p>The event is sent by {@link TraceClient}, which returns immediately, never throws, and
 * queues at most {@link TraceClient#QUEUE_CAPACITY} reports, so an unreachable trace server
 * costs the operator nothing but a dropped report. Reporting is on by default and is configured
 * through the {@code usage-reporting.*} properties in {@code application.yml} (each with an
 * environment override): set {@code USAGE_REPORTING_ENABLED=false} (Helm:
 * {@code usageReporting.enabled=false}) to turn it off; it fails closed, so any value but
 * {@code true}, {@code on}, {@code yes} or {@code 1} -- a blank one or a typo included -- turns it
 * off, and only an unset property defaults to on. A blank key also disables it, and a
 * blank endpoint, an endpoint that is not an absolute {@code http} or {@code https} URL, or an
 * overlong version yields a disabled client and a warning rather than a failed start-up.
 * {@code TRACE_USAGE_REPORTING=off} and {@code DO_NOT_TRACK=1} in the environment turn it off
 * too: the client checks those two itself, before this program's own setting, so they always
 * win. Details: {@value #DETAILS_URL}.
 */
@Component
@Slf4j
public class UsageReporter {

    /** The name the program key was issued for; the {@code application} field of every event. */
    static final String APPLICATION = "k8s-ai-operator";

    static final String STARTUP_EVENT = "startup";

    /** Reported as the version when neither the property nor the jar manifest gives one. */
    static final String UNKNOWN_VERSION = "unknown";

    /** Where what is and is not sent, and every way to turn it off, is written up. */
    static final String DETAILS_URL = "https://danielstephenson.dev/usage-reporting";

    private final TraceClient client;
    private final String version;

    public UsageReporter(
            @Value("${usage-reporting.enabled:true}") String enabled,
            @Value("${usage-reporting.endpoint:https://trace.danielstephenson.dev}") String endpoint,
            @Value("${usage-reporting.key:}") String key,
            @Value("${usage-reporting.version:}") String version) {
        this.version = resolveVersion(version);
        // Read as text and through the client's helper rather than bound as a boolean: only
        // true/on/yes/1 keep reporting on, so false, off, no, 0, a blank USAGE_REPORTING_ENABLED
        // or a typo turn it off instead of failing the start-up or being read as on. The
        // default "true" applies only when the property is not set at all.
        this.client = buildClient(TraceClient.allowsReporting(enabled), endpoint, key, this.version);
        if (client.isEnabled()) {
            log.info("Usage reporting is on: a startup event (program name, version, service=true and a random "
                    + "installation ID only) is sent to {}; "
                    + "set USAGE_REPORTING_ENABLED=false (usage-reporting.enabled=false) or TRACE_USAGE_REPORTING=off "
                    + "to turn it off. Details: {}", endpoint, DETAILS_URL);
        } else {
            log.info("Usage reporting is off ({}).", disabledReason(client));
        }
    }

    /** Whether a startup event will actually be sent. */
    public boolean isReporting() {
        return client.isEnabled();
    }

    /** The random installation ID sent as the tag {@code install}, or null while reporting is off. */
    public String installId() {
        return client.installId();
    }

    /** Sends the one startup event, once the context is fully up. Returns immediately. */
    @EventListener(ApplicationReadyEvent.class)
    public void reportStartup() {
        client.report(STARTUP_EVENT, null, startupTags());
    }

    /** Stops the client's sending thread when the context shuts down. */
    @PreDestroy
    public void close() {
        client.close();
    }

    /**
     * The tags attached to the startup event: {@code service=true}. The client adds the
     * version and the installation ID itself, as the tags {@code version} and {@code install},
     * to every event.
     */
    Map<String, String> startupTags() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("service", "true");
        return tags;
    }

    /** The version that will be reported, or {@value #UNKNOWN_VERSION} if none could be determined. */
    String version() {
        return version;
    }

    /**
     * The client's reason for sending nothing, with its Bukkit-flavoured name for the program's
     * own switch replaced by the property this service actually reads.
     */
    private static String disabledReason(TraceClient client) {
        String reason = client.disabledReason();
        return TraceClient.REASON_CONFIG.equals(reason) ? "usage-reporting.enabled" : reason;
    }

    private static TraceClient buildClient(boolean enabled, String endpoint, String key, String version) {
        try {
            if (enabled) {
                checkEndpoint(endpoint);
            }
            // The program's own switch goes to the builder rather than short-circuiting here, so
            // the client applies its precedence (environment first) and disabledReason() names
            // the switch that actually turned reporting off.
            return TraceClient.builder(endpoint, APPLICATION, version)
                    .key(key)
                    .enabled(enabled)
                    .installId(TraceInstallId.fromEnvironment())
                    .installIdFile(TraceInstallId.file(APPLICATION))
                    .logger(Logger.getLogger(UsageReporter.class.getName()))
                    .build();
        } catch (RuntimeException invalid) {
            // A usage report must never be the reason the operator fails to start.
            log.warn("Usage reporting is off: the usage-reporting settings could not be used ({})", invalid.getMessage());
            return TraceClient.disabled();
        }
    }

    /**
     * Rejects a non-blank endpoint that the client could never send to: anything that is not an
     * absolute {@code http} or {@code https} URL with a host. A blank one is left to the builder,
     * which rejects it itself.
     *
     * @throws IllegalArgumentException naming {@code usage-reporting.endpoint} when it is unusable
     */
    static void checkEndpoint(String endpoint) {
        if (endpoint == null || endpoint.trim().isEmpty()) {
            return;
        }
        String trimmed = endpoint.trim();
        URI uri;
        try {
            uri = new URI(trimmed);
            uri.toURL();
        } catch (URISyntaxException | MalformedURLException | IllegalArgumentException invalid) {
            throw new IllegalArgumentException("usage-reporting.endpoint is not a usable URL: " + trimmed, invalid);
        }
        String scheme = uri.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) || uri.getHost() == null) {
            throw new IllegalArgumentException("usage-reporting.endpoint is not an http or https URL: " + trimmed);
        }
    }

    private static String resolveVersion(String configured) {
        if (configured != null && !configured.trim().isEmpty() && !configured.contains("@project.version@")) {
            return configured.trim();
        }
        Package pkg = K8sAiOperatorApplication.class.getPackage();
        String fromManifest = pkg == null ? null : pkg.getImplementationVersion();
        return fromManifest == null || fromManifest.trim().isEmpty() ? UNKNOWN_VERSION : fromManifest.trim();
    }
}
