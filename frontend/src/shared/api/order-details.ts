import { useQueries, useQuery } from '@tanstack/react-query'
import {
  getEscrowRecords,
  getRefunds,
  listOrderPaymentIntents,
  listTransactions,
} from './gen/sdk.gen'
import type {
  EscrowRecord as EscrowRecordEntity,
  PaymentIntent,
  PaymentRefund as PaymentRefundEntity,
  PaymentTransaction,
} from './gen/types.gen'

export type EscrowRecord = EscrowRecordEntity
export type PaymentRefund = PaymentRefundEntity

export interface OrderPaymentAttempt {
  intent: PaymentIntent
  transactions: PaymentTransaction[]
}

export function useOrderPaymentActivityQuery(orderId: string, enabled = true) {
  const intentsQuery = useQuery({
    queryKey: ['order-payment-intents', orderId],
    queryFn: async () => {
      const response = await listOrderPaymentIntents({
        path: { orderId },
        throwOnError: true,
      })
      return response.data ?? []
    },
    enabled: enabled && Boolean(orderId),
  })

  const intents = intentsQuery.data ?? []
  const transactionQueries = useQueries({
    queries: intents.map((intent) => ({
      queryKey: ['payment-transactions', intent.id],
      queryFn: async () => {
        const response = await listTransactions({
          path: { id: intent.id! },
          throwOnError: true,
        })
        return response.data ?? []
      },
      enabled: enabled && Boolean(intent.id),
    })),
  })

  const data: OrderPaymentAttempt[] = intents.map((intent, index) => ({
    intent,
    transactions: transactionQueries[index]?.data ?? [],
  }))

  return {
    data,
    isLoading: intentsQuery.isLoading || transactionQueries.some((query) => query.isLoading),
    isFetching: intentsQuery.isFetching || transactionQueries.some((query) => query.isFetching),
    isError: intentsQuery.isError || transactionQueries.some((query) => query.isError),
    refetch: async () => {
      await intentsQuery.refetch()
      await Promise.all(transactionQueries.map((query) => query.refetch()))
    },
  }
}

export function useOrderEscrowQuery(orderId: string) {
  return useQuery({
    queryKey: ['order-escrow', orderId],
    queryFn: async () => {
      const response = await getEscrowRecords({
        path: { id: orderId },
        throwOnError: true,
      })
      return response.data ?? []
    },
    enabled: Boolean(orderId),
  })
}

export function useOrderRefundsQuery(orderId: string) {
  return useQuery({
    queryKey: ['order-refunds', orderId],
    queryFn: async () => {
      const response = await getRefunds({
        path: { id: orderId },
        throwOnError: true,
      })
      return response.data ?? []
    },
    enabled: Boolean(orderId),
  })
}
