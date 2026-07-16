import { createFileRoute } from '@tanstack/react-router'
import { WooCommercePage } from '@/pages/woocommerce-page'

export const Route = createFileRoute('/_authenticated/settings/woocommerce')({
  component: WooCommercePage,
})
