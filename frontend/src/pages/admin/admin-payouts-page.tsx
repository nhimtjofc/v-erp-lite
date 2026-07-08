import { useTranslation } from 'react-i18next'
import { useMeQuery } from '@/shared/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Banknote } from 'lucide-react'

/**
 * Admin payout approval dashboard.
 *
 * Day 1 (Dev 5): route/menu skeleton with role guard. The pending payout list and
 * approve/reject actions are wired up on later days.
 */
export function AdminPayoutsPage() {
  const { t } = useTranslation()
  const { data: me, isLoading: isMeLoading } = useMeQuery()
  const roleName = me?.role?.name
  const isAdmin = roleName === 'admin'

  if (isMeLoading) {
    return <div className="text-center py-8 text-muted-foreground">Loading access control...</div>
  }

  if (!isAdmin) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[300px] border border-dashed rounded-lg p-8 text-center space-y-4">
        <h2 className="text-xl font-bold text-destructive">Access Denied</h2>
        <p className="text-muted-foreground max-w-md">
          Only administrators can review payout requests.
        </p>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <Banknote className="h-7 w-7 text-primary" />
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('nav:payouts', 'Payouts')}</h1>
          <p className="text-muted-foreground mt-1">
            Review, approve or reject seller payout requests.
          </p>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t('nav:payouts', 'Payouts')}</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground py-8 text-center">
            Pending payout requests coming soon.
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
