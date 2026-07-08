import { createFileRoute } from '@tanstack/react-router'
import { OrderDetailPage } from '@/pages/order-detail-page'

export const Route = createFileRoute('/_authenticated/orders/$id')({
<<<<<<< HEAD
  component: () => <OrderDetailPage />,
=======
  component: () => {
    return <OrderDetailPage />
  },
>>>>>>> 8a9702588ecd1b6bc3585995b89b00cd491f3c58
})
