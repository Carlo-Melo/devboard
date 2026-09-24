import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { MemberService } from '../../../core/services/member.service';
import { ProjectService } from '../../../core/services/project.service';
import { ProjectMemberResponse, ProjectResponse, ProjectRole } from '../../../core/models/project.models';
import { GithubImportResponse, InviteResponse } from '../../../core/models/member.models';
import { ApiError } from '../../../core/models/api-error.model';

@Component({
  selector: 'app-member-list',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './member-list.component.html',
  styleUrl: './member-list.component.scss'
})
export class MemberListComponent implements OnInit {

  project: ProjectResponse | null = null;
  members: ProjectMemberResponse[] = [];
  invites: InviteResponse[] = [];
  isLoading = true;
  isSubmitting = false;
  errorMessage: string | null = null;
  successMessage: string | null = null;
  generatedInviteLink: string | null = null;
  generatedInviteRole: ProjectRole | null = null;
  linkCopyMessage: string | null = null;
  readonly roles: ProjectRole[] = ['ADMIN', 'DEVELOPER', 'VIEWER'];
  readonly roleLabels: Record<ProjectRole, string> = {
    ADMIN: 'Administrador',
    DEVELOPER: 'Desenvolvedor',
    VIEWER: 'Visualizador'
  };

  readonly inviteForm = this.formBuilder.group({
    email: ['', [Validators.required, Validators.email]],
    role: ['DEVELOPER' as ProjectRole, Validators.required]
  });

  constructor(
    private formBuilder: FormBuilder,
    private memberService: MemberService,
    private projectService: ProjectService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  get canManage(): boolean {
    return this.project?.currentUserRole === 'ADMIN';
  }

  ngOnInit(): void {
    const projectId = Number(this.route.snapshot.paramMap.get('id'));
    this.load(projectId);
  }

  invite(): void {
    if (!this.project || this.inviteForm.invalid) {
      this.inviteForm.markAllAsTouched();
      return;
    }
    this.isSubmitting = true;
    this.errorMessage = null;
    const value = this.inviteForm.getRawValue();
    this.memberService.inviteByEmail(this.project.id, { email: value.email!, role: value.role! }).subscribe({
      next: () => {
        this.isSubmitting = false;
        this.successMessage = 'Convite enviado com sucesso.';
        this.inviteForm.reset({ role: 'DEVELOPER' });
        this.loadInvites(this.project!.id);
      },
      error: (error: ApiError) => {
        this.isSubmitting = false;
        this.errorMessage = error.message;
      }
    });
  }

  generateLink(role: ProjectRole): void {
    if (!this.project) return;
    this.memberService.createInviteLink(this.project.id, role).subscribe({
      next: (invite) => {
        this.generatedInviteLink = invite.acceptanceUrl;
        this.generatedInviteRole = role;
        this.linkCopyMessage = null;
        this.successMessage = 'Link de convite criado com sucesso.';
        this.loadInvites(this.project!.id);
      },
      error: (error: ApiError) => this.errorMessage = error.message
    });
  }

  async copyGeneratedInviteLink(): Promise<void> {
    if (!this.generatedInviteLink) return;

    try {
      if (!navigator.clipboard) {
        throw new Error('Clipboard API indisponível');
      }

      await navigator.clipboard.writeText(this.generatedInviteLink);
      this.linkCopyMessage = 'Link copiado para a área de transferência.';
    } catch {
      this.linkCopyMessage = 'Não foi possível copiar automaticamente. Selecione e copie o link manualmente.';
    }
  }

  importGithub(role: ProjectRole): void {
    if (!this.project) return;
    this.isSubmitting = true;
    this.errorMessage = null;
    this.memberService.importGithub(this.project.id, role).subscribe({
      next: (result: GithubImportResponse) => {
        this.isSubmitting = false;
        this.successMessage = `${result.added} membro(s) adicionado(s), ${result.invited} convite(s) enviado(s) e ${result.ignored} ignorado(s).`;
        this.load(this.project!.id);
      },
      error: (error: ApiError) => {
        this.isSubmitting = false;
        this.errorMessage = error.message;
      }
    });
  }

  changeRole(memberId: number, role: string): void {
    if (!this.project) return;
    this.memberService.updateRole(this.project.id, memberId, role as ProjectRole).subscribe({
      next: () => this.load(this.project!.id),
      error: (error: ApiError) => this.errorMessage = error.message
    });
  }

  remove(memberId: number, name: string): void {
    if (!this.project || !confirm(`Remover ${name} do projeto?`)) return;
    this.memberService.remove(this.project.id, memberId).subscribe({
      next: () => this.load(this.project!.id),
      error: (error: ApiError) => this.errorMessage = error.message
    });
  }

  leave(): void {
    if (!this.project || !confirm('Sair deste projeto?')) return;
    this.memberService.leave(this.project.id).subscribe({
      next: () => this.router.navigateByUrl('/projects'),
      error: (error: ApiError) => this.errorMessage = error.message
    });
  }

  initials(name: string): string {
    return name
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map(part => part[0])
      .join('')
      .toUpperCase();
  }

  private load(projectId: number): void {
    this.isLoading = true;
    forkJoin({
      project: this.projectService.getById(projectId),
      members: this.memberService.list(projectId)
    }).subscribe({
      next: ({ project, members }) => {
        this.project = project;
        this.members = members;
        this.isLoading = false;
        if (this.canManage) this.loadInvites(projectId);
      },
      error: (error: ApiError) => {
        this.errorMessage = error.message;
        this.isLoading = false;
      }
    });
  }

  private loadInvites(projectId: number): void {
    this.memberService.listInvites(projectId, 'PENDING').subscribe({
      next: invites => this.invites = invites,
      error: (error: ApiError) => this.errorMessage = error.message
    });
  }
}
