import { AbstractControl, ValidationErrors } from '@angular/forms';

export function matchingPasswords(control: AbstractControl): ValidationErrors | null {
  return control.get('password')?.value === control.get('passwordConfirmation')?.value
    ? null
    : { mismatch: true };
}
