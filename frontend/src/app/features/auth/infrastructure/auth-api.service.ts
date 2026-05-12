import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { IAuthRepository } from '../domain/auth.repository';
import { AuthResponse, LoginRequest, RegisterRequest } from '../domain/auth.model';

@Injectable({ providedIn: 'root' })
export class AuthApiService implements IAuthRepository {
  private readonly base = `${environment.apiUrl}/api/auth`;

  constructor(private http: HttpClient) {}

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<any>(`${this.base}/login`, request).pipe(map(r => r.data));
  }

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<any>(`${this.base}/register`, request).pipe(map(r => r.data));
  }
}