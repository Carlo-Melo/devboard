package com.devboard.dto.auth;

import com.devboard.validation.FieldsMatch;
import com.devboard.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

@Data
@FieldsMatch(first = "password", second = "confirmPassword", message = "Senhas não coincidem")
public class RegisterRequest {

    @NotBlank(message = "Username é obrigatório")
    @Size(min = 3, max = 50, message = "Username deve ter entre 3 e 50 caracteres")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username deve conter apenas letras, números e underscore, sem espaços")
    private String username;

    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email inválido")
    private String email;

    @ToString.Exclude
    @NotBlank(message = "Senha é obrigatória")
    @StrongPassword
    private String password;

    @ToString.Exclude
    @NotBlank(message = "Confirmação de senha é obrigatória")
    private String confirmPassword;

    private String fullName;
}
