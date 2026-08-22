package com.trading.users;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserConfigRepository extends JpaRepository<UserConfig, Long> {
    Optional<UserConfig> findByUser(User user);
    Optional<UserConfig> findByUser_Email(String email);
    List<UserConfig> findByZerodhaConnectedTrue();
    Optional<UserConfig> findByUser_Id(Long userId);
}
