export interface Category {
  id: string;
  name: string;
}

export interface Product {
  id: string;
  /** Business identifier (SKU-XXXXXXXXXX), distinct from the surrogate id. */
  sku: string;
  name: string;
  /** Nullable on the API — products predating the enrichment have no brand. */
  brand: string | null;
  description: string | null;
  price: number;
  finalPrice: number;
  /** ISO-4217 alpha-3, e.g. "EUR". An amount without a currency is not money. */
  currency: string;
  stockQuantity: number;
  createdAt: string;
  updatedAt: string;
  categories: Category[];
}

export interface ProductPage {
  content: Product[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface ProductSearchParams {
  query?: string;
  minPrice?: number;
  maxPrice?: number;
  /** Exact-match facet, backed by idx_products_brand. */
  brand?: string;
  inStock?: boolean;
  page: number;
  size: number;
}
