import { createFileRoute } from '@tanstack/react-router'
import { CustomerDetailPage } from '@/pages/customer-detail-page'

export const Route = createFileRoute('/_authenticated/customers/$id')({
  component: CustomerDetailPage,
})
