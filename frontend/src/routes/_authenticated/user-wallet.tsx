import { UserWalletPage } from '@/pages/user-wallet-page'
import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/_authenticated/user-wallet')({
  component: UserWalletPage,
})
