import { Link, useNavigate } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import {
  useNotificationsQuery,
  useMarkNotificationReadMutation,
  useMarkAllNotificationsReadMutation,
  type Notification,
} from '@/shared/api/notifications'
import { Button } from '@/shared/ui/button'
import { Card, CardContent } from '@/shared/ui/card'
import { Badge } from '@/shared/ui/badge'
import { Bell, CheckCheck, ArrowLeft, Receipt } from 'lucide-react'

function formatDate(value?: string): string {
  return value ? new Date(value).toLocaleString() : '—'
}

export function NotificationsPage() {
  const { t } = useTranslation('notifications')
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data: notifications, isLoading } = useNotificationsQuery()
  const markRead = useMarkNotificationReadMutation()
  const markAllRead = useMarkAllNotificationsReadMutation()

  const NOTIFICATION_TYPE_LABELS: Record<string, { label: string; variant: 'default' | 'secondary' | 'destructive' }> = {
    ORDER_OWNER_ACCEPTED: { label: t('orderAccepted'), variant: 'default' },
    ORDER_OWNER_DECLINED: { label: t('orderDeclined'), variant: 'destructive' },
  }

  const handleMarkAllRead = async () => {
    await markAllRead.mutateAsync()
    await queryClient.invalidateQueries({ queryKey: ['notifications'] })
    await queryClient.invalidateQueries({ queryKey: ['notifications-count'] })
  }

  const handleClickNotification = async (notification: Notification) => {
    if (!notification.readAt) {
      await markRead.mutateAsync(notification.id!)
      await queryClient.invalidateQueries({ queryKey: ['notifications'] })
      await queryClient.invalidateQueries({ queryKey: ['notifications-count'] })
    }
    if (notification.orderId) {
      navigate({ to: '/orders/$id', params: { id: notification.orderId } })
    }
  }

  const list = notifications ?? []
  const unreadCount = list.filter((n) => !n.readAt).length

  return (
    <div className="space-y-6 max-w-3xl mx-auto">
      <div className="flex items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <Link to="/orders" className="text-muted-foreground hover:text-foreground flex items-center gap-1 text-sm font-medium">
            <ArrowLeft className="h-4 w-4" /> {t('back')}
          </Link>
          <div className="flex items-center gap-2.5">
            <Bell className="h-6 w-6 text-primary" />
            <h1 className="text-2xl font-bold tracking-tight">{t('title')}</h1>
            {unreadCount > 0 && (
              <Badge variant="destructive" className="text-xs px-2 py-0.5">{t('unread', { count: unreadCount })}</Badge>
            )}
          </div>
        </div>
        {unreadCount > 0 && (
          <Button variant="outline" size="sm" onClick={handleMarkAllRead} disabled={markAllRead.isPending}>
            <CheckCheck className="h-4 w-4 mr-1.5" /> {t('markAllRead')}
          </Button>
        )}
      </div>

      <Card>
        <CardContent className="p-0">
          {isLoading ? (
            <div className="py-16 text-center text-muted-foreground">{t('loading')}</div>
          ) : list.length === 0 ? (
            <div className="py-16 text-center space-y-3">
              <div className="mx-auto w-16 h-16 rounded-full bg-muted flex items-center justify-center">
                <Bell className="h-8 w-8 text-muted-foreground/50" />
              </div>
              <div>
                <p className="font-semibold">{t('emptyTitle')}</p>
                <p className="text-sm text-muted-foreground mt-1">{t('emptyDescription')}</p>
              </div>
            </div>
          ) : (
            <div className="divide-y divide-border">
              {list.map((notification) => {
                const typeMeta = NOTIFICATION_TYPE_LABELS[notification.type ?? ''] ?? { label: notification.type, variant: 'secondary' as const }
                const isUnread = !notification.readAt
                return (
                  <div
                    key={notification.id}
                    onClick={() => handleClickNotification(notification)}
                    className={`flex items-start gap-4 px-4 py-3 cursor-pointer transition-colors hover:bg-muted/50 ${
                      isUnread ? 'bg-primary/5' : ''
                    }`}
                  >
                    <div className={`mt-1 h-2 w-2 rounded-full shrink-0 ${isUnread ? 'bg-primary' : 'bg-transparent'}`} />
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1">
                        <Badge variant={typeMeta.variant} className="text-[10px] px-1.5 py-0">{typeMeta.label}</Badge>
                        {notification.orderId && (
                          <span className="text-xs text-muted-foreground font-mono flex items-center gap-1">
                            <Receipt className="h-3 w-3" />
                            {notification.orderId.slice(0, 8)}…
                          </span>
                        )}
                      </div>
                      <p className="text-sm text-foreground">{notification.message}</p>
                      <p className="text-xs text-muted-foreground mt-1">{formatDate(notification.createdAt)}</p>
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
