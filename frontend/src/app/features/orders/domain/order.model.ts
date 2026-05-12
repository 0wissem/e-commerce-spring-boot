export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';

export interface OrderItem {
  id: string;
  productId: string;
  productName: string;
  quantity: number;
  unitPrice: number;
}

export interface Order {
  id: string;
  customerId: string;
  status: OrderStatus;
  totalPrice: number;
  items: OrderItem[];
}

export interface OrderPage {
  content: Order[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}