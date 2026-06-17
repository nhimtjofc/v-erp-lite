import { createFileRoute } from '@tanstack/react-router'
import { BusinessPage } from '@/pages/business-page'

export const Route = createFileRoute('/_authenticated/business')({
  component: BusinessPage,
})
