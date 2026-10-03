package com.stephenson.k8saioperator.trace;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Exercises the reporter against a loopback stub; nothing here ever reaches the real service. */
class UsageReporterTest {

    private HttpServer stub;
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private String endpoint;

    @BeforeEach
    void startStub() throws Exception {
        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/api/metrics", exchange -> {
            try (InputStream in = exchange.getRequestBody()) {
                bodies.add(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });
        stub.start();
        endpoint = "http://127.0.0.1:" + stub.getAddress().getPort();
    }

    @AfterEach
    void stopStub() {
        stub.stop(0);
    }

    private static boolean environmentOptsOut() {
        String trace = System.getenv(TraceClient.ENV_USAGE_REPORTING);
        String dnt = System.getenv(TraceClient.ENV_DO_NOT_TRACK);
        return (trace != null && !trace.isBlank()) || (dnt != null && !dnt.isBlank());
    }

    @Test
    void carriesARandomInstallationIdOnlyWhileReportingIsOn() {
        UsageReporter on = new UsageReporter(true, endpoint, "a-key", "1.0");
        UsageReporter off = new UsageReporter(false, endpoint, "a-key", "1.0");

        assertNotNull(on.installId(), "an enabled client carries a random installation ID");
        assertNull(off.installId(), "a disabled client never makes up an ID");
        on.close();
        off.close();
    }

    @Test
    void disabledSettingSendsNothing() {
        UsageReporter reporter = new UsageReporter(false, endpoint, "a-key", "1.0");
        reporter.reportStartup();
        reporter.close();

        assertFalse(reporter.isReporting());
        assertTrue(bodies.isEmpty());
    }

    @Test
    void blankKeySendsNothing() {
        UsageReporter reporter = new UsageReporter(true, endpoint, "", "1.0");
        reporter.reportStartup();
        reporter.close();

        assertFalse(reporter.isReporting());
        assertTrue(bodies.isEmpty());
    }

    @Test
    void unfilteredVersionPlaceholderIsNotSent() {
        UsageReporter reporter = new UsageReporter(false, endpoint, "a-key", "@project.version@");
        // Outside a packaged jar there is no manifest version either.
        assertEquals("unknown", reporter.version());
        assertEquals("true", reporter.startupTags().get("service"));
        assertFalse(reporter.startupTags().containsKey("version"), "the client adds version itself");
    }

    @Test
    void sendsOneStartupEventWithServiceAndVersionTags() {
        if (environmentOptsOut()) {
            return; // the environment has switched reporting off; that path is the client's to test
        }
        UsageReporter reporter = new UsageReporter(true, endpoint, "a-key", "1.2.3");
        reporter.reportStartup();
        reporter.close();

        assertEquals(1, bodies.size());
        assertEquals("{\"application\":\"k8s-ai-operator\",\"name\":\"startup\","
                + "\"tags\":{\"service\":\"true\",\"version\":\"1.2.3\",\"install\":\"" + reporter.installId() + "\"}}", bodies.get(0));
    }
}
