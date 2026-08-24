export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';

/**
 * The product as it was WHEN THE ORDER WAS PLACED, frozen server-side.
 * Fields added in snapshot v2 are nullable: orders placed before the enrichment
 * genuinely have no sku/brand/currency, and the API returns null rather than failing.
 */
export interface ProductSnapshot {
  version: number | null;
  name: string;
  sku: string | null;
  brand: string | null;
  price: number;
  currency: string | null;
  categories: { id: string; name: string }[];
}

export interface OrderItem {
  id: string;
  productId: string;
  productName: string;
  quantity: number;
  unitPrice: number;
  /** unitPrice * quantity, computed server-side so it can never disagree with the line. */
  subtotal: number;
  productSnapshot: ProductSnapshot | null;
}

export interface ShippingAddress {
  line1: string | null;
  line2: string | null;
  city: string | null;
  postalCode: string | null;
  country: string | null;
}

export interface Order {
  id: string;
  /** Human-readable reference (ORD-2026-A1B2C3D4) — quote this in support, not the UUID. */
  orderNumber: string;
  customerId: string;
  customerName: string;
  status: OrderStatus;
  totalPrice: number;
  currency: string;
  shippingAddress: ShippingAddress | null;
  createdAt: string;
  updatedAt: string;
  items: OrderItem[];
}

export interface OrderPage {
  content: Order[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/**
 * A keyset (cursor) page — deliberately NOT an OrderPage.
 *
 * There is no totalPages, because computing it needs a COUNT over the whole history,
 * which is the full scan keyset pagination exists to avoid. `nextCursor` is opaque:
 * hand it back verbatim to fetch the following page. Null means the end.
 */
export interface CursorPage<T> {
  items: T[];
  nextCursor: string | null;
  hasMore: boolean;
}
