import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { CursorPage, Order, OrderPage, ShippingAddress } from '../domain/order.model';

export interface CreateOrderRequest {
  items: { productId: string; quantity: number }[];
  shippingAddress?: ShippingAddress;
}

@Injectable({ providedIn: 'root' })
export class OrderApiService {
  private readonly base = `${environment.apiUrl}/api/orders`;

  constructor(private http: HttpClient) {}

  /** @deprecated returns EVERY order, not just this customer's. Use getMyHistory. */
  getMyOrders(page = 0, size = 10): Observable<OrderPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<any>(this.base, { params }).pipe(map(r => r.data));
  }

  /**
   * Keyset-paginated history for ONE customer, newest first.
   * Pass the previous response's nextCursor to continue; omit it for the first page.
   */
  getMyHistory(customerId: string, cursor: string | null, limit = 10): Observable<CursorPage<Order>> {
    let params = new HttpParams().set('limit', limit);
    if (cursor) params = params.set('cursor', cursor);
    return this.http
      .get<any>(`${this.base}/customer/${customerId}/history`, { params })
      .pipe(map(r => r.data));
  }

  getById(id: string): Observable<Order> {
    return this.http.get<any>(`${this.base}/${id}`).pipe(map(r => r.data));
  }

  create(request: CreateOrderRequest): Observable<Order> {
    return this.http.post<any>(this.base, request).pipe(map(r => r.data));
  }
}
