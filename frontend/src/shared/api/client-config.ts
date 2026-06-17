import type { CreateClientConfig } from './gen/client.gen'

export const createClientConfig: CreateClientConfig = (config) => ({
  ...config,
  baseUrl: import.meta.env.VITE_API_BASE_URL || '',
  auth: () => localStorage.getItem('access_token') ?? undefined,
  throwOnError: true,
})
