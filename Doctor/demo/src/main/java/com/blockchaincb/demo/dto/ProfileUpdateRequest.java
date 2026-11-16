package com.blockchaincb.demo.dto;

import jakarta.validation.constraints.*;

public record ProfileUpdateRequest(
    @NotBlank @Size(max = 255) String fullName, @NotBlank @Size(max = 255) String hospital) {}
