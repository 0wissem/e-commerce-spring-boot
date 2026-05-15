import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AsyncPipe } from '@angular/common';
import { AuthUseCase } from '../../../features/auth/application/auth.usecase';
import { CartService } from '../../../features/cart/application/cart.service';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, AsyncPipe],
  templateUrl: './navbar.component.html'
})
export class NavbarComponent {
  constructor(public auth: AuthUseCase, public cart: CartService) {}
}