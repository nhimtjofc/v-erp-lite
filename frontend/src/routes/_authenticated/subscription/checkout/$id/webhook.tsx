import { createFileRoute } from '@tanstack/react-router'
import { SubscriptionPaymentWebhookPage } from '@/pages/subscription-payment-webhook-page'

export const Route = createFileRoute('/_authenticated/subscription/checkout/$id/webhook')({
  component: SubscriptionPaymentWebhookPage,
})
