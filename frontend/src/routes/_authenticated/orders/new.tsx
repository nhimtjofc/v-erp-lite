import { createFileRoute } from '@tanstack/react-router'
import { CartCheckoutPage } from '../../../pages/cart-checkout-page'

export const Route = createFileRoute('/_authenticated/orders/new')({
  component: CartCheckoutPage,
})
