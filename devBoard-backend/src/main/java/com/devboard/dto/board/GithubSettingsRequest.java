package com.devboard.dto.board;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

@Data
public class GithubSettingsRequest {
    private boolean moveOnCommit = true;
    private boolean moveOnPrOpen = true;
    private boolean moveOnPrMerge = true;
    private boolean importIssues = true;
    private boolean closeIssueOnDone = true;
    @NotBlank @Size(max = 255) private String branchPattern = "feature/task-{id}-{title}";
    @NotBlank @Size(max = 255) private String defaultBaseBranch = "main";
    private List<@NotBlank @Size(max = 255) String> watchedBranches = List.of();
}
