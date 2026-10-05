package com.devboard.security;

import com.devboard.config.SecurityConfig;
import com.devboard.config.AsyncConfig;
import com.devboard.controller.BoardGithubController;
import com.devboard.dto.board.GithubRepoResponse;
import com.devboard.dto.common.PageResponse;
import com.devboard.entity.User;
import com.devboard.exception.GithubAuthenticationException;
import com.devboard.repository.UserRepository;
import com.devboard.service.github.BoardGithubService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = BoardGithubController.class, properties = {
        "app.jwt.secret=only-for-tests-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "app.jwt.expiration-ms=86400000",
        "app.cors.allowed-origins=http://localhost:4200"
})
@Import({SecurityConfig.class, AsyncConfig.class, JwtTokenProvider.class, JwtAuthenticationEntryPoint.class})
class GithubAsyncAuthenticationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider jwt;
    @MockBean UserRepository users;
    @MockBean BoardGithubService github;
    private String token;

    @BeforeEach
    void setup() {
        User user = new User();
        user.setId(1L);
        user.setUsername("async-test");
        user.setEmail("async-test@example.com");
        when(users.findById(1L)).thenReturn(Optional.of(user));
        token = "Bearer " + jwt.generateToken(user);
    }

    @Test
    void publicRepositories_devePreservarAutenticacaoNoRedispatchAssincrono() throws Exception {
        when(github.publicRepositories(1L, null, 0, 20)).thenReturn(PageResponse.<GithubRepoResponse>builder()
                .content(List.of(new GithubRepoResponse(7, "async-test/repo", null, "https://github.com/async-test/repo", "main", false, false)))
                .page(0).size(20).totalElements(1).totalPages(1).first(true).last(true).build());

        var result = mvc.perform(get("/api/github-repos/public").header("Authorization", token))
                .andExpect(request().asyncStarted()).andReturn();

        mvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].fullName").value("async-test/repo"));
    }

    @Test
    void publicRepositories_deveRetornarErroGitHubSemConfundirComSessaoDevBoard() throws Exception {
        when(github.publicRepositories(1L, null, 0, 20))
                .thenThrow(new GithubAuthenticationException("Autorização GitHub expirada"));

        var result = mvc.perform(get("/api/github-repos/public").header("Authorization", token))
                .andExpect(request().asyncStarted()).andReturn();

        mvc.perform(asyncDispatch(result))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Autorização GitHub expirada"));
    }

    @Test
    void publicRepositories_deveExigirJwtValidoAntesDeIniciarConsulta() throws Exception {
        mvc.perform(get("/api/github-repos/public").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized()).andExpect(request().asyncNotStarted());
        verifyNoInteractions(github);
    }
}
