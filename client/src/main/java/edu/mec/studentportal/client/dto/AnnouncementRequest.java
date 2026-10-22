package edu.mec.studentportal.client.dto;

/** Publish body; audienceRole is null (all enabled users) or a single role name. */
public record AnnouncementRequest(String title, String content, String audienceRole) {}
