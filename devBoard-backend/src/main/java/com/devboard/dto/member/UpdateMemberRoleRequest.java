package com.devboard.dto.member;

import com.devboard.entity.enums.ProjectRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateMemberRoleRequest {

    @NotNull(message = "Papel é obrigatório")
    private ProjectRole role;
}
