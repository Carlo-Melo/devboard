package com.devboard.dto.member;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AcceptInviteRequest {

    @NotBlank(message = "Token do convite é obrigatório")
    private String token;
}
