package com.trading.users;

import com.trading.common.EncryptionUtil;
import com.trading.users.dto.CreateUserRequest;
import com.trading.users.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock UserConfigRepository userConfigRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock EncryptionUtil encryptionUtil;
    @InjectMocks UserService userService;

    @Test
    void createUser_savesUserAndDefaultConfig() {
        when(userRepository.existsByEmail("alice@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        User saved = User.builder().id(1L).name("Alice").email("alice@test.com")
                .passwordHash("hashed").role(UserRole.USER).active(true).build();
        when(userRepository.save(any())).thenReturn(saved);

        UserResponse result = userService.createUser(
                new CreateUserRequest("Alice", "alice@test.com", "password123", null));

        assertThat(result.email()).isEqualTo("alice@test.com");
        assertThat(result.role()).isEqualTo("USER");
        verify(userConfigRepository).save(any(UserConfig.class));
    }

    @Test
    void createUser_throwsWhenEmailExists() {
        when(userRepository.existsByEmail("alice@test.com")).thenReturn(true);
        assertThatThrownBy(() -> userService.createUser(
                new CreateUserRequest("Alice", "alice@test.com", "pw", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void setUserActive_throwsWhenUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.setUserActive(99L, false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
