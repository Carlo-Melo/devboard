package com.devboard.dto.task;
import jakarta.validation.constraints.Size;
public record CreateBranchRequest(@Size(max=255) String baseBranch, @Size(max=255) String name) {}
