package com.accenture.externalapis.demo.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.accenture.externalapis.demo.client.BookRestClient;
import com.accenture.externalapis.demo.client.BookWebClient;
import com.accenture.externalapis.demo.client.ClientException;
import com.accenture.externalapis.demo.dto.BookDto;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@WebMvcTest(BookController.class)
class BookControllerTest {

    private static final BookDto DUNE = new BookDto("Dune", "Frank Herbert", "Sci-Fi", 9.99);
    private static final BookDto EMMA = new BookDto("Emma", "Jane Austen", "Romance", 7.5);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookRestClient bookRestClient;

    @MockitoBean
    private BookWebClient bookWebClient;

    @Test
    @DisplayName("GET /api/books/{id} returns the book fetched with RestClient")
    void getBook() throws Exception {
        when(bookRestClient.getBook(1L)).thenReturn(DUNE);

        mockMvc.perform(get("/api/books/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Dune"))
                .andExpect(jsonPath("$.price").value(9.99));
    }

    @Test
    @DisplayName("GET /api/books returns all books fetched with RestClient")
    void getAllBooks() throws Exception {
        when(bookRestClient.getAllBooks()).thenReturn(List.of(DUNE, EMMA));

        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].title").value("Emma"));
    }

    @Test
    @DisplayName("a ClientException from the RestClient becomes a 502 Bad Gateway with a readable detail")
    void restClientFailureBecomesBadGateway() throws Exception {
        when(bookRestClient.getBook(991L)).thenThrow(new ClientException("Could not fetch book 991: unexpected response"));

        mockMvc.perform(get("/api/books/991"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("Bad Gateway"))
                .andExpect(jsonPath("$.detail").value("Could not fetch book 991: unexpected response"));
    }

    @Test
    @DisplayName("GET /api/books/async/{id} returns the book fetched with WebClient")
    void getBookAsync() throws Exception {
        when(bookWebClient.getBookAsync(1L)).thenReturn(Mono.just(DUNE));

        mockMvc.perform(asyncDispatch(mockMvc.perform(get("/api/books/async/1"))
                        .andExpect(request().asyncStarted()).andReturn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Dune"));
    }

    @Test
    @DisplayName("GET /api/books/async returns all books fetched with WebClient")
    void getAllBooksAsync() throws Exception {
        when(bookWebClient.getAllBooksAsync()).thenReturn(Flux.just(DUNE, EMMA));

        mockMvc.perform(asyncDispatch(mockMvc.perform(get("/api/books/async"))
                        .andExpect(request().asyncStarted()).andReturn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/books/async/parallel returns both books")
    void getBooksInParallel() throws Exception {
        when(bookWebClient.getBooksInParallel(1L, 2L)).thenReturn(Mono.just(List.of(DUNE, EMMA)));

        mockMvc.perform(asyncDispatch(mockMvc.perform(get("/api/books/async/parallel").param("id1", "1").param("id2", "2"))
                        .andExpect(request().asyncStarted()).andReturn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Dune"))
                .andExpect(jsonPath("$[1].title").value("Emma"));
    }

    @Test
    @DisplayName("a ClientException from the WebClient becomes a 502 Bad Gateway")
    void webClientFailureBecomesBadGateway() throws Exception {
        when(bookWebClient.getBookAsync(993L))
                .thenReturn(Mono.error(new ClientException("Could not fetch book 993: external service failed (503)")));

        mockMvc.perform(asyncDispatch(mockMvc.perform(get("/api/books/async/993"))
                        .andExpect(request().asyncStarted()).andReturn()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Could not fetch book 993: external service failed (503)"));
    }

    @Test
    @DisplayName("an unimplemented client method is reported as 501 Not Implemented")
    void notImplementedBecomes501() throws Exception {
        when(bookRestClient.getBook(5L)).thenThrow(new UnsupportedOperationException("not implemented"));

        mockMvc.perform(get("/api/books/5"))
                .andExpect(status().isNotImplemented());
    }
}