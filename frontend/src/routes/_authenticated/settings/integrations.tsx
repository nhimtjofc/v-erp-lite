import { createFileRoute } from '@tanstack/react-router'
import { IntegrationsPage } from '@/pages/tenant-settings/integrations-page'

export const Route = createFileRoute('/_authenticated/settings/integrations')({
  component: IntegrationsPage,
})
