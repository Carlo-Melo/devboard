import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { BoardService } from '../../../core/services/board.service';
import { GithubService } from '../../../core/services/github.service';
import { MemberService } from '../../../core/services/member.service';
import { ProjectService } from '../../../core/services/project.service';
import { BoardResponse } from '../../../core/models/board.models';
import { GithubRepoResponse } from '../../../core/models/github.models';
import { ProjectRole } from '../../../core/models/project.models';
import { ApiError } from '../../../core/models/api-error.model';

@Component({
  selector: 'app-board-settings', standalone: true, imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './board-settings.component.html', styleUrl: './board-settings.component.scss'
})
export class BoardSettingsComponent implements OnInit {
  board: BoardResponse | null = null;
  repositories: GithubRepoResponse[] = [];
  role: ProjectRole = 'VIEWER';
  busy = false;
  error: string | null = null;
  message: string | null = null;
  readonly details = this.fb.nonNullable.group({ name: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(100)]], description: [''], defaultBoard: [false] });
  readonly linkForm = this.fb.group({ repositoryId: [null as number | null, Validators.required], defaultBaseBranch: [''], watchedBranches: [''] });
  readonly searchForm = this.fb.nonNullable.group({ search: [''] });
  readonly settingsForm = this.fb.nonNullable.group({ moveOnCommit: [true], moveOnPrOpen: [true], moveOnPrMerge: [true], importIssues: [true], closeIssueOnDone: [true], branchPattern: ['feature/task-{id}-{title}', Validators.required], defaultBaseBranch: ['main', Validators.required], watchedBranches: [''] });
  readonly membersForm = this.fb.nonNullable.group({ role: ['DEVELOPER' as ProjectRole], logins: [''] });
  constructor(private fb: FormBuilder, private boards: BoardService, private github: GithubService, private members: MemberService, private projects: ProjectService, private route: ActivatedRoute, private router: Router) {}
  get canManage() { return this.role === 'ADMIN'; }
  get canSync() { return this.role !== 'VIEWER'; }
  ngOnInit() { this.load(Number(this.route.snapshot.paramMap.get('id'))); }
  load(id: number) {
    this.github.settings(id).subscribe({ next: s => {
      this.board = s.board;
      this.details.patchValue({ name: s.board.name, description: s.board.description ?? '', defaultBoard: s.board.defaultBoard });
      this.settingsForm.patchValue({ ...s, defaultBaseBranch: s.board.defaultBaseBranch, watchedBranches: s.board.watchedBranches.join(', ') });
      this.projects.getById(s.board.projectId).subscribe({ next: p => this.role = p.currentUserRole, error: e => this.fail(e) });
    }, error: e => this.fail(e) });
  }
  saveBoard() {
    if (!this.board || this.details.invalid || this.busy) { this.details.markAllAsTouched(); return; }
    this.start(); this.boards.update(this.board.id, this.details.getRawValue()).subscribe({ next: b => { this.board = b; this.done('Board atualizado.'); }, error: e => this.fail(e) });
  }
  searchRepositories() {
    this.start(); this.github.repositories(this.searchForm.getRawValue().search).subscribe({ next: r => { this.repositories = r; this.busy = false; }, error: e => this.fail(e) });
  }
  link() {
    if (!this.board || this.linkForm.invalid || this.busy) return;
    this.start(); const r = this.linkForm.getRawValue();
    const repo = this.repositories.find(repo => repo.id === Number(r.repositoryId));
    this.github.link(this.board.id, Number(r.repositoryId), r.defaultBaseBranch || repo?.defaultBranch || 'main', this.values(r.watchedBranches ?? '')).subscribe({ next: s => { this.done('Repositório vinculado. Registro do webhook e importação em andamento.'); this.load(s.board.id); }, error: e => this.fail(e) });
  }
  unlink() {
    if (!this.board || !confirm('Desvincular este repositório? As tarefas permanecem e perdem os vínculos GitHub.')) return;
    this.start(); this.github.unlink(this.board.id).subscribe({ next: () => { this.done('Repositório desvinculado.'); this.load(this.board!.id); }, error: e => this.fail(e) });
  }
  saveSettings() {
    if (!this.board || this.settingsForm.invalid || this.busy) return;
    this.start(); const r = this.settingsForm.getRawValue();
    this.github.update(this.board.id, { ...r, watchedBranches: this.values(r.watchedBranches) }).subscribe({ next: () => this.done('Automações atualizadas.'), error: e => this.fail(e) });
  }
  sync() {
    if (!this.board) return;
    this.start(); this.github.sync(this.board.id).subscribe({ next: () => this.done('Sincronização agendada. Aguarde antes de atualizar o board.'), error: e => this.fail(e) });
  }
  importMembers() {
    if (!this.board) return;
    this.start(); const r = this.membersForm.getRawValue();
    this.members.importGithub(this.board.id, r.role, this.values(r.logins)).subscribe({ next: r => this.done(`${r.added} adicionados, ${r.invited} convidados, ${r.ignored} ignorados. Os membros têm acesso ao projeto inteiro.`), error: e => this.fail(e) });
  }
  deleteBoard() {
    if (!this.board || !confirm('Excluir este board? Arquive as tarefas ativas antes de continuar.')) return;
    this.start(); this.boards.delete(this.board.id).subscribe({ next: () => this.router.navigate(['/projects', this.board!.projectId]), error: e => this.fail(e) });
  }
  private values(text: string) { return text.split(',').map(v => v.trim()).filter(Boolean); }
  private start() { this.busy = true; this.error = null; this.message = null; }
  private done(message: string) { this.busy = false; this.message = message; }
  private fail(error: ApiError) { this.busy = false; this.error = error.message; }
}
