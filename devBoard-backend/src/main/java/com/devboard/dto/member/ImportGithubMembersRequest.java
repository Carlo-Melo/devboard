package com.devboard.dto.member;

import com.devboard.entity.enums.ProjectRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ImportGithubMembersRequest {

    @NotNull(message = "Papel é obrigatório")
    private ProjectRole role;

    private List<String> logins;
}
