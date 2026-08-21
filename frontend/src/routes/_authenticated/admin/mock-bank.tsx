import { createFileRoute } from '@tanstack/react-router'
import { MockBankDashboardPage } from '@/pages/admin/mock-bank-dashboard-page'

export const Route = createFileRoute('/_authenticated/admin/mock-bank')({
  component: MockBankDashboardPage,
})
