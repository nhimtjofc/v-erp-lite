import { createFileRoute } from '@tanstack/react-router'
import { BusinessPage } from '@/pages/business-page'

export const Route = createFileRoute('/_authenticated/businesses')({
  component: BusinessPage,
})
