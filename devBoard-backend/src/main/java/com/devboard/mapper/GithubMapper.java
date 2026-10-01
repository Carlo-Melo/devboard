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
        return new GithubRepoResponse(r.id(), r.fullName(), r.description(), r.url(), r.defaultBranch(), linked);
    }
}
