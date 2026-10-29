package edu.itc.shop.order;

import java.util.List;

public record Page<T>(
        List<T> content,
        int page,
        int size,
        long totalElements
) {}
