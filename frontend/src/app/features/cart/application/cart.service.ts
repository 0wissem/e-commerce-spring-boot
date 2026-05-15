import { Injectable } from '@angular/core';
import { BehaviorSubject, map } from 'rxjs';
import { Cart, CartItem } from '../domain/cart.model';

@Injectable({ providedIn: 'root' })
export class CartService {
  private cartSubject = new BehaviorSubject<Cart>({ items: [] });
  cart$ = this.cartSubject.asObservable();
  itemCount$ = this.cart$.pipe(map(c => c.items.reduce((sum, i) => sum + i.quantity, 0)));
  total$ = this.cart$.pipe(map(c => c.items.reduce((sum, i) => sum + i.unitPrice * i.quantity, 0)));

  addItem(item: Omit<CartItem, 'quantity'>, quantity = 1) {
    const cart = this.cartSubject.value;
    const existing = cart.items.find(i => i.productId === item.productId);
    let items: CartItem[];
    if (existing) {
      items = cart.items.map(i =>
        i.productId === item.productId ? { ...i, quantity: i.quantity + quantity } : i
      );
    } else {
      items = [...cart.items, { ...item, quantity }];
    }
    this.cartSubject.next({ items });
  }

  updateQuantity(productId: string, quantity: number) {
    if (quantity <= 0) { this.removeItem(productId); return; }
    const items = this.cartSubject.value.items.map(i =>
      i.productId === productId ? { ...i, quantity } : i
    );
    this.cartSubject.next({ items });
  }

  removeItem(productId: string) {
    const items = this.cartSubject.value.items.filter(i => i.productId !== productId);
    this.cartSubject.next({ items });
  }

  clear() { this.cartSubject.next({ items: [] }); }

  getItems(): CartItem[] { return this.cartSubject.value.items; }
}