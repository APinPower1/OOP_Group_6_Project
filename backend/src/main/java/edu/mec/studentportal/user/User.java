package edu.mec.studentportal.user;

public abstract class User {
    private final long id;
    private final String fullName;
    private final String email;
    private final String passwordHash;
    private final String department;
    private final boolean enabled;

    protected User(
        long id,
        String fullName,
        String email,
        String passwordHash,
        String department,
        boolean enabled
    ) {
        if (fullName == null) {
            throw new IllegalArgumentException("fullName must not be null");
        }
        if (email == null) {
            throw new IllegalArgumentException("email must not be null");
        }
        if (passwordHash == null) {
            throw new IllegalArgumentException("passwordHash must not be null");
        }
        if (department == null) {
            throw new IllegalArgumentException("department must not be null");
        }
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.department = department;
        this.enabled = enabled;
    }

    public long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String getDepartment() {
        return department;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public abstract Role getRole();
}
