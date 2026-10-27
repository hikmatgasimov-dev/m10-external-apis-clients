package com.accenture.externalapis.demo.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.accenture.externalapis.demo.config.ExternalServiceProperties;
import com.accenture.externalapis.demo.dto.BookDto;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class BookRestClientImplTest {

    private static final String BASE_URL = "http://external.test/api";
    private static final String BOOK_JSON = """
            {"id":1,"title":"Dune","author":"Frank Herbert","genre":"Sci-Fi","price":9.99,
             "isbn":"111","publishedYear":1965}""";

    private MockRestServiceServer server;
    private BookRestClientImpl client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new BookRestClientImpl(builder, new ExternalServiceProperties(BASE_URL));
    }

    @Test
    @DisplayName("getBook maps the external response to a BookDto")
    void getBookMapsResponse() {
        server.expect(requestTo(BASE_URL + "/books/1")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(BOOK_JSON, MediaType.APPLICATION_JSON));

        BookDto book = client.getBook(1L);

        assertThat(book).isEqualTo(new BookDto("Dune", "Frank Herbert", "Sci-Fi", 9.99));
        server.verify();
    }

    @Test
    @DisplayName("getAllBooks maps every book in the list")
    void getAllBooksMapsList() {
        server.expect(requestTo(BASE_URL + "/books"))
                .andRespond(withSuccess("[" + BOOK_JSON + "," + BOOK_JSON + "]", MediaType.APPLICATION_JSON));

        List<BookDto> books = client.getAllBooks();

        assertThat(books).hasSize(2);
        assertThat(books.get(0).title()).isEqualTo("Dune");
    }

    @Test
    @DisplayName("a 4xx answer becomes a ClientException (like the teapot book)")
    void clientErrorBecomesClientException() {
        server.expect(requestTo(BASE_URL + "/books/99")).andRespond(withStatus(HttpStatus.I_AM_A_TEAPOT));

        assertThatThrownBy(() -> client.getBook(99L))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("rejected the request")
                .hasMessageContaining("418");
    }

    @Test
    @DisplayName("a 5xx answer becomes a ClientException")
    void serverErrorBecomesClientException() {
        server.expect(requestTo(BASE_URL + "/books/993")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.getBook(993L))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("external service failed")
                .hasMessageContaining("503");
    }

    @Test
    @DisplayName("a connection problem becomes a ClientException")
    void connectionProblemBecomesClientException() {
        server.expect(requestTo(BASE_URL + "/books/1"))
                .andRespond(withException(new IOException("Connection refused")));

        assertThatThrownBy(() -> client.getBook(1L))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("unreachable or timed out");
    }

    @Test
    @DisplayName("an empty body becomes a ClientException")
    void emptyBodyBecomesClientException() {
        server.expect(requestTo(BASE_URL + "/books/992"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getBook(992L))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("empty response");
    }

    @Test
    @DisplayName("a malformed body becomes a ClientException")
    void malformedBodyBecomesClientException() {
        server.expect(requestTo(BASE_URL + "/books/995"))
                .andRespond(withSuccess("{\"id\": 5, \"title\": ", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getBook(995L))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("unexpected response");
    }

    @Test
    @DisplayName("getAllBooks also wraps server errors in a ClientException")
    void getAllBooksServerError() {
        server.expect(requestTo(BASE_URL + "/books")).andRespond(withServerError());

        assertThatThrownBy(() -> client.getAllBooks())
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("fetch all books");
    }
}