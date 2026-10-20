package com.example.loandocs.controller;

import com.example.loandocs.dto.ApiResponse;
import com.example.loandocs.dto.UserDto;
import com.example.loandocs.entity.Role;
import com.example.loandocs.entity.User;
import com.example.loandocs.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthApiController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> registerCustomer(@Valid @RequestBody UserDto request, HttpServletRequest httpRequest) {
        userService.registerUser(request, Role.RoleType.ROLE_CUSTOMER, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Registration successful! You may now login."));
    }
}
