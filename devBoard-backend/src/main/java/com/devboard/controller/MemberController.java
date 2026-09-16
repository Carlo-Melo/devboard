package com.devboard.controller;

import com.devboard.dto.member.CreateEmailInviteRequest;
import com.devboard.dto.member.CreateInviteLinkRequest;
import com.devboard.dto.member.GithubImportResponse;
import com.devboard.dto.member.ImportGithubMembersRequest;
import com.devboard.dto.member.InviteResponse;
import com.devboard.dto.member.UpdateMemberRoleRequest;
import com.devboard.dto.project.ProjectMemberResponse;
import com.devboard.entity.enums.InviteStatus;
import com.devboard.security.SecurityUser;
import com.devboard.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @PostMapping("/api/projects/{projectId}/members/invite")
    public ResponseEntity<InviteResponse> inviteByEmail(@PathVariable Long projectId,
                                                          @Valid @RequestBody CreateEmailInviteRequest request,
                                                          @AuthenticationPrincipal SecurityUser user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(memberService.inviteByEmail(projectId, request.getEmail(), request.getRole(), user.getId()));
    }

    @PostMapping("/api/projects/{projectId}/members/invite-link")
    public ResponseEntity<InviteResponse> createInviteLink(@PathVariable Long projectId,
                                                            @Valid @RequestBody CreateInviteLinkRequest request,
                                                            @AuthenticationPrincipal SecurityUser user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(memberService.createInviteLink(projectId, request.getRole(), user.getId()));
    }

    @PostMapping("/api/projects/{projectId}/members/import-github")
    public ResponseEntity<GithubImportResponse> importGithub(@PathVariable Long projectId,
                                                               @Valid @RequestBody ImportGithubMembersRequest request,
                                                               @AuthenticationPrincipal SecurityUser user) {
        return ResponseEntity.ok(memberService.importGithubCollaborators(
                projectId, request.getRole(), request.getLogins(), user.getId()));
    }

    @GetMapping("/api/projects/{projectId}/members")
    public ResponseEntity<List<ProjectMemberResponse>> listMembers(@PathVariable Long projectId,
                                                                     @AuthenticationPrincipal SecurityUser user) {
        return ResponseEntity.ok(memberService.listMembers(projectId, user.getId()));
    }

    @GetMapping("/api/projects/{projectId}/invites")
    public ResponseEntity<List<InviteResponse>> listInvites(@PathVariable Long projectId,
                                                              @RequestParam(required = false) InviteStatus status,
                                                              @AuthenticationPrincipal SecurityUser user) {
        return ResponseEntity.ok(memberService.listInvites(projectId, status, user.getId()));
    }

    @PutMapping("/api/projects/{projectId}/members/{memberId}")
    public ResponseEntity<ProjectMemberResponse> updateRole(@PathVariable Long projectId,
                                                              @PathVariable Long memberId,
                                                              @Valid @RequestBody UpdateMemberRoleRequest request,
                                                              @AuthenticationPrincipal SecurityUser user) {
        return ResponseEntity.ok(memberService.updateRole(projectId, memberId, request.getRole(), user.getId()));
    }

    @DeleteMapping("/api/projects/{projectId}/members/{memberId}")
    public ResponseEntity<Void> remove(@PathVariable Long projectId, @PathVariable Long memberId,
                                       @AuthenticationPrincipal SecurityUser user) {
        memberService.removeMember(projectId, memberId, user.getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/projects/{projectId}/members/me")
    public ResponseEntity<Void> leave(@PathVariable Long projectId, @AuthenticationPrincipal SecurityUser user) {
        memberService.leaveProject(projectId, user.getId());
        return ResponseEntity.noContent().build();
    }
}
