package com.trading.users.dto;

import com.trading.users.User;
import jakarta.validation.constraints.*;

public record CreateUserRequest(
    @NotBlank String name,
    @Email @NotBlank String email,
    @NotBlank @Size(min = 8) String password,
    User.UserRole role
) {}
