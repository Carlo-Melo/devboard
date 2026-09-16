import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { MemberListComponent } from './member-list.component';
import { MemberService } from '../../../core/services/member.service';
import { ProjectService } from '../../../core/services/project.service';

describe('MemberListComponent', () => {
  let memberService: jasmine.SpyObj<MemberService>;

  beforeEach(async () => {
    memberService = jasmine.createSpyObj('MemberService', ['listInvites', 'inviteByEmail', 'createInviteLink', 'importGithub', 'updateRole', 'remove', 'leave']);
    memberService.listInvites.and.returnValue(of([]));

    const projectService = jasmine.createSpyObj<ProjectService>('ProjectService', ['getById']);
    projectService.getById.and.returnValue(of({
      id: 1, name: 'Projeto', owner: { id: 2, username: 'owner', email: 'owner@example.com', authProvider: 'TRADITIONAL', githubConnected: false },
      currentUserRole: 'ADMIN', currentUserOwner: true, watchedBranches: [], defaultBaseBranch: 'main', archived: false,
      members: [], boards: [], createdAt: '2026-01-01', updatedAt: '2026-01-01'
    } as any));

    await TestBed.configureTestingModule({
      imports: [MemberListComponent],
      providers: [
        { provide: MemberService, useValue: memberService },
        { provide: ProjectService, useValue: projectService },
        provideRouter([])
      ]
    })
      .overrideComponent(MemberListComponent, {
        set: {
          providers: [
            { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '1' } } } }
          ]
        }
      })
      .compileComponents();
  });

  it('loads the project and pending invites for an administrator', () => {
    const fixture = TestBed.createComponent(MemberListComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.project?.name).toBe('Projeto');
    expect(memberService.listInvites).toHaveBeenCalledWith(1, 'PENDING');
  });

  it('does not submit an invalid email invitation', () => {
    const fixture = TestBed.createComponent(MemberListComponent);
    fixture.detectChanges();

    fixture.componentInstance.invite();

    expect(memberService.inviteByEmail).not.toHaveBeenCalled();
  });

  it('submits a valid email invitation', () => {
    memberService.inviteByEmail.and.returnValue(of({} as any));
    const fixture = TestBed.createComponent(MemberListComponent);
    fixture.detectChanges();
    fixture.componentInstance.inviteForm.setValue({ email: 'new@example.com', role: 'DEVELOPER' });

    fixture.componentInstance.invite();

    expect(memberService.inviteByEmail).toHaveBeenCalledWith(1, { email: 'new@example.com', role: 'DEVELOPER' });
  });

  it('imports all GitHub collaborators with the selected role', () => {
    memberService.importGithub.and.returnValue(of({ added: 2, invited: 1, ignored: 0 }));
    const fixture = TestBed.createComponent(MemberListComponent);
    fixture.detectChanges();

    fixture.componentInstance.importGithub('VIEWER');

    expect(memberService.importGithub).toHaveBeenCalledWith(1, 'VIEWER');
    expect(fixture.componentInstance.successMessage).toContain('2 membro(s) adicionado(s)');
  });
});
