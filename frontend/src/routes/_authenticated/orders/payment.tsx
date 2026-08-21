import { createFileRoute } from '@tanstack/react-router'
import { PaymentProcessingPage } from '@/pages/payment-processing-page'

interface PaymentSearch {
  orderId: string
}

export const Route = createFileRoute('/_authenticated/orders/payment')({
  validateSearch: (search: Record<string, unknown>): PaymentSearch => ({
    orderId: typeof search.orderId === 'string' ? search.orderId : '',
  }),
  component: PaymentRoute,
})

function PaymentRoute() {
  const { orderId } = Route.useSearch()
  return <PaymentProcessingPage orderId={orderId} />
}