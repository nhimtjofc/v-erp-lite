import { useMutation, useQuery } from '@tanstack/react-query'
import { listNotifications, getNotificationCount, markNotificationRead, markAllNotificationsRead } from './gen/sdk.gen'
import type { Notification as NotificationEntity } from './gen/types.gen'

export type Notification = NotificationEntity
export interface NotificationCount {
  total: number
  unread: number
}

export function useNotificationsQuery(unreadOnly?: boolean) {
  return useQuery({
    queryKey: ['notifications', { unreadOnly }],
    queryFn: async () => {
      const response = await listNotifications({
        query: unreadOnly ? { unreadOnly: true } : undefined,
        throwOnError: true,
      })
      return response.data ?? []
    },
    refetchInterval: 30_000,
  })
}

export function useNotificationCountQuery() {
  return useQuery({
    queryKey: ['notifications-count'],
    queryFn: async () => {
      const response = await getNotificationCount({ throwOnError: true })
      return response.data as unknown as NotificationCount
    },
    refetchInterval: 30_000,
  })
}

export function useMarkNotificationReadMutation() {
  return useMutation({
    mutationFn: async (id: string) => {
      const response = await markNotificationRead({ path: { id }, throwOnError: true })
      return response.data
    },
  })
}

export function useMarkAllNotificationsReadMutation() {
  return useMutation({
    mutationFn: async () => {
      const response = await markAllNotificationsRead({ throwOnError: true })
      return response.data
    },
  })
}
