package com.devboard.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobre o critério de aceite central da spec: sem JWT o endpoint protegido responde 401,
 * com JWT válido responde 200, com JWT malformado/inválido responde 401.
 * Requer um Postgres real acessível (ver claude.md / configuração de datasource).
 */
@SpringBootTest
@AutoConfigureMockMvc
class JwtAuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void me_deveRetornar401_quandoSemToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_deveRetornar401_quandoTokenInvalido() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_deveRetornar200_quandoTokenValido() throws Exception {
        String suffix = String.valueOf(System.currentTimeMillis());
        Map<String, String> registerRequest = Map.of(
                "username", "itest_" + suffix,
                "email", "itest_" + suffix + "@example.com",
                "password", "SecurePass123",
                "confirmPassword", "SecurePass123"
        );

        String registerResponseJson = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(registerResponseJson, "$.token");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
