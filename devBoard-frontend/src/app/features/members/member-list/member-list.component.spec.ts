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
    memberService = jasmine.createSpyObj('MemberService', ['list', 'listInvites', 'inviteByEmail', 'createInviteLink', 'importGithub', 'updateRole', 'remove', 'leave']);
    memberService.list.and.returnValue(of([]));
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
    expect(memberService.list).toHaveBeenCalledWith(1);
    expect(memberService.listInvites).toHaveBeenCalledWith(1, 'PENDING');
  });

  it('renders every member returned by the members endpoint', () => {
    memberService.list.and.returnValue(of([
      {
        id: 5,
        projectId: 1,
        user: { id: 8, username: 'convidado', email: 'convidado@example.com', fullName: 'Pessoa Convidada', authProvider: 'TRADITIONAL', githubConnected: false },
        role: 'DEVELOPER'
      }
    ] as any));
    const fixture = TestBed.createComponent(MemberListComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.members.length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('Pessoa Convidada');
    expect(fixture.nativeElement.textContent).toContain('convidado@example.com');
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

  it('shows a generated invitation link with a copy action', () => {
    const inviteLink = 'http://localhost:4200/invites/token-123';
    memberService.createInviteLink.and.returnValue(of({ acceptanceUrl: inviteLink } as any));
    const fixture = TestBed.createComponent(MemberListComponent);
    fixture.detectChanges();

    fixture.componentInstance.generateLink('DEVELOPER');
    fixture.detectChanges();

    expect(fixture.componentInstance.generatedInviteLink).toBe(inviteLink);
    expect(fixture.nativeElement.querySelector('#generated-invite-link').value).toBe(inviteLink);
    expect(fixture.nativeElement.querySelector('.generated-invite-link button').textContent).toContain('Copiar link');
  });

  it('copies the generated invitation link to the clipboard', async () => {
    const writeText = jasmine.createSpy('writeText').and.resolveTo();
    const originalClipboard = Object.getOwnPropertyDescriptor(navigator, 'clipboard');
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } });
    const fixture = TestBed.createComponent(MemberListComponent);
    const component = fixture.componentInstance;
    component.generatedInviteLink = 'http://localhost:4200/invites/token-123';

    try {
      await component.copyGeneratedInviteLink();

      expect(writeText).toHaveBeenCalledWith('http://localhost:4200/invites/token-123');
      expect(component.linkCopyMessage).toContain('copiado');
    } finally {
      if (originalClipboard) {
        Object.defineProperty(navigator, 'clipboard', originalClipboard);
      } else {
        delete (navigator as { clipboard?: Clipboard }).clipboard;
      }
    }
  });
});
