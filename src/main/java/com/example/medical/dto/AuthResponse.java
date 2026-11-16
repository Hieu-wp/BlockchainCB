package com.example.medical.dto;

public record AuthResponse(String token, String role, String fullName) {
}
