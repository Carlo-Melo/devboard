import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-github-callback',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './github-callback.component.html',
  styleUrl: '../auth-shared.scss'
})
export class GithubCallbackComponent implements OnInit {

  errorMessage: string | null = null;

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    const fragment = new URLSearchParams(window.location.hash.replace(/^#/, ''));
    const token = fragment.get('token');
    const returnUrl = fragment.get('returnUrl') ?? '/projects';

    if (!token) {
      this.errorMessage = 'Não foi possível concluir o login com o GitHub.';
      return;
    }

    this.authService.setToken(token);
    this.router.navigateByUrl(returnUrl);
  }
}
