import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export interface RegistrationRequest {
  photoData?: string|null;
  name: string;
  lastName: string;
  email: string;
  password: string;
  passwordConfirmation: string;
}

@Injectable({ providedIn: 'root' })
export class RegistrationService {
  private readonly http = inject(HttpClient);

  register(request: RegistrationRequest) {
    return this.http.post<{ id: string; name: string }>('/api/registrations', request);
  }
}
