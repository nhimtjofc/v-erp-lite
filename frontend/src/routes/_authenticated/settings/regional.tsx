import { createFileRoute } from '@tanstack/react-router'
import { RegionalPage } from '@/pages/tenant-settings/regional-page'

export const Route = createFileRoute('/_authenticated/settings/regional')({
  component: RegionalPage,
})
