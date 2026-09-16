package com.devboard.dto.member;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GithubImportResponse {
    private int added;
    private int invited;
    private int ignored;
}
