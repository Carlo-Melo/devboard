import { AbstractControl, ValidationErrors } from '@angular/forms';

export function passwordMatchValidator(group: AbstractControl): ValidationErrors | null {
  const confirmControl = group.get('confirmPassword');
  if (!confirmControl) {
    return null;
  }

  const password = group.get('password')?.value;
  const confirmPassword = confirmControl.value;
  const { passwordMismatch, ...otherErrors } = confirmControl.errors ?? {};

  if (password === confirmPassword) {
    confirmControl.setErrors(Object.keys(otherErrors).length ? otherErrors : null);
    return null;
  }

  confirmControl.setErrors({ ...otherErrors, passwordMismatch: true });
  return { passwordMismatch: true };
}
