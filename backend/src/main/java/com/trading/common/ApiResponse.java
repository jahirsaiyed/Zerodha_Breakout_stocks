package com.trading.common;

public record ApiResponse<T>(boolean success, T data, String error) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }
    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(false, null, message);
    }
}
