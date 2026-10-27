package com.accenture.externalapis.demo.dto;

import java.util.List;

// Result of the sequential (blocking) vs parallel (non-blocking) comparison.
public record ComparisonResult(long sequentialMillis,
                               long parallelMillis,
                               List<BookDto> books) {
}