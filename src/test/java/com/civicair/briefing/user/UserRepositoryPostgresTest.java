package com.civicair.briefing.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class UserRepositoryPostgresTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private UserRepository users;

    @Test
    void persistsUniqueUserEmailInPostgres() {
        UserEntity user = users.save(new UserEntity("repository@example.test", "hashed", "SCHOOL"));
        assertThat(users.findByEmail("repository@example.test")).contains(user);
        assertThat(users.existsByEmail("repository@example.test")).isTrue();
    }
}
