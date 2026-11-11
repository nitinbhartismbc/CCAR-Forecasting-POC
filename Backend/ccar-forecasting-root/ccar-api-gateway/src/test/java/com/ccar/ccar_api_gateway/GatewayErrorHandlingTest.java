package com.ccar.ccar_api_gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end gateway tests against a real (in-process) downstream HTTP server:
 * 401 variants, downstream timeout, downstream down, pass-through of downstream errors, unknown route and
 * correlation-id propagation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "20s")
class GatewayErrorHandlingTest {

    private static final HttpServer DOWNSTREAM = startDownstream();
    private static final int DEAD_PORT = freePort();
    private static final AtomicReference<String> RECEIVED_CORRELATION_ID = new AtomicReference<>();

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("ccar.gateway.services.ccar-core.uri",
                () -> "http://localhost:" + DOWNSTREAM.getAddress().getPort());
        registry.add("ccar.gateway.services.ccar-core.response-timeout-ms", () -> "1000");
        // nothing listens here -> connection refused
        registry.add("ccar.gateway.services.user-service.uri", () -> "http://localhost:" + DEAD_PORT);
    }

    @AfterAll
    static void stopDownstream() {
        DOWNSTREAM.stop(0);
    }

    @Autowired
    private WebTestClient client;

    @Value("${ccar.security.jwt.secret}")
    private String jwtSecret;

    @BeforeEach
    void reset() {
        RECEIVED_CORRELATION_ID.set(null);
    }

    // ---------------------------------------------------------------------------------------------- 401s

    @Test
    void missingToken_is401ProblemWithWwwAuthenticate() {
        EntityExchangeResult<byte[]> result = client.get().uri("/ccar/forecast/ok")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().valueMatches(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"ccar\"")
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.errorCode").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.type").isEqualTo("urn:ccar:error:unauthorized")
                .jsonPath("$.instance").isEqualTo("/ccar/forecast/ok")
                .jsonPath("$.service").isEqualTo("ccar-api-gateway")
                .returnResult();

        // the id in the header and in the body are the same, so a user report can be traced in the logs
        String headerId = result.getResponseHeaders().getFirst("X-Correlation-Id");
        assertThat(headerId).isNotBlank();
        assertThat(new String(result.getResponseBody(), StandardCharsets.UTF_8))
                .contains("\"correlationId\":\"" + headerId + "\"");
        assertThat(RECEIVED_CORRELATION_ID.get()).as("request must not reach downstream").isNull();
    }

    @Test
    void expiredToken_is401TokenExpired() {
        client.get().uri("/ccar/forecast/ok")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(signingKey(), Instant.now().minusSeconds(60)))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueMatches(HttpHeaders.WWW_AUTHENTICATE, ".*error=\"invalid_token\".*")
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("TOKEN_EXPIRED");
    }

    @Test
    void tokenSignedWithOtherKey_is401TokenInvalid() {
        SecretKey otherKey = Jwts.SIG.HS512.key().build();
        client.get().uri("/ccar/forecast/ok")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(otherKey, Instant.now().plusSeconds(600)))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("TOKEN_INVALID");
    }

    @Test
    void garbageToken_is401TokenInvalid() {
        client.get().uri("/ccar/forecast/ok")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("TOKEN_INVALID");
    }

    // ------------------------------------------------------------------------------- correlation id

    @Test
    void validToken_forwardsCallersCorrelationIdDownstreamAndBack() {
        client.get().uri("/ccar/forecast/ok")
                .header(HttpHeaders.AUTHORIZATION, validBearer())
                .header("X-Correlation-Id", "ui-abc-123")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valuesMatch("X-Correlation-Id", "ui-abc-123"); // exactly one value

        assertThat(RECEIVED_CORRELATION_ID.get()).isEqualTo("ui-abc-123");
    }

    @Test
    void validToken_generatesCorrelationIdWhenMissingOrUnsafe() {
        EntityExchangeResult<byte[]> result = client.get().uri("/ccar/forecast/ok")
                .header(HttpHeaders.AUTHORIZATION, validBearer())
                .header("X-Correlation-Id", "evil id with spaces")
                .exchange()
                .expectStatus().isOk()
                .expectBody().returnResult();

        String echoed = result.getResponseHeaders().getFirst("X-Correlation-Id");
        assertThat(echoed).isNotBlank().doesNotContain("evil");
        assertThat(RECEIVED_CORRELATION_ID.get()).isEqualTo(echoed);
    }

    // --------------------------------------------------------------------------- downstream failures

    @Test
    void downstreamTimeout_is504DownstreamTimeout() {
        long start = System.nanoTime();

        client.get().uri("/ccar/forecast/slow")
                .header(HttpHeaders.AUTHORIZATION, validBearer())
                .exchange()
                .expectStatus().isEqualTo(504)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("DOWNSTREAM_TIMEOUT")
                .jsonPath("$.instance").isEqualTo("/ccar/forecast/slow")
                .jsonPath("$.correlationId").isNotEmpty();

        // gave up after the 1 s route timeout, not after the 5 s the service takes
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(4));
    }

    @Test
    void downstreamDown_is503DownstreamUnavailable() {
        client.post().uri("/users/login") // public endpoint, routed to the dead port
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"a@b.c\",\"password\":\"x\"}")
                .header("X-Correlation-Id", "login-cid-1")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().valueEquals(HttpHeaders.RETRY_AFTER, "30")
                .expectHeader().valueEquals("X-Correlation-Id", "login-cid-1")
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("DOWNSTREAM_UNAVAILABLE")
                .jsonPath("$.instance").isEqualTo("/users/login")
                .jsonPath("$.correlationId").isEqualTo("login-cid-1")
                .jsonPath("$.detail").value(d -> assertThat(d.toString()).doesNotContain("localhost"));
    }

    @Test
    void downstreamErrorResponse_isPassedThroughUnchanged() {
        client.get().uri("/ccar/forecast/problem")
                .header(HttpHeaders.AUTHORIZATION, validBearer())
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("BU_NOT_FOUND")
                .jsonPath("$.service").isEqualTo("ccar");
    }

    @Test
    void unknownRoute_is404RouteNotFound() {
        client.get().uri("/no/such/route")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().exists("X-Correlation-Id")
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("ROUTE_NOT_FOUND")
                .jsonPath("$.service").isEqualTo("ccar-api-gateway");
    }

    // ------------------------------------------------------------------------------------- helpers

    private String validBearer() {
        return "Bearer " + token(signingKey(), Instant.now().plusSeconds(600));
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    private static String token(SecretKey key, Instant expiresAt) {
        return Jwts.builder()
                .subject("test@example.com")
                .issuedAt(Date.from(expiresAt.minusSeconds(3600)))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    private static HttpServer startDownstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/ccar/forecast/ok", ex -> {
                RECEIVED_CORRELATION_ID.set(ex.getRequestHeaders().getFirst("X-Correlation-Id"));
                ex.getResponseHeaders().set("X-Correlation-Id",
                        ex.getRequestHeaders().getFirst("X-Correlation-Id"));
                respond(ex, 200, "application/json", "{\"ok\":true}");
            });
            server.createContext("/ccar/forecast/slow", ex -> {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                respond(ex, 200, "application/json", "{\"ok\":true}");
            });
            server.createContext("/ccar/forecast/problem", ex -> respond(ex, 404, "application/problem+json",
                    "{\"type\":\"urn:ccar:error:bu-not-found\",\"status\":404,\"errorCode\":\"BU_NOT_FOUND\","
                            + "\"service\":\"ccar\"}"));
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void respond(HttpExchange ex, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        } catch (IOException ignored) {
            // the gateway may already have given up on a slow response
        }
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
