package com.civicair.briefing.config;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.civicair.briefing.user.UserEntity;
import com.civicair.briefing.user.UserRepository;

@Component
@ConditionalOnProperty(name = "civic.local-admin.enabled", havingValue = "true")
public class LocalAdminInitializer implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalAdminInitializer.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public LocalAdminInitializer(UserRepository users, PasswordEncoder passwordEncoder,
            @Value("${civic.local-admin.email:}") String email,
            @Value("${civic.local-admin.password:}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (email.isBlank() || password.getBytes(StandardCharsets.UTF_8).length < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException(
                    "Local admin bootstrap requires an email and a password between 12 and 72 UTF-8 bytes.");
        }

        var existing = users.findByEmail(email);
        if (existing.isPresent()) {
            if (!existing.get().getRole().equals("ADMIN")) {
                throw new IllegalStateException("Local admin email already belongs to a non-admin account.");
            }
            return;
        }

        users.save(new UserEntity(email, passwordEncoder.encode(password), "ADMIN"));
        LOGGER.info("Initialized local demo administrator account: {}", email);
    }
}
