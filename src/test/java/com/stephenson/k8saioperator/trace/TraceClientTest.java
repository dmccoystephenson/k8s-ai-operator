package com.stephenson.k8saioperator.trace;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the vendored {@link TraceClient}.
 *
 * <p>Verifies the order in which reporting is switched off (environment, server-wide file,
 * the program's own setting, a missing key), how the server-wide file and the install ID are
 * read and written, how event tags are merged and encoded, and what a report sends. The
 * environment is replaced through the client's {@code environment} seam, files live in a
 * {@link TempDir}, and reports go to a loopback stub; nothing here reaches a real trace server.
 */
class TraceClientTest {

    @TempDir
    Path dir;

    private final Map<String, String> env = new HashMap<>();
    private Function<String, String> realEnvironment;

    private HttpServer stub;
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<String> paths = new CopyOnWriteArrayList<>();
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private final List<String> userAgents = new CopyOnWriteArrayList<>();
    private String endpoint;

    @BeforeEach
    void setUp() throws Exception {
        realEnvironment = TraceClient.environment;
        TraceClient.environment = env::get;

        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            userAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
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
    void tearDown() {
        TraceClient.environment = realEnvironment;
        stub.stop(0);
    }

    private TraceClient.Builder builder() {
        return TraceClient.builder(endpoint, "test-app", "1.0").key("a-key");
    }

    // --- Opt-outs ---

    @Test
    void keyAndNoOptOut_isEnabled() {
        TraceClient client = builder().build();

        assertTrue(client.isEnabled());
        assertNull(client.disabledReason());
        client.close();
    }

    @Test
    void usageReportingEnvOff_disablesCaseInsensitively() {
        for (String value : Arrays.asList("off", "FALSE", " 0 ", "No")) {
            env.put(TraceClient.ENV_USAGE_REPORTING, value);
            TraceClient client = builder().build();

            assertFalse(client.isEnabled(), value);
            assertEquals(TraceClient.REASON_ENVIRONMENT, client.disabledReason(), value);
        }
    }

    @Test
    void usageReportingEnvOtherValue_doesNotDisable() {
        env.put(TraceClient.ENV_USAGE_REPORTING, "on");
        TraceClient client = builder().build();

        assertTrue(client.isEnabled());
        client.close();
    }

    @Test
    void doNotTrackEnv_disables() {
        for (String value : Arrays.asList("1", "TRUE", "yes")) {
            env.put(TraceClient.ENV_DO_NOT_TRACK, value);
            TraceClient client = builder().build();

            assertEquals(TraceClient.REASON_ENVIRONMENT, client.disabledReason(), value);
        }
    }

    @Test
    void environment_winsOverConfigAndMissingKey() {
        env.put(TraceClient.ENV_DO_NOT_TRACK, "1");
        TraceClient client = TraceClient.builder(endpoint, "test-app", "1.0").enabled(false).build();

        assertEquals(TraceClient.REASON_ENVIRONMENT, client.disabledReason());
    }

    @Test
    void enabledFalse_winsOverMissingKey() {
        TraceClient client = TraceClient.builder(endpoint, "test-app", "1.0").enabled(false).build();

        assertEquals(TraceClient.REASON_CONFIG, client.disabledReason());
    }

    @Test
    void blankKey_isNoKey() {
        TraceClient client = TraceClient.builder(endpoint, "test-app", "1.0").key("  ").build();

        assertEquals(TraceClient.REASON_NO_KEY, client.disabledReason());
    }

    @Test
    void disabledClient_reportsNothingHasNoInstallIdAndClosesTwice() {
        TraceClient client = TraceClient.disabled();
        client.report("startup");
        client.close();
        client.close();

        assertFalse(client.isEnabled());
        assertEquals(TraceClient.REASON_CONFIG, client.disabledReason());
        assertNull(client.installId());
        assertTrue(bodies.isEmpty());
    }

    // --- Builder validation ---

    @Test
    void blankRequiredArguments_areRejected() {
        assertThrows(IllegalArgumentException.class, () -> TraceClient.builder(" ", "app", "1.0"));
        assertThrows(IllegalArgumentException.class, () -> TraceClient.builder(endpoint, null, "1.0"));
        assertThrows(IllegalArgumentException.class, () -> TraceClient.builder(endpoint, "app", ""));
    }

    @Test
    void overlongVersionOrInstallId_isRejected() {
        String overlong = "x".repeat(TraceClient.MAX_TAG_LENGTH + 1);

        assertThrows(IllegalArgumentException.class, () -> TraceClient.builder(endpoint, "app", overlong));
        assertThrows(IllegalArgumentException.class, () -> builder().installId(overlong));
    }

    // --- Server-wide config ---

    @Test
    void missingServerWideFile_isCreatedWithDefaultContentAndGainsServerId() throws Exception {
        TraceClient client = builder().serverWideConfig(dir.toFile()).build();
        Path file = dir.resolve(TraceClient.SERVER_WIDE_CONFIG_PATH);

        assertTrue(client.isEnabled());
        assertNotNull(client.installId());
        assertEquals(TraceClient.SERVER_WIDE_CONFIG_CONTENT + "server-id: " + client.installId() + "\n",
                Files.readString(file));
        client.close();
    }

    @Test
    void existingServerId_isReusedAndFileUnchanged() throws Exception {
        Path file = dir.resolve(TraceClient.SERVER_WIDE_CONFIG_PATH);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "enabled: true\nserver-id: kept-id\n");

        TraceClient client = builder().serverWideConfig(dir.toFile()).build();

        assertEquals("kept-id", client.installId());
        assertEquals("enabled: true\nserver-id: kept-id\n", Files.readString(file));
        client.close();
    }

    @Test
    void existingFileWithoutTrailingNewline_getsCommentAndServerIdAppended() throws Exception {
        Path file = dir.resolve(TraceClient.SERVER_WIDE_CONFIG_PATH);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "enabled: true");

        TraceClient client = builder().serverWideConfig(dir.toFile()).build();

        assertEquals("enabled: true\n" + TraceClient.SERVER_ID_COMMENT + "server-id: " + client.installId() + "\n",
                Files.readString(file));
        client.close();
    }

    @Test
    void serverWideEnabledFalse_disablesAndWritesNoServerId() throws Exception {
        Path file = dir.resolve(TraceClient.SERVER_WIDE_CONFIG_PATH);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "enabled: false\n");

        TraceClient client = builder().serverWideConfig(dir.toFile()).build();

        assertEquals(TraceClient.REASON_SERVER_WIDE, client.disabledReason());
        assertNull(client.installId());
        assertEquals("enabled: false\n", Files.readString(file));
    }

    @Test
    void environmentOptOut_neverCreatesServerWideFile() {
        env.put(TraceClient.ENV_USAGE_REPORTING, "off");
        builder().serverWideConfig(dir.toFile()).build();

        assertFalse(Files.exists(dir.resolve(TraceClient.SERVER_WIDE_CONFIG_PATH)));
    }

    @Test
    void parseServerWideConfig_readsSwitchTagsAndServerId() {
        TraceClient.ServerWideConfig config = TraceClient.parseServerWideConfig(Arrays.asList(
                "# comment",
                "tags:",
                "  ci: \"true\"",
                "  enabled: false",
                "  # skipped",
                "  'quoted': 'it''s'",
                "  bad key: dropped",
                "  url:http://x",
                "    deeper: ignored",
                "  trailing: value # comment",
                "  flow: [a, b]",
                "enabled: yes",
                "enabled: false",
                "server-id: not valid!",
                "server-id: id-1"));

        assertFalse(config.disables, "the first enabled: outside tags: is the switch");
        assertEquals("id-1", config.serverId, "an invalid server-id line is skipped");
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("ci", "true");
        expected.put("enabled", "false");
        expected.put("quoted", "it's");
        expected.put("trailing", "value");
        assertEquals(expected, config.tags);
    }

    @Test
    void parseServerWideConfig_keepsAtMostMaxTags() {
        List<String> lines = new ArrayList<>();
        lines.add("tags:");
        for (int i = 0; i < TraceClient.MAX_TAGS + 5; i++) {
            lines.add("  t" + i + ": v");
        }

        assertEquals(TraceClient.MAX_TAGS, TraceClient.parseServerWideConfig(lines).tags.size());
    }

    // --- Install ID ---

    @Test
    void explicitInstallId_winsOverInstallIdFile() throws Exception {
        File idFile = dir.resolve("id").toFile();
        TraceClient client = builder().installId("  explicit  ").installIdFile(idFile).build();

        assertEquals("explicit", client.installId());
        assertFalse(idFile.exists());
        client.close();
    }

    @Test
    void installIdFile_isCreatedThenReused() throws Exception {
        File idFile = dir.resolve("nested/id").toFile();
        TraceClient first = builder().installIdFile(idFile).build();
        TraceClient second = builder().installIdFile(idFile).build();

        assertEquals(first.installId() + "\n", Files.readString(idFile.toPath()));
        assertEquals(first.installId(), second.installId());
        first.close();
        second.close();
    }

    @Test
    void disabledClient_neverWritesInstallIdFile() {
        File idFile = dir.resolve("id").toFile();
        builder().enabled(false).installIdFile(idFile).build();

        assertFalse(idFile.exists());
    }

    @Test
    void installIdFromFile_skipsLinesThatAreNotIds() throws Exception {
        Path idFile = dir.resolve("id");
        Files.writeString(idFile, "# not an id\n\n  the-id  \nother\n");

        assertEquals("the-id", TraceClient.installIdFromFile(idFile.toFile()));
    }

    @Test
    void installIdFromFile_unreadableFileIsNotOverwritten() throws Exception {
        Path directory = Files.createDirectory(dir.resolve("a-directory"));

        assertNotNull(TraceClient.installIdFromFile(directory.toFile()));
        assertTrue(Files.isDirectory(directory));
    }

    // --- Tag merging ---

    @Test
    void withVersion_addsVersionUnlessEventHasOneAndCopies() {
        Map<String, String> tags = new HashMap<>();
        tags.put("name", "home");
        tags.put("dropped", null);

        Map<String, String> merged = TraceClient.withVersion(tags, "1.0");

        assertEquals(Map.of("name", "home", "version", "1.0"), merged);
        assertEquals(2, tags.size(), "the caller's map is not modified");
        assertEquals("own", TraceClient.withVersion(Map.of("version", "own"), "1.0").get("version"));
        assertEquals(Map.of("version", "1.0"), TraceClient.withVersion(null, "1.0"));
    }

    @Test
    void withInstall_keepsEventInstallAndRespectsMaxTags() {
        Map<String, String> own = Map.of(TraceClient.INSTALL_TAG, "own");
        assertSame(own, TraceClient.withInstall(own, "id"));
        Map<String, String> none = Map.of("a", "b");
        assertSame(none, TraceClient.withInstall(none, null));

        Map<String, String> full = new HashMap<>();
        for (int i = 0; i < TraceClient.MAX_TAGS; i++) {
            full.put("t" + i, "v");
        }
        assertSame(full, TraceClient.withInstall(full, "id"));
        assertEquals("id", TraceClient.withInstall(none, "id").get(TraceClient.INSTALL_TAG));
    }

    @Test
    void withServerWideTags_eventTagWinsAndMergeStopsAtMaxTags() {
        Map<String, String> event = new LinkedHashMap<>();
        event.put("ci", "false");
        Map<String, String> serverWide = new LinkedHashMap<>();
        serverWide.put("ci", "true");
        serverWide.put("site", "lab");

        assertEquals(Map.of("ci", "false", "site", "lab"), TraceClient.withServerWideTags(event, serverWide));
        assertSame(event, TraceClient.withServerWideTags(event, Map.of()));

        Map<String, String> full = new LinkedHashMap<>();
        for (int i = 0; i < TraceClient.MAX_TAGS; i++) {
            full.put("t" + i, "v");
        }
        assertFalse(TraceClient.withServerWideTags(full, serverWide).containsKey("site"));
    }

    // --- JSON ---

    @Test
    void json_omitsNonFiniteValueAndNullTags() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("a", "1");
        tags.put("b", null);

        assertEquals("{\"application\":\"app\",\"name\":\"n\",\"value\":2.5,\"tags\":{\"a\":\"1\"}}",
                TraceClient.json("app", "n", 2.5, tags));
        assertEquals("{\"application\":\"app\",\"name\":\"n\"}", TraceClient.json("app", "n", Double.NaN, null));
        assertEquals("{\"application\":\"app\",\"name\":\"n\"}",
                TraceClient.json("app", "n", Double.POSITIVE_INFINITY, Map.of()));
    }

    @Test
    void quote_escapesQuotesBackslashesAndControlCharacters() {
        assertEquals("\"a\\\"b\\\\c\\nd\\re\\tf\\u0001\"", TraceClient.quote("a\"b\\c\nd\re\tf\u0001"));
    }

    // --- Sending ---

    @Test
    void report_postsToMetricsEndpointWithKeyAndUserAgent() {
        TraceClient client = TraceClient.builder(endpoint + "//", "test-app", "1.0")
                .key("a-key").installId("id-1").build();
        client.report("command", 1.0, Map.of("name", "get"));
        client.close();

        assertEquals(List.of("/api/metrics"), paths);
        assertEquals(List.of("Bearer a-key"), authorizations);
        assertEquals(List.of("trace-client/" + TraceClient.VERSION + " (test-app)"), userAgents);
        assertEquals("{\"application\":\"test-app\",\"name\":\"command\",\"value\":1.0,"
                + "\"tags\":{\"name\":\"get\",\"version\":\"1.0\",\"install\":\"id-1\"}}", bodies.get(0));
    }

    @Test
    void blankEventName_sendsNothing() {
        TraceClient client = builder().build();
        client.report(" ");
        client.report(null, null, null);
        client.close();

        assertTrue(bodies.isEmpty());
    }

    @Test
    void unreachableServer_neverThrows() {
        TraceClient client = TraceClient.builder("http://127.0.0.1:1", "test-app", "1.0").key("a-key").build();

        client.report("startup");
        client.close();

        assertTrue(bodies.isEmpty());
    }
}
