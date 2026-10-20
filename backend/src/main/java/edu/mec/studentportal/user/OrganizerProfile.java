package edu.mec.studentportal.user;

public final class OrganizerProfile {
    private final String designation;

    public OrganizerProfile(String designation) {
        if (designation == null) {
            throw new IllegalArgumentException("designation must not be null");
        }
        this.designation = designation;
    }

    public String getDesignation() {
        return designation;
    }
}
