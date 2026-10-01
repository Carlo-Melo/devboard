package com.devboard.event;

/** Immutable snapshot: no credentials or managed entities cross the asynchronous boundary. */
public record GithubJobEvent(String operation, Long boardId, Long repositoryId, Long userId,
                             Long hookId, Long taskId, String value) {}
