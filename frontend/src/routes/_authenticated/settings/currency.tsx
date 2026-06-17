import { createFileRoute } from '@tanstack/react-router'
import { CurrencyPage } from '@/pages/tenant-settings/currency-page'

export const Route = createFileRoute('/_authenticated/settings/currency')({
  component: CurrencyPage,
})
