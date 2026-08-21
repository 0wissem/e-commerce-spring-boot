import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { OrderApiService } from '../infrastructure/order-api.service';
import { CursorPage, Order, OrderPage } from '../domain/order.model';
import { AuthUseCase } from '../../auth/application/auth.usecase';

@Injectable({ providedIn: 'root' })
export class GetOrdersUseCase {
  constructor(private api: OrderApiService, private auth: AuthUseCase) {}

  /** @deprecated offset-paginated and NOT customer-scoped. Use history(). */
  execute(page = 0, size = 10): Observable<OrderPage> {
    return this.api.getMyOrders(page, size);
  }

  /**
   * The signed-in customer's order history, keyset-paginated.
   * Returns an empty page when there is no valid token rather than calling the API.
   */
  history(cursor: string | null = null, limit = 10): Observable<CursorPage<Order>> {
    const customerId = this.auth.getUserId();
    if (!customerId) {
      return of({ items: [], nextCursor: null, hasMore: false });
    }
    return this.api.getMyHistory(customerId, cursor, limit);
  }
}
