package edu.mec.studentportal.user;

public sealed interface UserProfile
    permits StudentProfile, OrganizerProfile, FacultyProfile, AdminProfile {
}
