import { createFileRoute } from '@tanstack/react-router'
import { TenantsPage } from '@/pages/admin/tenants-page'

export const Route = createFileRoute('/_authenticated/admin/tenants/')({
  component: TenantsPage,
})
