package edu.mec.studentportal.user;

public final class AdminProfile {
    private final int adminLevel;

    public AdminProfile() {
        this(1);
    }

    public AdminProfile(int adminLevel) {
        if (adminLevel < 1) {
            throw new IllegalArgumentException("adminLevel must be at least 1");
        }
        this.adminLevel = adminLevel;
    }

    public int getAdminLevel() {
        return adminLevel;
    }
}
