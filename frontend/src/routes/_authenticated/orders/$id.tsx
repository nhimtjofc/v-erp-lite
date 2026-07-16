import { createFileRoute } from '@tanstack/react-router'
import { OrderDetailPage } from '@/pages/order-detail-page'

export const Route = createFileRoute('/_authenticated/orders/$id')({
  component: () => <OrderDetailPage />,
})
