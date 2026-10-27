package com.accenture.externalapis.demo.controller;

import com.accenture.externalapis.demo.client.BookRestClient;
import com.accenture.externalapis.demo.client.BookWebClient;
import com.accenture.externalapis.demo.dto.ComparisonResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RestController
@RequestParam("/api/books/compare")
public class BookComparisonController {
 private final BookRestClient bookRestClient;
 private  final BookWebClient bookWebClient;

 public BookComparisonController( BookRestClient bookRestClient,BookWebClient bookWebClient){
     this.bookRestClient = bookRestClient;
     this.bookWebClient = bookWebClient;
 }
    // Fetches the same two books twice: one after the other with RestClient, then at the
    // same time with WebClient + Mono.zip(), and reports how long each way took

    @GetMapping
    public Mono<ComparisonResult> compare(@RequestParam Long id1, @RequestParam Long id2){

        long start=System.nanoTime();
        bookRestClient.getBook(id1);
        bookRestClient.getBook(id2);

        long sequentialMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

        return  bookWebClient.getBooksInParallel(id1,id2).elapsed().map(timed->new ComparisonResult(sequentialMillis,timed.getT1(),timed.getT2()));
    }


}
