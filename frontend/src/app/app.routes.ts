import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'products', pathMatch: 'full' },
  {
    path: 'products',
    loadComponent: () =>
      import('./features/products/ui/product-list/product-list.component').then(m => m.ProductListComponent)
  },
  {
    path: 'products/:id',
    loadComponent: () =>
      import('./features/products/ui/product-detail/product-detail.component').then(m => m.ProductDetailComponent)
  },
  {
    path: 'auth/login',
    loadComponent: () =>
      import('./features/auth/ui/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: 'auth/register',
    loadComponent: () =>
      import('./features/auth/ui/register/register.component').then(m => m.RegisterComponent)
  },
  {
    path: 'orders',
    loadComponent: () =>
      import('./features/orders/ui/order-list/order-list.component').then(m => m.OrderListComponent)
  }
];
