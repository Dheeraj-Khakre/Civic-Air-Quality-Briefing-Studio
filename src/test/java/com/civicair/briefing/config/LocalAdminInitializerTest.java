package com.civicair.briefing.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.civicair.briefing.user.UserEntity;
import com.civicair.briefing.user.UserRepository;

class LocalAdminInitializerTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final ApplicationArguments arguments = mock(ApplicationArguments.class);

    @Test
    void createsAdminWithHashedPasswordAndNormalizedEmail() throws Exception {
        when(users.findByEmail("admin@civic-air.local")).thenReturn(Optional.empty());
        when(encoder.encode("a-long-local-password")).thenReturn("bcrypt-hash");
        when(users.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var initializer = new LocalAdminInitializer(users, encoder, " Admin@Civic-Air.Local ", "a-long-local-password");

        initializer.run(arguments);

        var saved = org.mockito.ArgumentCaptor.forClass(UserEntity.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@civic-air.local");
        assertThat(saved.getValue().getRole()).isEqualTo("ADMIN");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("bcrypt-hash");
    }

    @Test
    void doesNotChangeAnExistingAdminPassword() throws Exception {
        when(users.findByEmail("admin@civic-air.local"))
                .thenReturn(Optional.of(new UserEntity("admin@civic-air.local", "existing-hash", "ADMIN")));
        var initializer = new LocalAdminInitializer(users, encoder, "admin@civic-air.local", "another-local-password");

        initializer.run(arguments);

        verify(users, never()).save(any(UserEntity.class));
        verify(encoder, never()).encode(any());
    }

    @Test
    void refusesToPromoteAnExistingNonAdminAccount() {
        when(users.findByEmail("admin@civic-air.local"))
                .thenReturn(Optional.of(new UserEntity("admin@civic-air.local", "hash", "SCHOOL")));
        var initializer = new LocalAdminInitializer(users, encoder, "admin@civic-air.local", "another-local-password");

        assertThatThrownBy(() -> initializer.run(arguments))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-admin account");
        verify(users, never()).save(any(UserEntity.class));
    }
}
