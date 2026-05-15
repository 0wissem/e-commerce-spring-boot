export interface CartItem {
  productId: string;
  productName: string;
  unitPrice: number;
  quantity: number;
}

export interface Cart {
  items: CartItem[];
}