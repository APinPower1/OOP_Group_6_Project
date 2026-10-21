package edu.mec.studentportal.user;

public final class Student extends User {
    private final StudentProfile profile;

    public Student(
        long id,
        String fullName,
        String email,
        String passwordHash,
        String department,
        boolean enabled,
        StudentProfile profile
    ) {
        super(id, fullName, email, passwordHash, department, enabled);
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
        this.profile = profile;
    }

    public StudentProfile getProfile() {
        return profile;
    }

    @Override
    public Role getRole() {
        return Role.STUDENT;
    }
}
