import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { OrderApiService, CreateOrderRequest } from '../infrastructure/order-api.service';
import { Order } from '../domain/order.model';

@Injectable({ providedIn: 'root' })
export class CreateOrderUseCase {
  constructor(private api: OrderApiService) {}

  execute(request: CreateOrderRequest): Observable<Order> {
    return this.api.create(request);
  }
}