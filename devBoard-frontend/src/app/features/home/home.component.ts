import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ProjectCardComponent } from '../projects/project-card/project-card.component';
import { RouterLink } from '@angular/router';
import { BehaviorSubject, catchError, forkJoin, of, switchMap } from 'rxjs';
import { ProjectService } from '../../core/services/project.service';
import { AuthService } from '../../core/services/auth.service';
import { DashboardService } from '../../core/services/dashboard.service';
import { ProjectSummaryResponse } from '../../core/models/project.models';
import { UserResponse } from '../../core/models/auth.models';
import { DashboardActivity, DashboardSummary, DashboardTask } from '../../core/models/dashboard.models';
import { GithubRepoResponse } from '../../core/models/github.models';
import { GithubService } from '../../core/services/github.service';
import { RepositoryCardComponent } from '../repositories/repository-card/repository-card.component';
import { LinkRepositoryDialogComponent } from '../repositories/link-repository-dialog/link-repository-dialog.component';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({ selector: 'app-home', standalone: true, imports: [CommonModule, RouterLink, ProjectCardComponent, RepositoryCardComponent, LinkRepositoryDialogComponent], templateUrl: './home.component.html', styleUrl: './home.component.scss' })
export class HomeComponent implements OnInit {
  private destroyRef=inject(DestroyRef);
  projects: ProjectSummaryResponse[] = []; filterProjects: ProjectSummaryResponse[] = []; currentUser: UserResponse | null = null;
  isLoading = true; dashboardLoading = true; errorMessage: string | null = null; totalElements = 0; readonly pageSize = 4;
  selectedProject: number | null = null; summary: DashboardSummary | null = null; tasks: DashboardTask[] = []; activities: DashboardActivity[] = [];
  taskPage = 0; taskPages = 0; activityPage = 0; activityPages = 0; summaryError = false; taskError = false; activityError = false; refreshedAt: Date | null = null;
  repositories:GithubRepoResponse[]=[]; repositoriesLoading=true; repositoriesError=false; activeRepo:GithubRepoResponse|null=null; private linkTrigger:HTMLElement|null=null;
  private readonly filter$ = new BehaviorSubject<number | null>(null);
  constructor(private projectService: ProjectService, private authService: AuthService, private dashboard: DashboardService, private github:GithubService) {}
  ngOnInit(): void {
    this.loadProjects(); this.authService.getMe().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: user => {this.currentUser = user;if(user.githubConnected)this.loadRepositories();else this.repositoriesLoading=false;}, error: () => {this.repositoriesLoading=false;} });
    this.filter$.pipe(switchMap(projectId => {
      this.dashboardLoading = true; this.summaryError = this.taskError = this.activityError = false;
      return forkJoin({ summary: this.dashboard.summary(projectId).pipe(catchError(() => { this.summaryError = true; return of(null); })), tasks: this.dashboard.tasks(projectId).pipe(catchError(() => { this.taskError = true; return of(null); })), activities: this.dashboard.activities(projectId).pipe(catchError(() => { this.activityError = true; return of(null); })) });
    })).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(result => { this.summary = result.summary; this.tasks = result.tasks?.content ?? []; this.taskPages = result.tasks?.totalPages ?? 0; this.activities = result.activities?.content ?? []; this.activityPages = result.activities?.totalPages ?? 0; this.taskPage = this.activityPage = 0; this.dashboardLoading = false; this.refreshedAt = new Date(); });
  }
  loadRepositories():void {this.repositoriesLoading=true;this.repositoriesError=false;this.github.availableRepositories('',0,4).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({next:r=>{this.repositories=r.content;this.repositoriesLoading=false;},error:()=>{this.repositoriesError=true;this.repositoriesLoading=false;}});}
  openRepositoryLink(repo:GithubRepoResponse,event:MouseEvent):void{this.linkTrigger=event.currentTarget as HTMLElement;this.activeRepo=repo;}
  closeRepositoryDialog():void{this.activeRepo=null;setTimeout(()=>this.linkTrigger?.focus(),0);}
  repositoryLinked(id:number):void{this.repositories=this.repositories.map(r=>r.id===id?{...r,linked:true}:r);this.projectService.list(false,0,this.pageSize).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({next:r=>{this.projects=r.content;this.totalElements=r.totalElements;}});this.projectService.list(false,0,100).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({next:r=>this.filterProjects=r.content});}
  loadProjects(): void {
    this.isLoading = true; this.errorMessage = null;
    this.projectService.list(false, 0, 100).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: r => this.filterProjects = r.content, error: () => this.filterProjects = this.projects });
    this.projectService.list(false, 0, this.pageSize).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: r => { this.projects = r.content;  this.totalElements = r.totalElements; this.isLoading = false; }, error: () => { this.errorMessage = 'Não foi possível carregar os projetos.'; this.isLoading = false; } });
  }
  refresh(): void { this.loadProjects(); this.filter$.next(this.selectedProject); if(this.currentUser?.githubConnected)this.loadRepositories(); }
  onProjectChange(value: string): void { this.selectedProject = value ? Number(value) : null; this.filter$.next(this.selectedProject); }
  loadTaskPage(next: boolean): void { this.taskPage += next ? 1 : -1; this.dashboard.tasks(this.selectedProject, this.taskPage).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: r => { this.tasks = r.content; this.taskPages = r.totalPages; }, error: () => this.taskError = true }); }
  loadActivityPage(next: boolean): void { this.activityPage += next ? 1 : -1; this.dashboard.activities(this.selectedProject, this.activityPage).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: r => { this.activities = r.content; this.activityPages = r.totalPages; }, error: () => this.activityError = true }); }
  retryTasks(): void { this.taskError = false; this.dashboard.tasks(this.selectedProject, this.taskPage).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: r => { this.tasks = r.content; this.taskPages = r.totalPages; }, error: () => this.taskError = true }); }
  retryActivities(): void { this.activityError = false; this.dashboard.activities(this.selectedProject, this.activityPage).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: r => { this.activities = r.content; this.activityPages = r.totalPages; }, error: () => this.activityError = true }); }
  stageLabel(role: string): string { return ({ BACKLOG:'Backlog', TODO:'A fazer', IN_PROGRESS:'Em andamento', IN_REVIEW:'Em revisão', DONE:'Concluídas', NONE:'Sem etapa definida' } as Record<string,string>)[role] ?? 'Sem etapa definida'; }
  priorityLabel(priority: string): string { return ({ LOW:'Baixa', MEDIUM:'Média', HIGH:'Alta', URGENT:'Urgente' } as Record<string,string>)[priority] ?? priority; }
  stageEntries(): { label: string; value: number; color: string }[] { const labels: Record<string,string> = { BACKLOG:'Backlog', TODO:'A fazer', IN_PROGRESS:'Em andamento', IN_REVIEW:'Em revisão', DONE:'Concluídas', NONE:'Sem etapa definida' }; const colors: Record<string,string> = { BACKLOG:'#82928c', TODO:'#78aef4', IN_PROGRESS:'#70d6bd', IN_REVIEW:'#c19bf2', DONE:'#76c893', NONE:'#c3ab76' }; return Object.entries(this.summary?.tasksByStage ?? {}).map(([key,value]) => ({ label:labels[key] ?? key, value, color:colors[key] ?? '#c3ab76' })).filter(x => x.value > 0); }
  get stageTotal(): number { return this.stageEntries().reduce((n,x) => n+x.value,0); }
  get stageGradient(): string { const values=this.stageEntries(); const total=this.stageTotal; if(!total)return '#26332f 0 100%'; let current=0; return `conic-gradient(${values.map(v=>{const from=current;current+=v.value/total*100;return `${v.color} ${from}% ${current}%`;}).join(',')})`; }
  priorityEntries(): { key:string; label:string; value:number; color:string }[] { return [['LOW','Baixa','#7da7a0'],['MEDIUM','Média','#7ca8e0'],['HIGH','Alta','#dca863'],['URGENT','Urgente','#d87b7b']].map(([key,label,color])=>({key,label,color,value:this.summary?.pendingByPriority[key]??0})); }
  priorityMax(): number { return Math.max(1,...this.priorityEntries().map(x=>x.value)); }
  isOverdue(task: DashboardTask): boolean { return !!task.dueDate && task.dueDate < this.today; }
  get today(): string { const n=new Date(); return `${n.getFullYear()}-${String(n.getMonth()+1).padStart(2,'0')}-${String(n.getDate()).padStart(2,'0')}`; }
  activityAction(type:string):string { return ({CREATED:'criou a tarefa',MOVED:'moveu a tarefa',COMMENTED:'comentou na tarefa',ASSIGNEE_CHANGED:'alterou o responsável',COMMIT_RECEIVED:'recebeu um commit',PR_OPENED:'abriu um pull request',PR_MERGED:'integrou um pull request',PR_CLOSED:'fechou um pull request',GITHUB_ISSUE_LINKED:'vinculou uma issue',BRANCH_CREATED:'criou uma branch',ARCHIVED:'arquivou a tarefa'} as Record<string,string>)[type]??'atualizou a tarefa'; }
}
