package com.devboard.service.github;

import com.devboard.util.HashUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Proteção CSRF do handshake OAuth do GitHub: o {@code state} é opaco, de uso único e
 * carrega o {@code redirectUri} original do frontend. Guardado em memória (Caffeine),
 * suficiente para o MVP — mesmo padrão de {@code ratelimit.RateLimiterService}.
 */
@Component
public class GithubOAuthStateService {

    private static final String EMPTY_REDIRECT_MARKER = "";

    private final Cache<String, String> states = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    public String create(String redirectUri) {
        String state = HashUtil.generateSecureToken();
        states.put(state, redirectUri == null ? EMPTY_REDIRECT_MARKER : redirectUri);
        return state;
    }

    /** Remove o state (uso único) e devolve o redirectUri associado, se o state for válido. */
    public Optional<String> consume(String state) {
        if (state == null) {
            return Optional.empty();
        }

        String redirectUri = states.asMap().remove(state);
        if (redirectUri == null) {
            return Optional.empty();
        }

        return redirectUri.equals(EMPTY_REDIRECT_MARKER) ? Optional.of("") : Optional.of(redirectUri);
    }
}
