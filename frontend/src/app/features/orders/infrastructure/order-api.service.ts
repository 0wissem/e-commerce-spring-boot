import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { Order, OrderPage } from '../domain/order.model';

export interface CreateOrderRequest {
  items: { productId: string; quantity: number }[];
}

@Injectable({ providedIn: 'root' })
export class OrderApiService {
  private readonly base = `${environment.apiUrl}/api/orders`;

  constructor(private http: HttpClient) {}

  getMyOrders(page = 0, size = 10): Observable<OrderPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<any>(this.base, { params }).pipe(map(r => r.data));
  }

  getById(id: string): Observable<Order> {
    return this.http.get<any>(`${this.base}/${id}`).pipe(map(r => r.data));
  }

  create(request: CreateOrderRequest): Observable<Order> {
    return this.http.post<any>(this.base, request).pipe(map(r => r.data));
  }
}