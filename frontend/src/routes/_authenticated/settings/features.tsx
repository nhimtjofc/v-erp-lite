import { createFileRoute } from '@tanstack/react-router'
import { FeaturesPage } from '@/pages/tenant-settings/features-page'

export const Route = createFileRoute('/_authenticated/settings/features')({
  component: FeaturesPage,
})
