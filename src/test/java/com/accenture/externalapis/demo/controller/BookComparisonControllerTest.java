package com.accenture.externalapis.demo.controller;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
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
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Mono;

@WebMvcTest(BookComparisonController.class)
class BookComparisonControllerTest {

    private static final BookDto DUNE = new BookDto("Dune", "Frank Herbert", "Sci-Fi", 9.99);
    private static final BookDto EMMA = new BookDto("Emma", "Jane Austen", "Romance", 7.5);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookRestClient bookRestClient;

    @MockitoBean
    private BookWebClient bookWebClient;

    @Test
    @DisplayName("compare reports both timings and the books; the parallel way is faster")
    void parallelIsFasterThanSequential() throws Exception {
        when(bookRestClient.getBook(1L)).thenAnswer(invocation -> {
            Thread.sleep(200);
            return DUNE;
        });
        when(bookRestClient.getBook(2L)).thenAnswer(invocation -> {
            Thread.sleep(200);
            return EMMA;
        });
        when(bookWebClient.getBooksInParallel(1L, 2L))
                .thenReturn(Mono.delay(Duration.ofMillis(200)).thenReturn(List.of(DUNE, EMMA)));

        var started = mockMvc.perform(get("/api/books/compare").param("id1", "1").param("id2", "2"))
                .andExpect(request().asyncStarted()).andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sequentialMillis").value(greaterThanOrEqualTo(400)))
                .andExpect(jsonPath("$.parallelMillis").value(lessThan(400)))
                .andExpect(jsonPath("$.books.length()").value(2))
                .andExpect(jsonPath("$.books[0].title").value("Dune"));
    }

    @Test
    @DisplayName("compare reports a failing external call as 502 Bad Gateway")
    void failingCallBecomesBadGateway() throws Exception {
        when(bookRestClient.getBook(991L)).thenThrow(new ClientException("Could not fetch book 991: unexpected response"));

        mockMvc.perform(get("/api/books/compare").param("id1", "991").param("id2", "2"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Could not fetch book 991: unexpected response"));
    }
}