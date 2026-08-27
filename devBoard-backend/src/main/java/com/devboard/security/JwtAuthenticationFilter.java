package com.devboard.security;

import com.devboard.entity.User;
import com.devboard.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Não é {@code @Component}: se fosse, o Spring Boot também a registraria como filtro de
 * servlet global (por ser um {@link jakarta.servlet.Filter}), executando-a duas vezes por
 * requisição além do registro explícito no {@link com.devboard.config.SecurityConfig}.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        extractToken(request)
                .flatMap(jwtTokenProvider::parseClaims)
                .map(jwtTokenProvider::getUserId)
                .flatMap(userRepository::findById)
                .ifPresent(this::authenticate);

        filterChain.doFilter(request, response);
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return Optional.of(header.substring(BEARER_PREFIX.length()));
        }
        return Optional.empty();
    }

    private void authenticate(User user) {
        SecurityUser securityUser = new SecurityUser(user);
        var authentication = new UsernamePasswordAuthenticationToken(
                securityUser, null, securityUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
