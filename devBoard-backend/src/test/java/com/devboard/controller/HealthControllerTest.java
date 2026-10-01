package com.devboard.controller;

import com.devboard.config.SecurityConfig;
import com.devboard.exception.GlobalExceptionHandler;
import com.devboard.repository.UserRepository;
import com.devboard.security.JwtAuthenticationEntryPoint;
import com.devboard.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {
        HealthController.class,
        SecurityConfig.class,
        JwtAuthenticationEntryPoint.class,
        GlobalExceptionHandler.class
})
@AutoConfigureMockMvc
@ImportAutoConfiguration({JacksonAutoConfiguration.class, WebMvcAutoConfiguration.class})
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserRepository userRepository;

    @Test
    void health_deveRetornar200ComStatusUp_quandoSemAutenticacao() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(content().json("{\"status\":\"UP\"}", true));
    }
}
