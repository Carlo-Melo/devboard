import { CommonModule } from '@angular/common';
import { Component, HostListener, OnInit } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { UserResponse } from '../../../core/models/auth.models';
import { A11yModule } from '@angular/cdk/a11y';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, A11yModule],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss'
})
export class AppShellComponent implements OnInit {
  user: UserResponse | null = null;
  menuOpen = false;
  accountOpen = false;
  isMobile = window.innerWidth < 768;

  constructor(private auth: AuthService, public router: Router) {}

  ngOnInit(): void {
    this.auth.getMe().subscribe({ next: user => this.user = user, error: () => {} });
  }

  get section(): string {
    if (this.router.url.startsWith('/boards')) return 'Quadro Kanban';
    if (this.router.url.startsWith('/tasks')) return 'Detalhe da tarefa';
    if (this.router.url.startsWith('/projects/new')) return 'Novo projeto';
    return 'Projetos';
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
