import { createFileRoute } from '@tanstack/react-router'
import { CustomerFormPage } from '@/pages/customer-form-page'

export const Route = createFileRoute('/_authenticated/customers/new')({
  component: () => <CustomerFormPage />,
})
