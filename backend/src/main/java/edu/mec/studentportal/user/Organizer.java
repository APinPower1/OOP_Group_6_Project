package edu.mec.studentportal.user;

public final class Organizer extends User {
    private final OrganizerProfile profile;

    public Organizer(
        long id,
        String fullName,
        String email,
        String passwordHash,
        String department,
        boolean enabled,
        OrganizerProfile profile
    ) {
        super(id, fullName, email, passwordHash, department, enabled);
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
        this.profile = profile;
    }

    public OrganizerProfile getProfile() {
        return profile;
    }

    @Override
    public Role getRole() {
        return Role.ORGANIZER;
    }
}
