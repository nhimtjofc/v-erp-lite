import { createFileRoute } from '@tanstack/react-router'
import { SecurityPage } from '@/pages/tenant-settings/security-page'

export const Route = createFileRoute('/_authenticated/settings/security')({
  component: SecurityPage,
})
