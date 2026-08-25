package com.trading.notifications;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
    List<DeviceToken> findByUser_Id(Long userId);
    void deleteByUser_IdAndToken(Long userId, String token);
}
