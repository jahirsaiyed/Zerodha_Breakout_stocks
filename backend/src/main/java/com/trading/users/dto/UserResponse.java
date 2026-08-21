package com.trading.users.dto;

public record UserResponse(Long id, String name, String email, String role, Boolean active) {}
