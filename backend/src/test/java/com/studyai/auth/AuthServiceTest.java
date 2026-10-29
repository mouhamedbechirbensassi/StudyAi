package com.studyai.auth;

import com.studyai.auth.dto.AuthResponse;
import com.studyai.auth.dto.LoginRequest;
import com.studyai.auth.dto.RegisterRequest;
import com.studyai.common.exception.EmailAlreadyExistsException;
import com.studyai.security.JwtService;
import com.studyai.user.Role;
import com.studyai.user.User;
import com.studyai.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = new User("Alice Doe", "alice@example.com", "hashedPassword", Role.STUDENT);
        sampleUser.setId(1L);
    }

    @Test
    void register_ShouldHashPasswordAndSaveUser() {
        RegisterRequest request = new RegisterRequest("Alice Doe", "alice@example.com", "Secret123!");

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123!")).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(jwtService.generateToken(any(), any(User.class))).thenReturn("jwt-token-123");

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("jwt-token-123", response.getToken());
        assertEquals("alice@example.com", response.getUser().getEmail());
        assertEquals(Role.STUDENT, response.getUser().getRole());
        verify(passwordEncoder).encode("Secret123!");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_WhenEmailExists_ShouldThrowException() {
        RegisterRequest request = new RegisterRequest("Alice Doe", "alice@example.com", "Secret123!");
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_ShouldAuthenticateAndReturnToken() {
        LoginRequest request = new LoginRequest("alice@example.com", "Secret123!");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(null);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(sampleUser));
        when(jwtService.generateToken(any(), any(User.class))).thenReturn("jwt-token-456");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("jwt-token-456", response.getToken());
        assertEquals("alice@example.com", response.getUser().getEmail());
    }

    @Test
    void login_WithInvalidCredentials_ShouldThrowBadCredentialsException() {
        LoginRequest request = new LoginRequest("alice@example.com", "WrongPassword");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }
}
