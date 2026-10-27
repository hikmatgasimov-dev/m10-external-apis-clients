package com.accenture.externalapis.demo.client;

import com.accenture.externalapis.demo.config.ExternalServiceProperties;
import com.accenture.externalapis.demo.dto.BookApiResponse;
import com.accenture.externalapis.demo.dto.BookDto;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;


@Component
public class BookWebClientImpl implements BookWebClient {

    private static final Logger log = LoggerFactory.getLogger(BookWebClientImpl.class);
    private static final String BOOKS_PATH = "/books";
    private static final int MAX_RETRIES = 2;
    private WebClient webClient;

    public BookWebClientImpl(WebClient.Builder builder, ExternalServiceProperties properties) {
        this.webClient = builder.baseUrl(properties.baseUrl()).build();
    }

    // Fetches one book as a Mono; an empty body, errors and retries are handled here.
    @Override
    public Mono<BookDto> getBookAsync(Long id) {
        return webClient.get()
                .uri(BOOKS_PATH + "/{id}" + id)
                .retrieve()
                .bodyToMono(BookApiResponse.class)
                .switchIfEmpty(Mono.error(new ClientException(
                        "External service returned an empty response for book " + id))).
                retryWhen(retryTransientErrors())
                .map(this::toDto);
    }
    // True for 5xx answers, which can succeed on a second try (4xx and timeouts are not retried).
    private boolean isTransient(Throwable error) {
        return error instanceof WebClientResponseException response
                && response.getStatusCode().is5xxServerError();
    }
    // Turns any WebClient failure into a ClientException with a readable message.
    private Throwable toClientException(String action, Throwable error) {
        if (error instanceof ClientException) {
            return error;
        }
        if (error instanceof WebClientResponseException response && response.getStatusCode().isError()) {
            log.error("External service answered {} while trying to {}", response.getStatusCode(), action);
            String reason = response.getStatusCode().is4xxClientError() ? "rejected the request" : "failed";
            return new ClientException("Could not " + action + ": external service " + reason
                    + " (" + response.getStatusCode() + ")", error);
        }
        if (error instanceof WebClientRequestException) {
            log.error("Connection problem while trying to {}: {}", action, error.getMessage());
            return new ClientException("Could not " + action
                    + ": external service is unreachable or timed out", error);
        }
        log.error("Unreadable response while trying to {}: {}", action, error.getMessage());
        return new ClientException("Could not " + action + ": unexpected response from external service", error);
    }
    // Retry policy: up to 2 retries with a short backoff, only for transient errors.
    private Retry retryTransientErrors(){
        return Retry.backoff(MAX_RETRIES, Duration.ofMillis(200))
                .filter(this::isTransient)
                .doBeforeRetry(signal -> log.warn("Retrying external call (attempt {}): {}",
                        signal.totalRetries() + 1, signal.failure().getMessage()))
                .onRetryExhaustedThrow((spec, signal) -> signal.failure());
    }

    // Fetches all books as a Flux of BookDto.
    @Override
    public Flux<BookDto> getAllBooksAsync() {
        throw new UnsupportedOperationException("WebClient is not implemented yet");
    }

    // Fetches two books at the same time with Mono.zip(); fails if either call fails.
    @Override
    public Mono<List<BookDto>> getBooksInParallel(Long id1, Long id2) {
        throw new UnsupportedOperationException("WebClient is not implemented yet");
    }
    // Keeps only the fields BookDto needs from the raw external response.
    private BookDto toDto(BookApiResponse book) {
        return new BookDto(book.title(), book.author(), book.genre(), book.price());
    }

}
