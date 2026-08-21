package com.trading.users.dto;

import com.trading.users.UserRole;
import jakarta.validation.constraints.*;

public record CreateUserRequest(
    @NotBlank String name,
    @Email @NotBlank String email,
    @NotBlank @Size(min = 8) String password,
    UserRole role
) {}
