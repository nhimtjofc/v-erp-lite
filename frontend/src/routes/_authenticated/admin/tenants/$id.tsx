import { createFileRoute } from '@tanstack/react-router'
import { TenantDetailPage } from '@/pages/admin/tenant-detail-page'

export const Route = createFileRoute('/_authenticated/admin/tenants/$id')({
  component: TenantDetailPage,
})
