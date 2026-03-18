package com.anirudh.storage.minis3.auth.jwt.filters;

import com.anirudh.storage.minis3.auth.dto.AuthenticatedUser;
import com.anirudh.storage.minis3.auth.jwt.service.JwtService;
import com.anirudh.storage.minis3.constants.MiniS3Status;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Anirudh
 * @since 24/02/26
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String authorizationHeaderValue = request.getHeader("Authorization");
        if (Objects.isNull(authorizationHeaderValue) || !authorizationHeaderValue.startsWith(MiniS3Status.BEARER.getValue())) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = extractToken(authorizationHeaderValue);

        String subj = jwtService.validateToken(token);

        if (Objects.isNull(subj)) {
            filterChain.doFilter(request, response);
            return;
        }


        AuthenticatedUser user = new AuthenticatedUser(UUID.fromString(subj));

        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(user, null, List.of());

        if (Objects.isNull(SecurityContextHolder.getContext().getAuthentication()))
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        filterChain.doFilter(request, response);

    }

    private String extractToken(String authorizationHeaderValue) {
        return authorizationHeaderValue.substring(MiniS3Status.BEARER.getValue().length());
    }
}
