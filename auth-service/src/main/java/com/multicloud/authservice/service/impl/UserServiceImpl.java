package com.multicloud.authservice.service.impl;

import com.multicloud.authservice.dto.request.UpdateUserRequest;
import com.multicloud.authservice.dto.response.UserResponse;
import com.multicloud.authservice.entity.Role;
import com.multicloud.authservice.entity.User;
import com.multicloud.authservice.exception.UnauthorizedActionException;
import com.multicloud.authservice.mapper.UserMapper;
import com.multicloud.authservice.repository.PasswordResetTokenRepository;
import com.multicloud.authservice.repository.UserRepository;
import com.multicloud.authservice.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final UserMapper userMapper;

    // ─────────────────────────────────────────────────────────
    // READ OPERATIONS
    // ─────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        log.debug("Fetching all users");
        return userRepository.findAll()
                .stream()
                .map(userMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(String userId) {
        log.debug("Fetching user by id: {}", userId);
        User user = findUserOrThrow(userId);
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {
        log.debug("Fetching user by email: {}", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException(
                        "User not found with email: " + email));
        return userMapper.toResponse(user);
    }

    // ─────────────────────────────────────────────────────────
    // UPDATE OPERATIONS
    // ─────────────────────────────────────────────────────────

    @Override
    public UserResponse updateUser(String userId,
                                   UpdateUserRequest request,
                                   User currentUser) {
        User target = findUserOrThrow(userId);

        // A CLIENT can only update their own profile
        if (currentUser.getRole() == Role.CLIENT
                && !currentUser.getId().equals(userId)) {
            throw new UnauthorizedActionException(
                    "Clients can only update their own profile");
        }

        // ADMIN cannot update a SUPER_ADMIN profile
        if (currentUser.getRole() == Role.ADMIN
                && target.getRole() == Role.SUPER_ADMIN) {
            throw new UnauthorizedActionException(
                    "ADMIN cannot modify a SUPER_ADMIN account");
        }

        target.setFirstName(request.getFirstName());
        target.setLastName(request.getLastName());
        target.setCompany(request.getCompany());
        target.setAvatar(request.getAvatar());

        User saved = userRepository.save(target);
        log.info("User {} updated by {}", target.getEmail(), currentUser.getEmail());
        return userMapper.toResponse(saved);
    }

    @Override
    public void setAccountLocked(String userId, boolean locked, User currentUser) {
        User target = findUserOrThrow(userId);

        // Only SUPER_ADMIN can lock/unlock ADMIN accounts
        if (target.getRole() == Role.ADMIN
                && currentUser.getRole() != Role.SUPER_ADMIN) {
            throw new UnauthorizedActionException(
                    "Only SUPER_ADMIN can lock/unlock ADMIN accounts");
        }

        target.setAccountNonLocked(!locked);
        userRepository.save(target);
        log.info("User {} account locked={} by {}",
                target.getEmail(), locked, currentUser.getEmail());
    }

    @Override
    public UserResponse changeUserRole(String userId,
                                       String newRoleStr,
                                       User currentUser) {
        // Only SUPER_ADMIN can change roles
        if (currentUser.getRole() != Role.SUPER_ADMIN) {
            throw new UnauthorizedActionException(
                    "Only SUPER_ADMIN can change user roles");
        }

        Role newRole;
        try {
            newRole = Role.valueOf(newRoleStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid role: " + newRoleStr);
        }

        User target = findUserOrThrow(userId);
        target.setRole(newRole);
        User saved = userRepository.save(target);

        log.info("User {} role changed to {} by SUPER_ADMIN {}",
                target.getEmail(), newRole, currentUser.getEmail());
        return userMapper.toResponse(saved);
    }

    // ─────────────────────────────────────────────────────────
    // DELETE OPERATIONS
    // ─────────────────────────────────────────────────────────

    @Override
    public void deleteUser(String userId, User currentUser) {
        User target = findUserOrThrow(userId);

        // Cannot delete yourself
        if (currentUser.getId().equals(userId)) {
            throw new UnauthorizedActionException("Cannot delete your own account");
        }

        // ADMIN cannot delete another ADMIN or SUPER_ADMIN
        if (currentUser.getRole() == Role.ADMIN
                && target.getRole() != Role.CLIENT) {
            throw new UnauthorizedActionException(
                    "ADMIN can only delete CLIENT accounts");
        }

        // Delete associated reset tokens first (cascade or manual)
        tokenRepository.deleteByUser(target);
        userRepository.delete(target);

        log.info("User {} deleted by {}", target.getEmail(), currentUser.getEmail());
    }

    // ─────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────

    private User findUserOrThrow(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "User not found with id: " + userId));
    }
}