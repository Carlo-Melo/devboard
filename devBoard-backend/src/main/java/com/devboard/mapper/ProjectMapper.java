package com.devboard.mapper;

import com.devboard.dto.project.BoardSummaryResponse;
import com.devboard.dto.project.ProjectMemberResponse;
import com.devboard.dto.project.ProjectResponse;
import com.devboard.dto.project.ProjectSummaryResponse;
import com.devboard.entity.Board;
import com.devboard.entity.Project;
import com.devboard.entity.ProjectMember;
import com.devboard.entity.enums.ProjectRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ProjectMapper {

    private final UserMapper userMapper;

    public ProjectSummaryResponse toSummary(Project project, long memberCount) {
        return ProjectSummaryResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .owner(userMapper.toResponse(project.getOwner()))
                .memberCount(memberCount)
                .githubLinked(project.hasGithubRepo())
                .lastSyncAt(project.getLastSyncAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }

    public ProjectResponse toResponse(Project project, ProjectRole currentUserRole, boolean currentUserOwner,
                                       List<ProjectMember> members, List<Board> boards) {
        return ProjectResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .owner(userMapper.toResponse(project.getOwner()))
                .currentUserRole(currentUserRole.name())
                .currentUserOwner(currentUserOwner)
                .githubRepoId(project.getGithubRepoId())
                .githubRepoOwner(project.getGithubRepoOwner())
                .githubRepoName(project.getGithubRepoName())
                .githubRepoUrl(project.getGithubRepoUrl())
                .watchedBranches(new ArrayList<>(project.getWatchedBranches()))
                .defaultBaseBranch(project.getDefaultBaseBranch())
                .archived(project.getArchived())
                .lastSyncAt(project.getLastSyncAt())
                .members(members.stream().map(this::toMemberResponse).toList())
                .boards(boards.stream().map(this::toBoardSummary).toList())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }

    private ProjectMemberResponse toMemberResponse(ProjectMember member) {
        return ProjectMemberResponse.builder()
                .user(userMapper.toResponse(member.getUser()))
                .role(member.getRole().name())
                .build();
    }

    private BoardSummaryResponse toBoardSummary(Board board) {
        return BoardSummaryResponse.builder()
                .id(board.getId())
                .name(board.getName())
                .defaultBoard(board.getIsDefault())
                .build();
    }
}
