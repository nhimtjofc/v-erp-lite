import { useMutation, useQuery } from '@tanstack/react-query'
import { getWallet, myWallet, transactions, updateMyBankDetails } from './gen/sdk.gen'
import type { WalletResponse, WalletTransactionResponse } from './gen/types.gen'

export type SellerWallet = WalletResponse
export type WalletTransaction = WalletTransactionResponse

export interface BankDetailsInput {
  bankId: string
  bankAccountNumber: string
  bankAccountHolder: string
  currency?: string
}

const TRANSACTIONS_PAGE_SIZE = 50

export function useSellerWalletQuery() {
  return useQuery({
    queryKey: ['seller-wallet'],
    queryFn: async () => {
      const pinnedId = import.meta.env.VITE_SELLER_WALLET_ID as string | undefined
      const response = pinnedId
        ? await getWallet({ path: { id: pinnedId }, throwOnError: true })
        : await myWallet({ throwOnError: true })
      return response.data ?? null
    },
  })
}

export function useWalletTransactionsQuery(walletId?: string) {
  return useQuery({
    queryKey: ['wallet-transactions', walletId],
    queryFn: async () => {
      const response = await transactions({
        path: { id: walletId! },
        query: { limit: TRANSACTIONS_PAGE_SIZE, offset: 0 },
        throwOnError: true,
      })
      return response.data ?? []
    },
    enabled: !!walletId,
  })
}

export function useUpdateBankDetailsMutation() {
  return useMutation({
    mutationFn: async (input: BankDetailsInput) => {
      const response = await updateMyBankDetails({
        query: { currency: input.currency },
        body: {
          bankId: input.bankId,
          bankAccountNumber: input.bankAccountNumber,
          bankAccountHolder: input.bankAccountHolder,
        },
        throwOnError: true,
      })
      return response.data
    },
  })
}
