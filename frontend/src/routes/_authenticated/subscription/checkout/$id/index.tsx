import { createFileRoute } from '@tanstack/react-router'
import { SubscriptionPaymentMethodPage } from '@/pages/subscription-payment-method-page'

export const Route = createFileRoute('/_authenticated/subscription/checkout/$id/')({
  component: SubscriptionPaymentMethodPage,
})
