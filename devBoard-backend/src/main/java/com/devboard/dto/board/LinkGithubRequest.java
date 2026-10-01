package com.devboard.dto.board;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

@Data
public class LinkGithubRequest {
    @NotNull @Positive private Long githubRepoId;
    private List<@NotBlank @Size(max = 255) String> watchedBranches;
    @Size(max = 255) private String defaultBaseBranch;
}
