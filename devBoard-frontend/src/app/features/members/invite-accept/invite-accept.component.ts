import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MemberService } from '../../../core/services/member.service';
import { AuthService } from '../../../core/services/auth.service';
import { InvitePublicResponse } from '../../../core/models/member.models';
import { ApiError } from '../../../core/models/api-error.model';

@Component({
  selector: 'app-invite-accept',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './invite-accept.component.html',
  styleUrl: '../../auth/auth-shared.scss'
})
export class InviteAcceptComponent implements OnInit {
  token = '';
  invite: InvitePublicResponse | null = null;
  errorMessage: string | null = null;
  isAccepting = false;

  constructor(private memberService: MemberService, private authService: AuthService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    this.token = this.route.snapshot.paramMap.get('token') ?? '';
    if (!this.token) { this.errorMessage = 'Convite inválido.'; return; }
    this.memberService.getPublicInvite(this.token).subscribe({ next: invite => this.invite = invite, error: (error: ApiError) => this.errorMessage = error.message });
  }

  accept(): void {
    if (!this.authService.isAuthenticated()) {
      this.router.navigate(['/login'], { queryParams: { returnUrl: `/invites/${this.token}` } });
      return;
    }
    this.isAccepting = true;
    this.memberService.acceptInvite(this.token).subscribe({
      next: member => this.router.navigateByUrl(`/projects/${member.projectId}`),
      error: (error: ApiError) => { this.errorMessage = error.message; this.isAccepting = false; }
    });
  }
}
