import { Observable } from 'rxjs';
import { AuthResponse, LoginRequest, RegisterRequest } from './auth.model';

export abstract class IAuthRepository {
  abstract login(request: LoginRequest): Observable<AuthResponse>;
  abstract register(request: RegisterRequest): Observable<AuthResponse>;
}