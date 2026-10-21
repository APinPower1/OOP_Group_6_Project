package edu.mec.studentportal.user;

public final class Faculty extends User {
    private final FacultyProfile profile;

    public Faculty(
        long id,
        String fullName,
        String email,
        String passwordHash,
        String department,
        boolean enabled,
        FacultyProfile profile
    ) {
        super(id, fullName, email, passwordHash, department, enabled);
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
        this.profile = profile;
    }

    public FacultyProfile getProfile() {
        return profile;
    }

    @Override
    public Role getRole() {
        return Role.FACULTY;
    }
}
