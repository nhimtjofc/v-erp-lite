import { createFileRoute } from '@tanstack/react-router'
import { OrderDetailPage } from '@/pages/order-detail-page'

export const Route = createFileRoute('/_authenticated/orders/$id')({
<<<<<<< HEAD
  component: () => <OrderDetailPage />,
=======
  component: () => {
    return <OrderDetailPage />
  },
>>>>>>> 071fbf0d4336084f5ba6ada3f2e1628e362b054f
})
