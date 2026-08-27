import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { ApiError } from '../../../core/models/api-error.model';
import { UserResponse } from '../../../core/models/auth.models';
import { passwordMatchValidator } from '../../../shared/validators/password-match.validator';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss'
})
export class RegisterComponent {

  registerForm: FormGroup;
  isLoading = false;
  errorMessage: string | null = null;
  registeredUser: UserResponse | null = null;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService
  ) {
    this.registerForm = this.fb.group(
      {
        username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(50), Validators.pattern(/^[a-zA-Z0-9_]+$/)]],
        email: ['', [Validators.required, Validators.email]],
        password: ['', [Validators.required, Validators.minLength(8), Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/)]],
        confirmPassword: ['', Validators.required],
        fullName: ['']
      },
      { validators: passwordMatchValidator() }
    );
  }

  get f() {
    return this.registerForm.controls;
  }

  onSubmit(): void {
    if (this.registerForm.invalid) {
      this.registerForm.markAllAsTouched();
      return;
    }

    this.isLoading = true;
    this.errorMessage = null;

    const { confirmPassword, ...rest } = this.registerForm.value;
    const request = { ...rest, confirmPassword };

    this.authService.register(request).subscribe({
      next: (response) => {
        this.isLoading = false;
        this.registeredUser = response.user;
        this.registerForm.reset();
      },
      error: (err: ApiError) => {
        this.isLoading = false;
        this.errorMessage = err.message;

        err.fieldErrors?.forEach(fieldError => {
          const control = this.registerForm.get(fieldError.field);
          if (control) {
            control.setErrors({ ...(control.errors ?? {}), server: fieldError.message });
          }
        });
      }
    });
  }
}
