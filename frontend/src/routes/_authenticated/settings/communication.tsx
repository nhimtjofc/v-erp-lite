import { createFileRoute } from '@tanstack/react-router'
import { CommunicationPage } from '@/pages/tenant-settings/communication-page'

export const Route = createFileRoute('/_authenticated/settings/communication')({
  component: CommunicationPage,
})
