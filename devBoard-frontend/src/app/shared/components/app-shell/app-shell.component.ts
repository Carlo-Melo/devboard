import { CommonModule } from '@angular/common';
import { Component, DestroyRef, HostListener, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { UserResponse } from '../../../core/models/auth.models';
import { A11yModule } from '@angular/cdk/a11y';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, RouterLink, A11yModule],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss'
})
export class AppShellComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  user: UserResponse | null = null;
  menuOpen = false;
  accountOpen = false;
  isMobile = window.innerWidth < 768;

  constructor(private auth: AuthService, public router: Router) {}

  ngOnInit(): void {
    this.auth.getMe().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({ next: user => this.user = user, error: () => {} });
  }

  get currentPath(): string { return this.router.url.split(/[?#]/)[0]; }
  get isHomeActive(): boolean { return this.currentPath === '/projects'; }
  get isProjectsActive(): boolean {
    const [, section, destination] = this.currentPath.split('/');
    return section === 'projects' && !!destination && destination !== 'new';
  }

  get section(): string {
    if (this.isHomeActive) return 'Home';
    if (this.currentPath === '/repositories') return 'Meus repositórios';
    if (this.currentPath.startsWith('/boards')) return 'Quadro Kanban';
    if (this.currentPath.startsWith('/tasks')) return 'Detalhe da tarefa';
    if (this.currentPath === '/projects/new') return 'Novo projeto';
    if (this.currentPath.endsWith('/edit')) return 'Editar projeto';
    if (this.currentPath.endsWith('/members')) return 'Membros do projeto';
    if (this.isProjectsActive && this.currentPath !== '/projects/list') return 'Detalhe do projeto';
    return 'Meus projetos';
  }

  @HostListener('document:keydown.escape')
  closeMenus(): void { this.menuOpen = false; this.accountOpen = false; }

  @HostListener('window:resize')
  onResize(): void { this.isMobile = window.innerWidth < 768; if (!this.isMobile) this.menuOpen = false; }

  logout(): void {
    this.auth.logout().subscribe({ next: () => this.finishLogout(), error: () => this.finishLogout() });
  }

  private finishLogout(): void {
    this.auth.removeToken();
    this.router.navigateByUrl('/login');
  }
}
