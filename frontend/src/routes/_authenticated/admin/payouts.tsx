import { createFileRoute } from '@tanstack/react-router'
import { AdminPayoutsPage } from '@/pages/admin/admin-payouts-page'

export const Route = createFileRoute('/_authenticated/admin/payouts')({
  component: AdminPayoutsPage,
})
