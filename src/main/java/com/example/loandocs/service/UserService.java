package com.example.loandocs.service;

import com.example.loandocs.dto.UserDto;
import com.example.loandocs.entity.Role;
import com.example.loandocs.entity.User;
import com.example.loandocs.exception.AppException;
import com.example.loandocs.exception.ResourceNotFoundException;
import com.example.loandocs.repository.RoleRepository;
import com.example.loandocs.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Transactional
    public User registerUser(UserDto request, Role.RoleType roleType, String clientIp) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new AppException("Username is already in use: " + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException("Email is already in use: " + request.getEmail());
        }

        Role role = roleRepository.findByName(roleType)
                .orElseGet(() -> roleRepository.save(Role.builder().name(roleType).build()));

        User user = User.builder()
                .username(request.getUsername().trim())
                .email(request.getEmail().trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .enabled(true)
                .tenantId("DEFAULT_BANK_TENANT")
                .roles(new HashSet<>(Collections.singletonList(role)))
                .build();

        user = userRepository.save(user);

        auditLogService.recordEvent(
                user,
                "USER_REGISTERED",
                "User",
                user.getUsername(),
                clientIp,
                "SUCCESS",
                "{\"role\":\"" + roleType.name() + "\"}"
        );

        return user;
    }

    @Transactional(readOnly = true)
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }
}
