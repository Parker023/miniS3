package com.anirudh.storage.minis3.auth.apikey.filter;

import com.anirudh.storage.minis3.auth.apikey.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

import static com.anirudh.storage.minis3.constants.MiniS3Status.API_KEY;
import static com.anirudh.storage.minis3.constants.MiniS3Status.BEARER;

/**
 * Authenticates {@code Authorization: Bearer ms3_...} headers. Anything else is left to the JWT filter.
 *
 * @author Anirudh
 * @since 19/10/26
 */
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String authorizationHeaderValue = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (Objects.isNull(authorizationHeaderValue) || !authorizationHeaderValue.startsWith(BEARER.getValue() + API_KEY.getValue())) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawKey = authorizationHeaderValue.substring(BEARER.getValue().length());

        apiKeyService.authenticate(rawKey).ifPresent(user -> {
            if (Objects.isNull(SecurityContextHolder.getContext().getAuthentication())) {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
            }
        });

        filterChain.doFilter(request, response);
    }
}
