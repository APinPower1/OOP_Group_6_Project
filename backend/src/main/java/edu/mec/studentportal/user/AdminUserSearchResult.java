package edu.mec.studentportal.user;

import java.util.List;

record AdminUserSearchResult(List<AdminUserRow> users, long totalItems) {
    AdminUserSearchResult {
        if (users == null) {
            throw new IllegalArgumentException("users must not be null");
        }
        if (totalItems < 0) {
            throw new IllegalArgumentException("totalItems must not be negative");
        }
        users = List.copyOf(users);
    }
}
