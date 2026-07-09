import { createFileRoute } from '@tanstack/react-router'
import { VouchersPage } from '../../pages/vouchers-page'

export const Route = createFileRoute('/_authenticated/vouchers')({
  component: VouchersPage,
})
