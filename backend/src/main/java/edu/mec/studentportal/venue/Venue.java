package edu.mec.studentportal.venue;

/**
 * A managed campus venue. Events reference venues by name (plain string), so
 * removing a venue never invalidates existing events.
 */
public record Venue(long id, String name) {
    public Venue {
        if (id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }
}
