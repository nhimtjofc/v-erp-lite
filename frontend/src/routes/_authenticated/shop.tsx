import { createFileRoute } from '@tanstack/react-router'
import { ShopPage } from '@/pages/shop-page'

export const Route = createFileRoute('/_authenticated/shop')({
  component: ShopPage,
})
