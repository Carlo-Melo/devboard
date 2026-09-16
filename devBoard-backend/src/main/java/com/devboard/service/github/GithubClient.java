package com.devboard.service.github;

import com.devboard.exception.ExternalServiceException;
import com.devboard.exception.InvalidRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Chamadas REST diretas à API do GitHub necessárias para o login OAuth (troca de code,
 * perfil e email). Sem estado de configuração — client id/secret/callback vêm por parâmetro
 * para manter a classe pura e fácil de testar.
 */
@Slf4j
@Component
public class GithubClient {

    private final RestClient oauthClient = RestClient.create("https://github.com");
    private final RestClient apiClient = RestClient.create("https://api.github.com");

    public record TokenResponse(String accessToken) {
    }

    public record GithubProfile(Long id, String login, String name, String avatarUrl) {
    }

    public record GithubCollaborator(Long id, String login, String email) {
    }

    public TokenResponse exchangeCode(String code, String clientId, String clientSecret, String callbackUrl) {
        try {
            Map<?, ?> response = oauthClient.post()
                    .uri("/login/oauth/access_token")
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .body(Map.of(
                            "client_id", clientId,
                            "client_secret", clientSecret,
                            "code", code,
                            "redirect_uri", callbackUrl
                    ))
                    .retrieve()
                    .body(Map.class);

            Object accessToken = response == null ? null : response.get("access_token");
            if (accessToken == null) {
                throw new InvalidRequestException("Code do GitHub inválido ou expirado");
            }

            return new TokenResponse(accessToken.toString());
        } catch (RestClientResponseException ex) {
            log.warn("Falha ao trocar code por access token no GitHub: status={}", ex.getStatusCode());
            throw new ExternalServiceException("GitHub indisponível");
        } catch (ResourceAccessException ex) {
            log.warn("GitHub inacessível ao trocar code por access token", ex);
            throw new ExternalServiceException("GitHub indisponível");
        }
    }

    public GithubProfile fetchProfile(String accessToken) {
        try {
            Map<?, ?> response = apiClient.get()
                    .uri("/user")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);

            if (response == null || response.get("id") == null) {
                throw new InvalidRequestException("Token GitHub inválido");
            }

            return new GithubProfile(
                    ((Number) response.get("id")).longValue(),
                    (String) response.get("login"),
                    (String) response.get("name"),
                    (String) response.get("avatar_url")
            );
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 401) {
                throw new InvalidRequestException("Token GitHub inválido");
            }
            log.warn("Falha ao buscar perfil no GitHub: status={}", ex.getStatusCode());
            throw new ExternalServiceException("GitHub indisponível");
        } catch (ResourceAccessException ex) {
            log.warn("GitHub inacessível ao buscar perfil", ex);
            throw new ExternalServiceException("GitHub indisponível");
        }
    }

    public String fetchPrimaryVerifiedEmail(String accessToken) {
        try {
            List<Map<String, Object>> emails = apiClient.get()
                    .uri("/user/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(List.class);

            return (emails == null ? List.<Map<String, Object>>of() : emails).stream()
                    .filter(entry -> Boolean.TRUE.equals(entry.get("primary")) && Boolean.TRUE.equals(entry.get("verified")))
                    .map(entry -> (String) entry.get("email"))
                    .findFirst()
                    .orElseThrow(() -> new InvalidRequestException("Não foi possível obter um email verificado do GitHub"));
        } catch (RestClientResponseException ex) {
            log.warn("Falha ao buscar emails no GitHub: status={}", ex.getStatusCode());
            throw new ExternalServiceException("GitHub indisponível");
        } catch (ResourceAccessException ex) {
            log.warn("GitHub inacessível ao buscar emails", ex);
            throw new ExternalServiceException("GitHub indisponível");
        }
    }

    public List<GithubCollaborator> fetchCollaborators(String accessToken, String owner, String repository) {
        try {
            List<Map<String, Object>> collaborators = apiClient.get()
                    .uri("/repos/{owner}/{repository}/collaborators", owner, repository)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(List.class);

            return (collaborators == null ? List.<Map<String, Object>>of() : collaborators).stream()
                    .map(entry -> toCollaborator(accessToken, entry))
                    .toList();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 401) {
                throw new com.devboard.exception.UnauthorizedException("Token GitHub inválido");
            }
            log.warn("Falha ao listar colaboradores no GitHub: status={}", ex.getStatusCode());
            throw new ExternalServiceException("GitHub indisponível");
        } catch (ResourceAccessException ex) {
            log.warn("GitHub inacessível ao listar colaboradores", ex);
            throw new ExternalServiceException("GitHub indisponível");
        }
    }

    private GithubCollaborator toCollaborator(String accessToken, Map<String, Object> entry) {
        Long id = ((Number) entry.get("id")).longValue();
        String login = (String) entry.get("login");
        String email = fetchPublicEmail(accessToken, login);
        return new GithubCollaborator(id, login, email);
    }

    private String fetchPublicEmail(String accessToken, String login) {
        try {
            Map<?, ?> response = apiClient.get()
                    .uri("/users/{login}", login)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);
            return response == null ? null : (String) response.get("email");
        } catch (RestClientResponseException ex) {
            log.debug("Email público indisponível para colaborador {}: status={}", login, ex.getStatusCode());
            return null;
        } catch (ResourceAccessException ex) {
            log.debug("Não foi possível consultar email público do colaborador {}", login);
            return null;
        }
    }
}
