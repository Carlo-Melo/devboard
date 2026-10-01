package com.devboard.dto.task;
import jakarta.validation.constraints.*;
public record LinkIssueRequest(@NotNull @Positive Long githubIssueId) {}
