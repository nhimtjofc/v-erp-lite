import { createFileRoute } from '@tanstack/react-router'
import { PersonalInfoPage } from '@/pages/profile-settings/personal-info-page'

export const Route = createFileRoute('/_authenticated/profile/')({
  component: PersonalInfoPage,
})
