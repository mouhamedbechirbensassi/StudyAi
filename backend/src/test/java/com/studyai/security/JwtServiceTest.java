package com.studyai.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private final String testSecret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", testSecret);
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", 3600000L); // 1 hour
    }

    @Test
    void shouldGenerateAndValidateTokenSuccessfully() {
        UserDetails userDetails = new User("student@studyai.com", "password", Collections.emptyList());

        String token = jwtService.generateToken(userDetails);
        assertNotNull(token);
        assertFalse(token.isEmpty());

        String username = jwtService.extractUsername(token);
        assertEquals("student@studyai.com", username);

        assertTrue(jwtService.isTokenValid(token, userDetails));
    }

    @Test
    void shouldRejectTokenWithDifferentUser() {
        UserDetails user1 = new User("user1@studyai.com", "password", Collections.emptyList());
        UserDetails user2 = new User("user2@studyai.com", "password", Collections.emptyList());

        String token = jwtService.generateToken(user1);
        assertFalse(jwtService.isTokenValid(token, user2));
    }

    @Test
    void shouldRejectMalformedToken() {
        UserDetails userDetails = new User("user@studyai.com", "password", Collections.emptyList());
        assertFalse(jwtService.isTokenValid("malformed.jwt.token", userDetails));
    }
}
