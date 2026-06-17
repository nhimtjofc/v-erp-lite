import { createFileRoute } from '@tanstack/react-router'
import { ProfileSecurityPage } from '@/pages/profile-settings/security-page'

export const Route = createFileRoute('/_authenticated/profile/security')({
  component: ProfileSecurityPage,
})
