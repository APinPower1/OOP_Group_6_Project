package edu.mec.studentportal.user;

public final class Admin extends User {
    private final AdminProfile profile;

    public Admin(
        long id,
        String fullName,
        String email,
        String passwordHash,
        String department,
        boolean enabled,
        AdminProfile profile
    ) {
        super(id, fullName, email, passwordHash, department, enabled);
        if (profile == null) {
            throw new IllegalArgumentException("profile must not be null");
        }
        this.profile = profile;
    }

    public AdminProfile getProfile() {
        return profile;
    }

    @Override
    public Role getRole() {
        return Role.ADMIN;
    }
}
