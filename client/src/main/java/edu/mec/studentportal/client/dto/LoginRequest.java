package edu.mec.studentportal.client.dto;

/** Body for POST /auth/login. */
public record LoginRequest(String email, String password) {}
