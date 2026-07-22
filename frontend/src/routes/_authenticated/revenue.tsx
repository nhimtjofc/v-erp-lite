import { createFileRoute } from '@tanstack/react-router'
import { RevenuePage } from '@/pages/revenue-page'

export const Route = createFileRoute('/_authenticated/revenue')({
  component: RevenuePage,
})
