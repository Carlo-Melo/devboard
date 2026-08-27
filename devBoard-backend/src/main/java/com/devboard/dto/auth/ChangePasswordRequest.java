package com.devboard.dto.auth;

import com.devboard.validation.FieldsMatch;
import com.devboard.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

@Data
@FieldsMatch(first = "newPassword", second = "confirmPassword", message = "Senhas não coincidem")
public class ChangePasswordRequest {

    @ToString.Exclude
    @NotBlank(message = "Senha atual é obrigatória")
    private String currentPassword;

    @ToString.Exclude
    @NotBlank(message = "Nova senha é obrigatória")
    @StrongPassword
    private String newPassword;

    @ToString.Exclude
    @NotBlank(message = "Confirmação de senha é obrigatória")
    private String confirmPassword;
}
