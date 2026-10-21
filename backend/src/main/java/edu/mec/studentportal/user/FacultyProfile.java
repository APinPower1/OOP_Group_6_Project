package edu.mec.studentportal.user;

public final class FacultyProfile implements UserProfile {
    private final String employeeId;
    private final String designation;

    public FacultyProfile(String employeeId, String designation) {
        if (employeeId == null) {
            throw new IllegalArgumentException("employeeId must not be null");
        }
        if (designation == null) {
            throw new IllegalArgumentException("designation must not be null");
        }
        this.employeeId = employeeId;
        this.designation = designation;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public String getDesignation() {
        return designation;
    }
}
