import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { getUser } from '@/shared/auth'
import { useHealthQuery, useListBusinessesQuery } from '@/shared/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import type { User } from 'oidc-client-ts'

export function DashboardPage() {
  const { t } = useTranslation()
  const [user, setUser] = useState<User | null>(null)
  const { data: healthRaw } = useHealthQuery()
  const { data: customers } = useListBusinessesQuery()
  const health = healthRaw as { status?: string; service?: string } | undefined
  useEffect(() => {
    getUser().then(setUser)
  }, [])

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">{t('dashboard:title')}</h1>
        <p className="text-muted-foreground mt-1">
          {t('dashboard:welcome', { name: user?.profile?.name })}
        </p>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium">{t('dashboard:systemStatus')}</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{health?.status ?? '...'}</div>
            <p className="text-xs text-muted-foreground mt-1">
              {health?.service ?? t('common:loading')}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium">{t('dashboard:customers')}</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{customers ? customers.length : '...'}</div>
            <p className="text-xs text-muted-foreground mt-1">{t('dashboard:customersCount')}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium">{t('dashboard:tenant')}</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold truncate">
              {user?.profile?.azp ?? 'default'}
            </div>
            <p className="text-xs text-muted-foreground mt-1">{t('dashboard:activeTenant')}</p>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
