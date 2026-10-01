package com.devboard.dto.board;

public record GithubRepoResponse(long id, String fullName, String description, String url,
                                 String defaultBranch, boolean linked) {}
