package com.devboard.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

@Data
public class LoginRequest {

    @NotBlank(message = "Email ou username é obrigatório")
    private String emailOrUsername;

    @ToString.Exclude
    @NotBlank(message = "Senha é obrigatória")
    private String password;
}
