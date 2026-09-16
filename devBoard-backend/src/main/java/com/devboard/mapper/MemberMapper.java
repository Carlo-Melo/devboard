package com.devboard.mapper;

import com.devboard.dto.member.InvitePublicResponse;
import com.devboard.dto.member.InviteResponse;
import com.devboard.dto.project.ProjectMemberResponse;
import com.devboard.entity.ProjectInvite;
import com.devboard.entity.ProjectMember;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberMapper {

    private final UserMapper userMapper;

    @Value("${app.base-url}")
    private String baseUrl;

    public ProjectMemberResponse toMemberResponse(ProjectMember member) {
        return ProjectMemberResponse.builder()
                .id(member.getId())
                .projectId(member.getProject().getId())
                .user(userMapper.toResponse(member.getUser()))
                .role(member.getRole().name())
                .invitedBy(member.getInvitedBy() != null ? userMapper.toResponse(member.getInvitedBy()) : null)
                .joinedAt(member.getJoinedAt())
                .build();
    }

    public InviteResponse toInviteResponse(ProjectInvite invite) {
        return InviteResponse.builder()
                .id(invite.getId())
                .projectId(invite.getProject().getId())
                .type(invite.getType().name())
                .email(invite.getEmail())
                .role(invite.getRole().name())
                .status(invite.getStatus().name())
                .acceptanceUrl(baseUrl + "/invites/" + invite.getToken())
                .invitedBy(userMapper.toResponse(invite.getInvitedBy()))
                .uses(invite.getUses())
                .maxUses(invite.getMaxUses())
                .expiresAt(invite.getExpiresAt())
                .createdAt(invite.getCreatedAt())
                .build();
    }

    public InvitePublicResponse toPublicResponse(ProjectInvite invite) {
        String inviterName = invite.getInvitedBy().getFullName() != null
                ? invite.getInvitedBy().getFullName() : invite.getInvitedBy().getUsername();
        return InvitePublicResponse.builder()
                .projectName(invite.getProject().getName())
                .inviterName(inviterName)
                .role(invite.getRole().name())
                .expiresAt(invite.getExpiresAt())
                .build();
    }
}
