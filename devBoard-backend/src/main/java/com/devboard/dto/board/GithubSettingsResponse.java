package com.devboard.dto.board;

public record GithubSettingsResponse(BoardResponse board, boolean moveOnCommit, boolean moveOnPrOpen,
        boolean moveOnPrMerge, boolean importIssues, boolean closeIssueOnDone, String branchPattern) {}
