import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { InviteAcceptComponent } from './invite-accept.component';
import { MemberService } from '../../../core/services/member.service';
import { AuthService } from '../../../core/services/auth.service';

describe('InviteAcceptComponent', () => {
  let memberService: jasmine.SpyObj<MemberService>;
  let authService: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    memberService = jasmine.createSpyObj('MemberService', ['getPublicInvite', 'acceptInvite']);
    memberService.getPublicInvite.and.returnValue(of({ projectName: 'Projeto', inviterName: 'Ana', role: 'DEVELOPER', expiresAt: '2026-02-01' }));
    authService = jasmine.createSpyObj('AuthService', ['isAuthenticated']);

    await TestBed.configureTestingModule({
      imports: [InviteAcceptComponent],
      providers: [
        { provide: MemberService, useValue: memberService },
        { provide: AuthService, useValue: authService },
        provideRouter([])
      ]
    })
      .overrideComponent(InviteAcceptComponent, {
        set: {
          providers: [
            { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'token-123' } } } }
          ]
        }
      })
      .compileComponents();
  });

  it('loads the public invitation context', () => {
    const fixture = TestBed.createComponent(InviteAcceptComponent);
    fixture.detectChanges();

    expect(memberService.getPublicInvite).toHaveBeenCalledWith('token-123');
    expect(fixture.componentInstance.invite?.projectName).toBe('Projeto');
  });

  it('sends an unauthenticated user to login preserving the invitation URL', () => {
    authService.isAuthenticated.and.returnValue(false);
    const router = TestBed.inject(Router);
    const navigate = spyOn(router, 'navigate');
    const fixture = TestBed.createComponent(InviteAcceptComponent);
    fixture.detectChanges();

    fixture.componentInstance.accept();

    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/invites/token-123' } });
  });

  it('accepts the invite and opens its project for an authenticated user', () => {
    authService.isAuthenticated.and.returnValue(true);
    memberService.acceptInvite.and.returnValue(of({ projectId: 7 } as any));
    const router = TestBed.inject(Router);
    const navigate = spyOn(router, 'navigateByUrl');
    const fixture = TestBed.createComponent(InviteAcceptComponent);
    fixture.detectChanges();

    fixture.componentInstance.accept();

    expect(memberService.acceptInvite).toHaveBeenCalledWith('token-123');
    expect(navigate).toHaveBeenCalledWith('/projects/7');
  });
});
