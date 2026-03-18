package com.anirudh.storage.minis3.auth.jwt.service;

import com.anirudh.storage.minis3.auth.jwt.properties.JwtProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Anirudh
 * @since 24/02/26
 */

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {
    @Mock
    private JwtProperties properties;

    @InjectMocks
    private JwtService jwtService;


    @Test
    void generateToken() {

        when(properties.getExpiration()).thenReturn(Duration.ofMillis(3600000));
        when(properties.getSecret()).thenReturn("P5acAACnIepdXiBFy9MGbzZTLD2yLexEKM9ZHDgppgI");

        String token = jwtService.generateToken("abc-def-ghi");

        verify(properties).getExpiration();
        verify(properties).getSecret();
        assertNotNull(token);
    }

    @Test
    void validateToken_Fail() {
        when(properties.getSecret()).thenReturn("P5acAACnIepdXiBFy9MGbzZTLD2yLexEKM9ZHDgppgI");
        String subj = jwtService.validateToken("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbWlydWRoc2hhbm11a2hhMjM5OUBnbWFpbC5jb20iLCJpYXQiOjE3NzE4OTg1OTQsImV4cCI6MTc3MTg5OTQ5NH0.YJD6nxvd4jnDpBvWo72k9NntGRzgAe-8qnzyL15rrD8");
        assertNull(subj);
    }

    @Test
    void validateToken_Success() {
        when(properties.getSecret()).thenReturn("P5acAACnIepdXiBFy9MGbzZTLD2yLexEKM9ZHDgppgI");
        String subj = jwtService.validateToken("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI2M2E2YjI3ZS05MTVhLTQ5MDUtOWJhZS03MGJhNWNmYmUxZDIiLCJpYXQiOjE3NzI0NTQ4ODMsImV4cCI6MTc3MjQ1NTc4M30.SIa7DryA8x2uDcRcccrNbygJ_cbRMvH2KT6vQQFwqFo");
        assertNotNull(subj);
    }


}