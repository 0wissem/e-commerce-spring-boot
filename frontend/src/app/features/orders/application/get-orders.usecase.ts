import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { OrderApiService } from '../infrastructure/order-api.service';
import { OrderPage } from '../domain/order.model';

@Injectable({ providedIn: 'root' })
export class GetOrdersUseCase {
  constructor(private api: OrderApiService) {}

  execute(page = 0, size = 10): Observable<OrderPage> {
    return this.api.getMyOrders(page, size);
  }
}