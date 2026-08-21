import { createFileRoute } from '@tanstack/react-router'
import { SubscriptionPage } from '@/pages/subscription-page'

export const Route = createFileRoute('/_authenticated/subscription/')({
  component: SubscriptionPage,
})
