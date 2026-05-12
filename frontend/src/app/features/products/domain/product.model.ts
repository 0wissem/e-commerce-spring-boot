export interface Category {
  id: string;
  name: string;
}

export interface Product {
  id: string;
  name: string;
  price: number;
  finalPrice: number;
  stockQuantity: number;
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
  inStock?: boolean;
  page: number;
  size: number;
}