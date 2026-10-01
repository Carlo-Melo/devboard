package com.devboard.controller;

import com.devboard.service.AuthService;
import com.devboard.service.github.GithubOAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private GithubOAuthService githubOAuthService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, githubOAuthService)).build();
    }

    @Test
    void githubLogin_deveRedirecionarParaAutorizacaoDoGithub_semEspacos() throws Exception {
        String authorizationUrl = "https://github.com/login/oauth/authorize?client_id=client-id"
                + "&redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fapi%2Fauth%2Fgithub%2Fcallback"
                + "&scope=repo%20user%3Aemail&state=state-fake";
        when(githubOAuthService.buildAuthorizationUrl(null)).thenReturn(authorizationUrl);

        mockMvc.perform(get("/api/auth/github/login"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", authorizationUrl));
    }
}
