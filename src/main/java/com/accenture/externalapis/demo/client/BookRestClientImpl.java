package com.accenture.externalapis.demo.client;

import com.accenture.externalapis.demo.config.ExternalServiceProperties;
import com.accenture.externalapis.demo.dto.BookApiResponse;
import com.accenture.externalapis.demo.dto.BookDto;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;


@Component
public class BookRestClientImpl implements BookRestClient{
    private static final Logger log = LoggerFactory.getLogger(BookRestClientImpl.class);
    private static final String BOOKS_PATH = "/books";
    private RestClient restClient;

    //  BookApiResponse responee=call("fetch book "+id,()->restClient.get());
    public BookRestClientImpl(RestClient.Builder builder, ExternalServiceProperties properties) {
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
    }

    @Override
    public BookDto getBook(Long id) {
        BookApiResponse response = call("fetch book " + id, () -> restClient.get()
                .uri(BOOKS_PATH + "/{id}", id)
                .retrieve()
                .body(BookApiResponse.class));
        if (response == null) {
            throw new ClientException("External service returned an empty response for book " + id);
        }
        return toDto(response);

    }
    @Override
    public List<BookDto> getAllBooks(){
        BookApiResponse [] response = call("fetch all books ", () -> restClient.get()
                .uri(BOOKS_PATH)
                .retrieve()
                .body(BookApiResponse[].class));
        if (response == null) {
            throw new ClientException("External service returned an empty response for the book list");
        }
        return Arrays.stream(response).map(this::toDto).toList();
    }

    private <T> T call(String action, Supplier<T> request) {
        try {
            return request.get();
        } catch (HttpClientErrorException e) {
            log.error("Client error while trying to {}: {}", action, e.getStatusCode());
            throw new ClientException("Could not " + action + ": external service rejected the request ("
                    + e.getStatusCode() + ")", e);
        } catch (HttpServerErrorException e) {
            log.error("Server error while trying to {}: {}", action, e.getStatusCode());
            throw new ClientException("Could not " + action + ": external service failed ("
                    + e.getStatusCode() + ")", e);
        } catch (ResourceAccessException e) {
            log.error("Connection problem while trying to {}: {}", action, e.getMessage());
            throw new ClientException("Could not " + action + ": external service is unreachable or timed out", e);
        } catch (RestClientException e) {
            log.error("Unreadable response while trying to {}: {}", action, e.getMessage());
            throw new ClientException("Could not " + action + ": unexpected response from external service", e);
        }
    }

    private BookDto toDto(BookApiResponse book) {
        return new BookDto(book.title(), book.author(), book.genre(), book.price());
    }
}
