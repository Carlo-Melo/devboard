import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { ApiError } from '../../../core/models/api-error.model';
import { passwordMatchValidator } from '../../../shared/validators/password-match.validator';

type ViewState = 'loading' | 'invalid' | 'form' | 'done';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './reset-password.component.html',
  styleUrl: './reset-password.component.scss'
})
export class ResetPasswordComponent implements OnInit {

  form: FormGroup;
  state: ViewState = 'loading';
  isLoading = false;
  errorMessage: string | null = null;
  private token = '';

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private route: ActivatedRoute
  ) {
    this.form = this.fb.group(
      {
        newPassword: ['', [Validators.required, Validators.minLength(8), Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/)]],
        confirmPassword: ['', Validators.required]
      },
      { validators: passwordMatchValidator('newPassword', 'confirmPassword') }
    );
  }

  get f() {
    return this.form.controls;
  }

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token') ?? '';

    if (!this.token) {
      this.state = 'invalid';
      return;
    }

    this.authService.validateResetToken(this.token).subscribe({
      next: () => (this.state = 'form'),
      error: () => (this.state = 'invalid')
    });
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isLoading = true;
    this.errorMessage = null;

    this.authService.resetPassword({ token: this.token, ...this.form.value }).subscribe({
      next: () => {
        this.isLoading = false;
        this.state = 'done';
      },
      error: (err: ApiError) => {
        this.isLoading = false;
        this.errorMessage = err.message;
      }
    });
  }
}
