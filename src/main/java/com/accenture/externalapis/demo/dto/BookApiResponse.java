package com.accenture.externalapis.demo.dto;

// Raw book as returned by the external service (GET /api/books and GET /api/books/{id}).
public record BookApiResponse(Long id,
                              String title,
                              String author,
                              String genre,
                              double price,
                              String isbn,
                              int publishedYear) {
}
