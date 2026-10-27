package com.accenture.externalapis.demo.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.accenture.externalapis.demo.config.ExternalServiceProperties;
import com.accenture.externalapis.demo.dto.BookDto;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class BookWebClientImplTest {

    private static final String BASE_URL = "http://external.test/api";
    private static final BookDto DUNE = new BookDto("Dune", "Frank Herbert", "Sci-Fi", 9.99);

    private static String bookJson(long id, String title) {
        return """
                {"id":%d,"title":"%s","author":"Frank Herbert","genre":"Sci-Fi","price":9.99,
                 "isbn":"111","publishedYear":1965}""".formatted(id, title);
    }

    private static Mono<ClientResponse> json(HttpStatus status, String body) {
        return Mono.just(ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }

    // Builds the client on top of a fake "external service": every request is answered by the given function.
    private BookWebClientImpl clientAnswering(Function<String, Mono<ClientResponse>> service) {
        WebClient.Builder builder = WebClient.builder()
                .exchangeFunction(request -> service.apply(request.url().getPath()));
        return new BookWebClientImpl(builder, new ExternalServiceProperties(BASE_URL));
    }

    @Test
    @DisplayName("getBookAsync maps the external response to a BookDto")
    void getBookAsyncMapsResponse() {
        BookWebClientImpl client = clientAnswering(path -> json(HttpStatus.OK, bookJson(1, "Dune")));

        StepVerifier.create(client.getBookAsync(1L)).expectNext(DUNE).verifyComplete();
    }

    @Test
    @DisplayName("getBookAsync calls GET /books/{id} on the configured base URL")
    void getBookAsyncUsesConfiguredUrl() {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            assertThat(request.method()).isEqualTo(HttpMethod.GET);
            assertThat(request.url()).isEqualTo(URI.create(BASE_URL + "/books/7"));
            return json(HttpStatus.OK, bookJson(7, "Dune"));
        });
        BookWebClientImpl client = new BookWebClientImpl(builder, new ExternalServiceProperties(BASE_URL));

        StepVerifier.create(client.getBookAsync(7L)).expectNext(DUNE).verifyComplete();
    }

    @Test
    @DisplayName("getAllBooksAsync emits every book")
    void getAllBooksAsyncEmitsAll() {
        BookWebClientImpl client = clientAnswering(path ->
                json(HttpStatus.OK, "[" + bookJson(1, "Dune") + "," + bookJson(2, "Emma") + "]"));

        StepVerifier.create(client.getAllBooksAsync())
                .expectNext(DUNE)
                .expectNext(new BookDto("Emma", "Frank Herbert", "Sci-Fi", 9.99))
                .verifyComplete();
    }

    @Test
    @DisplayName("a 4xx answer becomes a ClientException and is not retried")
    void clientErrorIsNotRetried() {
        AtomicInteger calls = new AtomicInteger();
        BookWebClientImpl client = clientAnswering(path -> {
            calls.incrementAndGet();
            return json(HttpStatus.I_AM_A_TEAPOT, "{}");
        });

        StepVerifier.create(client.getBookAsync(99L))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ClientException.class)
                        .hasMessageContaining("rejected the request")
                        .hasMessageContaining("418"))
                .verify();
        assertThat(calls).hasValue(1);
    }

    @Test
    @DisplayName("a 5xx answer is retried, then becomes a ClientException")
    void serverErrorIsRetriedThenFails() {
        AtomicInteger calls = new AtomicInteger();
        BookWebClientImpl client = clientAnswering(path -> {
            calls.incrementAndGet();
            return json(HttpStatus.SERVICE_UNAVAILABLE, "{}");
        });

        StepVerifier.create(client.getBookAsync(993L))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ClientException.class)
                        .hasMessageContaining("external service failed")
                        .hasMessageContaining("503"))
                .verify(Duration.ofSeconds(5));
        assertThat(calls).hasValue(3); // first call + 2 retries
    }

    @Test
    @DisplayName("a flaky book succeeds after the service recovers (retry bonus)")
    void flakyServiceRecoversAfterRetries() {
        AtomicInteger calls = new AtomicInteger();
        BookWebClientImpl client = clientAnswering(path -> calls.incrementAndGet() < 3
                ? json(HttpStatus.SERVICE_UNAVAILABLE, "{}")
                : json(HttpStatus.OK, bookJson(996, "Dune")));

        StepVerifier.create(client.getBookAsync(996L)).expectNext(DUNE).verifyComplete();
        assertThat(calls).hasValue(3);
    }

    @Test
    @DisplayName("a connection problem becomes a ClientException")
    void connectionProblemBecomesClientException() {
        BookWebClientImpl client = clientAnswering(path -> Mono.error(new WebClientRequestException(
                new IOException("Connection refused"), HttpMethod.GET,
                URI.create(BASE_URL + "/books/1"), new HttpHeaders())));

        StepVerifier.create(client.getBookAsync(1L))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ClientException.class)
                        .hasMessageContaining("unreachable or timed out"))
                .verify();
    }

    @Test
    @DisplayName("an empty body becomes a ClientException")
    void emptyBodyBecomesClientException() {
        BookWebClientImpl client = clientAnswering(path ->
                Mono.just(ClientResponse.create(HttpStatus.OK).build()));

        StepVerifier.create(client.getBookAsync(992L))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ClientException.class)
                        .hasMessageContaining("empty response"))
                .verify();
    }

    @Test
    @DisplayName("a malformed body becomes a ClientException")
    void malformedBodyBecomesClientException() {
        BookWebClientImpl client = clientAnswering(path -> json(HttpStatus.OK, "{\"id\": 5, \"title\": "));

        StepVerifier.create(client.getBookAsync(995L))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ClientException.class)
                        .hasMessageContaining("unexpected response"))
                .verify();
    }

    @Test
    @DisplayName("getBooksInParallel returns both books")
    void parallelReturnsBothBooks() {
        BookWebClientImpl client = clientAnswering(path -> json(HttpStatus.OK,
                bookJson(path.endsWith("1") ? 1 : 2, path.endsWith("1") ? "Dune" : "Emma")));

        StepVerifier.create(client.getBooksInParallel(1L, 2L))
                .expectNext(List.of(DUNE, new BookDto("Emma", "Frank Herbert", "Sci-Fi", 9.99)))
                .verifyComplete();
    }

    @Test
    @DisplayName("getBooksInParallel fails with a ClientException when one of the calls fails")
    void parallelFailsWhenOneCallFails() {
        BookWebClientImpl client = clientAnswering(path -> path.endsWith("991")
                ? json(HttpStatus.NOT_FOUND, "{}")
                : json(HttpStatus.OK, bookJson(1, "Dune")));

        StepVerifier.create(client.getBooksInParallel(1L, 991L))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ClientException.class)
                        .hasMessageContaining("book 991"))
                .verify();
    }

    @Test
    @DisplayName("getBooksInParallel runs both calls at the same time, not one after the other")
    void parallelCallsOverlapInTime() {
        BookWebClientImpl client = clientAnswering(path ->
                Mono.delay(Duration.ofMillis(300)).then(json(HttpStatus.OK, bookJson(1, "Dune"))));

        Duration elapsed = StepVerifier.create(client.getBooksInParallel(1L, 2L))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(elapsed).isLessThan(Duration.ofMillis(550)); // two sequential calls would take 600+ ms
    }
}