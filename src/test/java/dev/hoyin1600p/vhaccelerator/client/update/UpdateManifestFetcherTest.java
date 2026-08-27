package dev.hoyin1600p.vhaccelerator.client.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UpdateManifestFetcherTest {
    private static final String DOWNLOAD_URL =
            "https://www.curseforge.com/minecraft/mc-mods/vh-accelerator";
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                0
        );
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void acceptsAValidManifest() {
        respond("/valid", 200, """
                {
                  "1.18.2": {"1.0.12": "Performance Improvement"},
                  "promos": {"1.18.2-latest": "1.0.12"}
                }
                """);

        Optional<UpdateNotice> result = fetch("/valid").join();

        assertTrue(result.isPresent());
        assertEquals("1.0.12", result.orElseThrow().targetVersion());
    }

    @Test
    void rejectsANonSuccessResponse() {
        respond("/missing", 404, "not found");

        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> fetch("/missing").join()
        );

        assertTrue(exception.getCause().getMessage().contains("HTTP 404"));
    }

    @Test
    void rejectsMalformedJsonWithoutHanging() {
        respond("/malformed", 200, "this is not json");

        assertThrows(
                CompletionException.class,
                () -> fetch("/malformed").join()
        );
    }

    @Test
    void rejectsAManifestOverTheConfiguredCharacterLimit() {
        respond("/oversized", 200, "x".repeat(262_145));

        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> fetch("/oversized").join()
        );

        assertTrue(exception.getCause().getMessage().contains("exceeded"));
    }

    @Test
    void propagatesAnAbruptConnectionFailure() {
        server.createContext("/closed", HttpExchange::close);

        assertThrows(
                CompletionException.class,
                () -> fetch("/closed").join()
        );
    }

    @Test
    void timesOutAStalledResponse() {
        server.createContext("/stalled", exchange -> {
            try {
                Thread.sleep(1_000L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });

        assertThrows(
                CompletionException.class,
                () -> UpdateManifestFetcher.fetch(
                        uri("/stalled"),
                        "vhaccelerator",
                        "VH Accelerator",
                        "1.0.11",
                        "1.18.2",
                        DOWNLOAD_URL,
                        Duration.ofMillis(100L)
                ).join()
        );
    }

    private java.util.concurrent.CompletableFuture<Optional<UpdateNotice>> fetch(
            String path
    ) {
        return UpdateManifestFetcher.fetch(
                uri(path),
                "vhaccelerator",
                "VH Accelerator",
                "1.0.11",
                "1.18.2",
                DOWNLOAD_URL
        );
    }

    private URI uri(String path) {
        return URI.create("http://" + server.getAddress().getHostString()
                + ":" + server.getAddress().getPort() + path);
    }

    private void respond(String path, int status, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        server.createContext(path, exchange -> {
            exchange.getResponseHeaders().set(
                    "Content-Type",
                    "application/json; charset=utf-8"
            );
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
    }
}
