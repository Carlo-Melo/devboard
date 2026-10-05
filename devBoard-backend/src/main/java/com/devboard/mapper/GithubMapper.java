package com.devboard.mapper;

import com.devboard.dto.board.*;
import com.devboard.entity.Board;
import com.devboard.service.github.GithubRepositoryClient.RepositoryData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;

@Component @RequiredArgsConstructor
public class GithubMapper {
    private final BoardMapper boards;
    public GithubSettingsResponse settings(Board b) {
        return new GithubSettingsResponse(boards.toResponse(b, List.of()), b.isMoveOnCommit(), b.isMoveOnPrOpen(),
                b.isMoveOnPrMerge(), b.isImportIssues(), b.isCloseIssueOnDone(), b.getBranchPattern());
    }
    public GithubRepoResponse repository(RepositoryData r, boolean linked) {
        return new GithubRepoResponse(r.id(), r.fullName(), r.description(), r.url(), r.defaultBranch(), linked,
                r.isPrivate());
    }
    public com.devboard.dto.board.GithubDestinationResponse destination(Board b) {
        return new com.devboard.dto.board.GithubDestinationResponse(b.getProject().getId(), b.getProject().getName(), b.getId(), b.getName());
    }
}
