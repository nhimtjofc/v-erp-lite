import { createFileRoute } from '@tanstack/react-router'
import { SellerWalletPage } from '@/pages/seller-wallet-page'

export const Route = createFileRoute('/_authenticated/wallet')({
  component: SellerWalletPage,
})
