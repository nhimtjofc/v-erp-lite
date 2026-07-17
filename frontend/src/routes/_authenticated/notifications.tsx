import { createFileRoute } from '@tanstack/react-router'
import { NotificationsPage } from '@/pages/notifications-page'

export const Route = createFileRoute('/_authenticated/notifications')({
  component: NotificationsPage,
})
