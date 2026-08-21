package com.trading.users;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserConfigRepository extends JpaRepository<UserConfig, Long> {
    Optional<UserConfig> findByUser(User user);
    Optional<UserConfig> findByUser_Email(String email);
}
