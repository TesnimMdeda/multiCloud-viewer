package com.multicloud.authservice.service.impl;

import com.multicloud.authservice.dto.request.*;
import com.multicloud.authservice.dto.response.*;
import com.multicloud.authservice.entity.*;
import com.multicloud.authservice.exception.*;
import com.multicloud.authservice.mapper.UserMapper;
import com.multicloud.authservice.repository.*;
import com.multicloud.authservice.security.JwtService;
import com.multicloud.authservice.service.*;
import com.multicloud.authservice.util.TokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final UserMapper userMapper;
    private final TokenGenerator tokenGenerator;
    private final com.multicloud.authservice.client.NotificationClient notificationClient;

    @Value("${jwt.expiration}") private long jwtExpiration;
    @Value("${jwt.reset-token-expiration}") private long resetTokenExpiration;
    @Value("${app.frontend-url}") private String frontendUrl;

    @Override
    public UserResponse registerUser(RegisterUserRequest request, User creator) {
        // Validate creator can assign requested role
        if (!creator.getRole().canCreate(request.getRole())) {
            throw new UnauthorizedActionException(
                    "You cannot create a user with role: " + request.getRole());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException(
                    "User with email " + request.getEmail() + " already exists");
        }

        User newUser = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .company(request.getCompany())
                .avatar(request.getAvatar())
                .role(request.getRole())
                .enabled(false)          // disabled until password is set
                .accountNonLocked(true)
                .createdBy(creator.getId())
                .build();

        userRepository.save(newUser);

        // Generate secure setup token & send email
        String rawToken = tokenGenerator.generateSecureToken();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(passwordEncoder.encode(rawToken))
                .user(newUser)
                .expiresAt(LocalDateTime.now().plusSeconds(resetTokenExpiration / 1000))
                .build();
        tokenRepository.save(resetToken);

        String link = frontendUrl + "/reset-password?token=" + rawToken;
        emailService.sendPasswordSetupEmail(newUser, link);

        log.info("User {} created by {} ({})", newUser.getEmail(),
                creator.getEmail(), creator.getRole());
        return userMapper.toResponse(newUser);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));
        String token = jwtService.generateToken(user);
        return AuthResponse.builder()
                .accessToken(token)
                .expiresIn(jwtExpiration)
                .user(userMapper.toResponse(user))
                .build();
    }

    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            tokenRepository.deleteByUser(user);
            String rawToken = tokenGenerator.generateSecureToken();
            PasswordResetToken token = PasswordResetToken.builder()
                    .token(passwordEncoder.encode(rawToken))
                    .user(user)
                    .expiresAt(LocalDateTime.now().plusSeconds(resetTokenExpiration / 1000))
                    .build();
            tokenRepository.save(token);
            String link = frontendUrl + "/reset-password?token=" + rawToken;
            emailService.sendPasswordResetEmail(user, link);
        });
        // Always return success to prevent email enumeration
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new InvalidTokenException("Passwords do not match");
        }

        PasswordResetToken matchedToken = tokenRepository.findAll().stream()
                .filter(t -> passwordEncoder.matches(request.getToken(), t.getToken()))
                .findFirst()
                .orElseThrow(() -> new InvalidTokenException("Invalid token"));

        if (matchedToken.isUsed()) {
            throw new InvalidTokenException("Link already used");
        }
        if (matchedToken.isExpired()) {
            throw new InvalidTokenException("Link expired");
        }

        User user = matchedToken.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setEnabled(true);
        userRepository.save(user);

        matchedToken.setUsed(true);
        tokenRepository.save(matchedToken);

        log.info("Password successfully reset for user: {}", user.getEmail());

        // Notify the creator that the user is now active
        if (user.getCreatedBy() != null) {
            userRepository.findById(user.getCreatedBy()).ifPresent(creator -\u003e {
                try {
                    notificationClient.sendNotification(com.multicloud.authservice.client.NotificationClient.NotificationRequest.builder()
                            .userEmail(creator.getEmail())
                            .title("User Account Active")
                            .message("User " + user.getFirstName() + " " + user.getLastName() + " (" + user.getEmail() + ") has set their password and is now active.")
                            .type("SUCCESS")
                            .build());
                } catch (Exception e) {
                    log.error("Failed to send activation notification to creator {}", creator.getEmail(), e);
                }
            });
        }
    }

    @Override
    public void validateToken(String token) {
        PasswordResetToken matchedToken = tokenRepository.findAll().stream()
                .filter(t -> passwordEncoder.matches(token, t.getToken()))
                .findFirst()
                .orElseThrow(() -> new InvalidTokenException("Invalid token"));

        if (matchedToken.isUsed()) {
            throw new InvalidTokenException("Link already used");
        }
        if (matchedToken.isExpired()) {
            throw new InvalidTokenException("Link expired");
        }
    }

    @Override
    public void logout(String token) {
        // For stateless JWT: implement token blacklist via Redis in production
        log.info("User logged out (token invalidated client-side)");
    }
}