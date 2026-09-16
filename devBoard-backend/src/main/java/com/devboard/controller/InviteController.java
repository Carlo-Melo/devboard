package com.devboard.controller;

import com.devboard.dto.member.AcceptInviteRequest;
import com.devboard.dto.member.InvitePublicResponse;
import com.devboard.dto.project.ProjectMemberResponse;
import com.devboard.security.SecurityUser;
import com.devboard.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InviteController {

    private final MemberService memberService;

    @PostMapping("/api/invites/accept")
    public ResponseEntity<ProjectMemberResponse> accept(@Valid @RequestBody AcceptInviteRequest request,
                                                         @AuthenticationPrincipal SecurityUser user) {
        return ResponseEntity.ok(memberService.acceptInvite(request.getToken(), user.getId()));
    }

    @GetMapping("/api/invites/{token}")
    public ResponseEntity<InvitePublicResponse> getPublic(@PathVariable String token) {
        return ResponseEntity.ok(memberService.getPublicInvite(token));
    }

    @DeleteMapping("/api/invites/{inviteId}")
    public ResponseEntity<Void> revoke(@PathVariable Long inviteId, @AuthenticationPrincipal SecurityUser user) {
        memberService.revokeInvite(inviteId, user.getId());
        return ResponseEntity.noContent().build();
    }
}
