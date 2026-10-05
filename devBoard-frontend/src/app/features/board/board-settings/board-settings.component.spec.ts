import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { BoardSettingsComponent } from './board-settings.component';
import { BoardService } from '../../../core/services/board.service';
import { GithubService } from '../../../core/services/github.service';
import { MemberService } from '../../../core/services/member.service';
import { ProjectService } from '../../../core/services/project.service';
describe('BoardSettingsComponent', () => {
  let github: jasmine.SpyObj<GithubService>;
  let boards: jasmine.SpyObj<BoardService>;
  let members: jasmine.SpyObj<MemberService>;
  let projects: jasmine.SpyObj<ProjectService>;
  const board = { id: 7, projectId: 2, name: 'Backend', defaultBoard: true, columns: [], archived: false, watchedBranches: [], defaultBaseBranch: 'main', githubReauthRequired: false, createdAt: '', updatedAt: '' };
  beforeEach(async () => {
    github = jasmine.createSpyObj('GithubService', ['settings', 'repositories', 'link', 'unlink', 'sync', 'update']);
    boards = jasmine.createSpyObj('BoardService', ['update', 'delete']);
    members = jasmine.createSpyObj('MemberService', ['importGithub']);
    projects = jasmine.createSpyObj('ProjectService', ['getById']);
    github.settings.and.returnValue(of({ board, moveOnCommit: true, moveOnPrOpen: true, moveOnPrMerge: true, importIssues: true, closeIssueOnDone: true, branchPattern: 'feature/task-{id}-{title}' }));
    projects.getById.and.returnValue(of({ currentUserRole: 'ADMIN' } as any));
    await TestBed.configureTestingModule({ imports: [BoardSettingsComponent], providers: [
      provideRouter([]), { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '7' } } } },
      { provide: GithubService, useValue: github }, { provide: BoardService, useValue: boards },
      { provide: MemberService, useValue: members }, { provide: ProjectService, useValue: projects }
    ] }).compileComponents();
  });
  function create() { const f = TestBed.createComponent(BoardSettingsComponent); f.detectChanges(); return f; }
  it('blocks archived settings and offers a return to the project', () => {
    github.settings.and.returnValue(of({ board: { ...board, archived: true }, moveOnCommit: true, moveOnPrOpen: true, moveOnPrMerge: true, importIssues: true, closeIssueOnDone: true, branchPattern: 'feature/task-{id}-{title}' }));
    const fixture = create();
    expect(fixture.nativeElement.textContent).toContain('Board arquivado');
    expect(fixture.nativeElement.querySelector('a[href="/projects/2"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
    expect(projects.getById).not.toHaveBeenCalled();
  });
  it('shows the default indicator independently of the board name', () => {
    const f = create(); expect(f.nativeElement.textContent).toContain('Backend'); expect(f.nativeElement.textContent).toContain('Padrão');
  });
  it('renames the board while preserving its default flag', () => {
    boards.update.and.returnValue(of({ ...board, name: 'Infraestrutura' }));
    const f = create(); f.componentInstance.details.controls.name.setValue('Infraestrutura'); f.componentInstance.saveBoard();
    expect(boards.update).toHaveBeenCalledWith(7, { name: 'Infraestrutura', description: '', defaultBoard: true });
  });
  it('imports members from the selected board rather than the project ID', () => {
    members.importGithub.and.returnValue(of({ added: 1, invited: 0, ignored: 0 }));
    const f = create(); f.componentInstance.importMembers();
    expect(members.importGithub).toHaveBeenCalledWith(7, 'DEVELOPER', []);
    expect(f.componentInstance.message).toContain('projeto inteiro');
  });
  it('does not offer management to viewers', () => {
    projects.getById.and.returnValue(of({ currentUserRole: 'VIEWER' } as any));
    const f = create(); expect(f.componentInstance.canManage).toBeFalse();
    expect(f.nativeElement.textContent).not.toContain('Excluir board');
  });
  it('displays a repository conflict returned by the backend', () => {
    github.link.and.returnValue(throwError(() => ({ status: 409, message: 'Repositório já vinculado' })));
    const f = create(); f.componentInstance.linkForm.controls.repositoryId.setValue(123); f.componentInstance.link();
    expect(f.componentInstance.error).toBe('Repositório já vinculado'); expect(f.componentInstance.busy).toBeFalse();
  });
});
