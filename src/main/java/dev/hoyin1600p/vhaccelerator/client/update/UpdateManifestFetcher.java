package dev.hoyin1600p.vhaccelerator.client.update;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

final class UpdateManifestFetcher {
    private static final int MAX_MANIFEST_CHARACTERS = 262_144;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private UpdateManifestFetcher() {
    }

    static CompletableFuture<Optional<UpdateNotice>> fetch(
            URI manifestUri,
            String modId,
            String displayName,
            String currentVersion,
            String minecraftVersion,
            String downloadUrl
    ) {
        return fetch(
                manifestUri,
                modId,
                displayName,
                currentVersion,
                minecraftVersion,
                downloadUrl,
                REQUEST_TIMEOUT
        );
    }

    static CompletableFuture<Optional<UpdateNotice>> fetch(
            URI manifestUri,
            String modId,
            String displayName,
            String currentVersion,
            String minecraftVersion,
            String downloadUrl,
            Duration requestTimeout
    ) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(manifestUri)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header(
                        "User-Agent",
                        modId + "/" + currentVersion + " update-check"
                )
                .GET()
                .build();

        return client.sendAsync(
                request,
                HttpResponse.BodyHandlers.ofString()
        ).thenApply(response -> {
            if (response.statusCode() != 200) {
                throw new CompletionException(new IOException(
                        "Update manifest returned HTTP "
                                + response.statusCode()
                ));
            }
            String body = response.body();
            if (body.length() > MAX_MANIFEST_CHARACTERS) {
                throw new CompletionException(new IOException(
                        "Update manifest exceeded "
                                + MAX_MANIFEST_CHARACTERS + " characters"
                ));
            }
            return UpdateManifestParser.parse(
                    body,
                    modId,
                    displayName,
                    currentVersion,
                    minecraftVersion,
                    downloadUrl
            );
        });
    }
}
