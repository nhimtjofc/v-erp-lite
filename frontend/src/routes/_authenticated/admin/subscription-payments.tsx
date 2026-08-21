import { createFileRoute } from '@tanstack/react-router'
import { AdminSubscriptionPaymentsPage } from '@/pages/admin/admin-subscription-payments-page'

export const Route = createFileRoute('/_authenticated/admin/subscription-payments')({
  component: AdminSubscriptionPaymentsPage,
})
