package edu.mec.studentportal.user;

public final class StudentProfile implements UserProfile {
    private final String rollNumber;
    private final int semester;
    private final String batch;

    public StudentProfile(String rollNumber, int semester, String batch) {
        if (rollNumber == null) {
            throw new IllegalArgumentException("rollNumber must not be null");
        }
        if (semester < 1) {
            throw new IllegalArgumentException("semester must be at least 1");
        }
        if (batch == null) {
            throw new IllegalArgumentException("batch must not be null");
        }
        this.rollNumber = rollNumber;
        this.semester = semester;
        this.batch = batch;
    }

    public String getRollNumber() {
        return rollNumber;
    }

    public int getSemester() {
        return semester;
    }

    public String getBatch() {
        return batch;
    }
}
