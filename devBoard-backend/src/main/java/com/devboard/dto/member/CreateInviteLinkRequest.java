package com.devboard.dto.member;

import com.devboard.entity.enums.ProjectRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateInviteLinkRequest {

    @NotNull(message = "Papel é obrigatório")
    private ProjectRole role;
}
