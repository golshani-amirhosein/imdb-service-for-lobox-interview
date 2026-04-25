package com.lobox.interview.repository.dto;

import org.jspecify.annotations.NonNull;

public record Person(int id, String fullName, short birthYear, short deathYear) {
    @Override
    public @NonNull String toString() {
        return "%s (Birth: %s | Death: %s)".formatted(
                fullName,
                birthYear == 0 ? "?" : birthYear,
                deathYear == 0 ? "-" : deathYear
        );
    }
}
