package org.example.springboot0.auth.api;

import jakarta.validation.Valid;
import org.example.springboot0.auth.application.AuthService;
import org.example.springboot0.auth.application.dto.LoginRequest;
import org.example.springboot0.auth.application.dto.RegisterRequest;
import org.example.springboot0.auth.application.dto.TokenResponse;
import org.example.springboot0.shared.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<TokenResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Registered", new TokenResponse(authService.register(request))));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Logged in", new TokenResponse(authService.login(request))));
    }
}
