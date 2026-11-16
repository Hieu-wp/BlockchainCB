package com.example.medical.dto;

import com.example.medical.entity.Enums.Gender;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

/** Dang ky tai khoan benh nhan. */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(min = 6, max = 100) String password,
        @NotBlank @Size(max = 120) String fullName,
        @NotNull @Past LocalDate dateOfBirth,
        @NotNull Gender gender,
        @Size(max = 5) String bloodType,
        @Size(max = 20) String phone,
        @Size(max = 255) String address) {
}
