package com.anirudh.storage.minis3.auth.jwt.filters;

import com.anirudh.storage.minis3.auth.dto.AuthenticatedUser;
import com.anirudh.storage.minis3.auth.jwt.service.JwtService;
import com.anirudh.storage.minis3.util.CommonUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Anirudh
 * @since 24/02/26
 */

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private HttpServletRequest servletRequest;
    @Mock
    private HttpServletResponse servletResponse;

    @Mock
    private FilterChain filterChain;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    UUID subj;


    @BeforeEach
    void setup() {
        SecurityContextHolder.clearContext();
        subj = CommonUtils.generateRandomUUID();
    }

    @Test
    void doFilterInternal_header_empty() throws ServletException, IOException {

        when(servletRequest.getHeader("Authorization")).thenReturn(null);

        jwtAuthenticationFilter.doFilterInternal(servletRequest, servletResponse, filterChain);

        verify(filterChain).doFilter(servletRequest, servletResponse);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtService, never()).validateToken(anyString());


    }

    @Test
    void doFilterInternal_header_Not_contains_Bearer() throws ServletException, IOException {

        when(servletRequest.getHeader("Authorization")).thenReturn("mytoken123455");

        jwtAuthenticationFilter.doFilterInternal(servletRequest, servletResponse, filterChain);

        assertNotEquals("Bearer ", servletRequest.getHeader("Authorization").substring(0, 8));
        verify(filterChain).doFilter(servletRequest, servletResponse);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtService, never()).validateToken(anyString());


    }

    @Test
    void doFilterInternal_Invalid_Token() throws ServletException, IOException {

        when(servletRequest.getHeader("Authorization")).thenReturn("Bearer mytoken123455");

        when(jwtService.validateToken(anyString())).thenReturn(null);

        jwtAuthenticationFilter.doFilterInternal(servletRequest, servletResponse, filterChain);

        assertEquals("Bearer ", servletRequest.getHeader("Authorization").substring(0, 7));
        verify(filterChain).doFilter(servletRequest, servletResponse);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtService).validateToken(anyString());


    }

    @Test
    void doFilterInternal_valid_Token() throws ServletException, IOException {

        when(servletRequest.getHeader("Authorization")).thenReturn("Bearer mytoken123455");

        when(jwtService.validateToken(anyString())).thenReturn(subj.toString());

        jwtAuthenticationFilter.doFilterInternal(servletRequest, servletResponse, filterChain);

        verify(filterChain).doFilter(servletRequest, servletResponse);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        assertNotNull(((AuthenticatedUser)SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUserPublicId());
        assertEquals(subj, ((AuthenticatedUser)SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUserPublicId());
        verify(jwtService).validateToken(anyString());


    }
}