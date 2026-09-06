package com.example.wallet_system.dto.response;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Serializable pagination envelope. We map Spring Data's {@link Page} into this
 * explicit shape rather than serializing {@code Page} directly (whose JSON
 * structure is unstable across versions and logs a warning).
 */
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.isFirst(),
            page.isLast());
    }
}
