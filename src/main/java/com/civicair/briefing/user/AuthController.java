package com.civicair.briefing.user;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import com.civicair.briefing.common.ApiException;
import com.civicair.briefing.common.RequestRateLimiter;
import com.civicair.briefing.audit.AuditService;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private static final long TOKEN_LIFETIME_SECONDS = 3600;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final RequestRateLimiter rateLimiter;
    private final AuditService audit;

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager, JwtEncoder jwtEncoder, RequestRateLimiter rateLimiter,
            AuditService audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
    }

    @PostMapping("/register")
    @Transactional
    public TokenResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        rateLimiter.check(clientKey(httpRequest), "registration", 10, Duration.ofHours(1));
        validateBcryptPassword(request.password());
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "An account with this email already exists.");
        }
        UserEntity user = users.save(new UserEntity(email, passwordEncoder.encode(request.password()), "SCHOOL"));
        audit.record(user.getId(), "USER_REGISTERED", user.getId());
        return tokenFor(user);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        rateLimiter.check(clientKey(httpRequest), "login", 10, Duration.ofMinutes(15));
        validateBcryptPassword(request.password());
        String email = request.email().trim().toLowerCase();
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
        UserEntity user = users.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password."));
        audit.record(user.getId(), "USER_AUTHENTICATED", user.getId());
        return tokenFor(user);
    }

    private TokenResponse tokenFor(UserEntity user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("civic-air-quality-briefing-studio")
                .issuedAt(now)
                .expiresAt(now.plus(TOKEN_LIFETIME_SECONDS, ChronoUnit.SECONDS))
                .subject(user.getEmail())
                .claim("userId", user.getId().toString())
                .claim("roles", List.of("ROLE_" + user.getRole()))
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", TOKEN_LIFETIME_SECONDS,
                new UserSummary(user.getId(), user.getEmail(), user.getRole()));
    }

    private UUID clientKey(HttpServletRequest request) {
        return UUID.nameUUIDFromBytes(request.getRemoteAddr().getBytes(StandardCharsets.UTF_8));
    }

    private void validateBcryptPassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_TOO_LONG",
                    "Password must not exceed 72 UTF-8 bytes.");
        }
    }

    public record RegisterRequest(@NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 72) String password) {}
    public record LoginRequest(@NotBlank @Email String email, @NotBlank @Size(max = 72) String password) {}
    public record TokenResponse(String accessToken, String tokenType, long expiresIn, UserSummary user) {}
    public record UserSummary(UUID id, String email, String role) {}
}
