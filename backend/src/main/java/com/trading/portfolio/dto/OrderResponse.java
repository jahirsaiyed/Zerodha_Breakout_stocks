package com.trading.portfolio.dto;

import com.trading.signals.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderResponse(
        Long id,
        String symbol,
        String type,
        String orderKind,
        int quantity,
        BigDecimal price,
        String status,
        String zerodhaOrderId,
        Long positionId,
        LocalDateTime placedAt,
        LocalDateTime updatedAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getSymbol(),
                order.getType().name(),
                order.getOrderKind().name(),
                order.getQuantity(),
                order.getPrice(),
                order.getStatus().name(),
                order.getZerodhaOrderId(),
                order.getPosition() != null ? order.getPosition().getId() : null,
                order.getPlacedAt(),
                order.getUpdatedAt()
        );
    }
}
