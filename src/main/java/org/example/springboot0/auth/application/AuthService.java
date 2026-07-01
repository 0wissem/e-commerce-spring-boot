package org.example.springboot0.auth.application;

import org.example.springboot0.auth.application.dto.LoginRequest;
import org.example.springboot0.auth.application.dto.RegisterRequest;
import org.example.springboot0.customer.domain.Customer;
import org.example.springboot0.customer.domain.ICustomerRepository;
import org.example.springboot0.customer.domain.Role;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Registration (create a CONSUMER with a hashed password) and login (verify + issue a token). */
@Service
public class AuthService {

    private final ICustomerRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(ICustomerRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public String register(RegisterRequest request) {
        Customer user = new Customer(null, request.name(), request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(Role.CONSUMER);
        return jwtService.issue(users.save(user));
    }

    public String login(LoginRequest request) {
        Customer user = users.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return jwtService.issue(user);
    }
}
