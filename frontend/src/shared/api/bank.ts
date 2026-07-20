import { useQuery } from '@tanstack/react-query'
import { listBanks } from './gen/sdk.gen'
import type { Bank } from './gen/types.gen'

export type BankOption = Bank

export function useBanksQuery() {
  return useQuery({
    queryKey: ['banks'],
    queryFn: async () => {
      const response = await listBanks({ throwOnError: true })
      return response.data ?? []
    },
    staleTime: 5 * 60 * 1000,
  })
}
