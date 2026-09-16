package com.devboard.service.github;

import com.devboard.dto.auth.AuthResponse;
import com.devboard.entity.User;
import com.devboard.entity.enums.AuthProvider;
import com.devboard.exception.InvalidRequestException;
import com.devboard.repository.UserRepository;
import com.devboard.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Orquestra o login via GitHub OAuth (spec-authentication.md §4.3/§4.4): inicia o handshake,
 * troca o code por token, resolve/cria o usuário e emite o JWT do devBoard.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GithubOAuthService {

    private static final String OAUTH_SCOPES = "repo user:email";
    private static final String DEFAULT_RETURN_URL = "/projects";

    private final UserRepository userRepository;
    private final AuthService authService;
    private final GithubClient githubClient;
    private final GithubOAuthStateService stateService;

    @Value("${app.github.client-id}")
    private String clientId;

    @Value("${app.github.client-secret}")
    private String clientSecret;

    @Value("${app.github.callback-url}")
    private String callbackUrl;

    @Value("${app.base-url}")
    private String frontendBaseUrl;

    public String buildAuthorizationUrl(String redirectUri) {
        String state = stateService.create(redirectUri);

        return UriComponentsBuilder.fromUriString("https://github.com/login/oauth/authorize")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", callbackUrl)
                .queryParam("scope", OAUTH_SCOPES)
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    @Transactional
    public String handleCallback(String code, String state) {
        String redirectUri = stateService.consume(state)
                .orElseThrow(() -> new InvalidRequestException("State do GitHub inválido ou expirado"));

        GithubClient.TokenResponse tokenResponse = githubClient.exchangeCode(code, clientId, clientSecret, callbackUrl);
        GithubClient.GithubProfile profile = githubClient.fetchProfile(tokenResponse.accessToken());
        String email = githubClient.fetchPrimaryVerifiedEmail(tokenResponse.accessToken());

        User user = resolveUser(profile, email, tokenResponse.accessToken());
        AuthResponse authResponse = authService.buildAuthResponse(user);

        log.info("Login via GitHub bem-sucedido: userId={}, githubId={}", user.getId(), profile.id());
        return buildFrontendRedirectUrl(authResponse, redirectUri);
    }

    private User resolveUser(GithubClient.GithubProfile profile, String email, String accessToken) {
        User user = userRepository.findByGithubId(profile.id())
                .or(() -> userRepository.findByEmail(email))
                .orElseGet(() -> createUser(profile, email));

        user.setGithubId(profile.id());
        user.setGithubUsername(profile.login());
        user.setGithubToken(accessToken);
        if (user.getAvatarUrl() == null) {
            user.setAvatarUrl(profile.avatarUrl());
        }

        return userRepository.save(user);
    }

    private User createUser(GithubClient.GithubProfile profile, String email) {
        User user = new User();
        user.setUsername(resolveAvailableUsername(profile.login()));
        user.setEmail(email);
        user.setFullName(profile.name());
        user.setAvatarUrl(profile.avatarUrl());
        user.setAuthProvider(AuthProvider.GITHUB);
        user.setPassword(null);

        log.info("Nova conta criada via login GitHub: githubId={}", profile.id());
        return user;
    }

    private String resolveAvailableUsername(String githubLogin) {
        String candidate = githubLogin;
        int suffix = 2;
        while (userRepository.existsByUsername(candidate)) {
            candidate = githubLogin + suffix;
            suffix++;
        }
        return candidate;
    }

    private String buildFrontendRedirectUrl(AuthResponse authResponse, String redirectUri) {
        String returnUrl = (redirectUri == null || redirectUri.isBlank()) ? DEFAULT_RETURN_URL : redirectUri;

        String fragment = "token=" + authResponse.getToken()
                + "&expiresAt=" + authResponse.getExpiresAt()
                + "&returnUrl=" + URLEncoder.encode(returnUrl, StandardCharsets.UTF_8);

        return frontendBaseUrl + "/auth/github/callback#" + fragment;
    }
}
