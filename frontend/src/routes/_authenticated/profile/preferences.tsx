import { createFileRoute } from '@tanstack/react-router'
import { PreferencesPage } from '@/pages/profile-settings/preferences-page'

export const Route = createFileRoute('/_authenticated/profile/preferences')({
  component: PreferencesPage,
})
