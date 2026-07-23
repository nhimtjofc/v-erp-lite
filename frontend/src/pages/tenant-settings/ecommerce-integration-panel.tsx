import { useTranslation } from 'react-i18next'
import { useEcommerceHealthQuery } from '@/shared/api'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import {
  Activity,
  ArrowLeftRight,
  LoaderCircle,
  PlugZap,
  RefreshCw,
  ShieldCheck,
  Store,
  Webhook,
} from 'lucide-react'

function formatTimestamp(value: string | null, locale: string, fallback: string): string {
  if (!value) return fallback
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return fallback
  return new Intl.DateTimeFormat(locale, {
    dateStyle: 'medium',
    timeStyle: 'medium',
  }).format(date)
}

function ConnectionBadge({ status }: { status: string }) {
  const { t } = useTranslation()
  if (status === 'CONNECTED') {
    return (
      <Badge className="border-transparent bg-success text-success-foreground">
        {t('settings:ecommerce.connectionStatuses.connected')}
      </Badge>
    )
  }
  if (status === 'ERROR') {
    return <Badge variant="destructive">{t('settings:ecommerce.connectionStatuses.error')}</Badge>
  }
  if (status === 'CONFIGURED') {
    return (
      <Badge variant="outline" className="border-success/40 text-success">
        {t('settings:ecommerce.connectionStatuses.configured')}
      </Badge>
    )
  }
  if (status === 'DISABLED') {
    return <Badge variant="secondary">{t('settings:ecommerce.connectionStatuses.disabled')}</Badge>
  }
  if (status === 'NOT_CONFIGURED') {
    return <Badge variant="outline">{t('settings:ecommerce.connectionStatuses.notConfigured')}</Badge>
  }
  return <Badge variant="secondary">{t('settings:ecommerce.connectionStatuses.disconnected')}</Badge>
}

function HealthBadge({ status }: { status: string }) {
  const { t } = useTranslation()
  if (status === 'UP') {
    return (
      <Badge variant="outline" className="border-success/40 text-success">
        {t('settings:ecommerce.healthStatuses.up')}
      </Badge>
    )
  }
  if (status === 'DOWN') {
    return <Badge variant="destructive">{t('settings:ecommerce.healthStatuses.down')}</Badge>
  }
  if (status === 'DEGRADED') {
    return <Badge variant="secondary">{t('settings:ecommerce.healthStatuses.degraded')}</Badge>
  }
  return <Badge variant="outline">{t('settings:ecommerce.healthStatuses.unknown')}</Badge>
}

export function EcommerceIntegrationPanel({
  tenantScope,
  businessId,
}: {
  tenantScope?: string
  businessId?: string
}) {
  const { t, i18n } = useTranslation()
  const query = useEcommerceHealthQuery(tenantScope, businessId)

  if (!tenantScope || query.isLoading) {
    return (
      <Card aria-live="polite">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <PlugZap className="h-5 w-5" />
            {t('settings:ecommerce.title')}
          </CardTitle>
          <CardDescription>{t('settings:ecommerce.description')}</CardDescription>
        </CardHeader>
        <CardContent className="flex items-center gap-2 text-sm text-muted-foreground">
          <LoaderCircle className="h-4 w-4 animate-spin" />
          {t('settings:ecommerce.loading')}
        </CardContent>
      </Card>
    )
  }

  if (query.isError || !query.data) {
    return (
      <Card aria-live="polite" className="border-destructive/40">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <PlugZap className="h-5 w-5" />
            {t('settings:ecommerce.title')}
          </CardTitle>
          <CardDescription>{t('settings:ecommerce.loadError')}</CardDescription>
        </CardHeader>
        <CardContent>
          <Button variant="outline" size="sm" onClick={() => query.refetch()}>
            <RefreshCw className="h-4 w-4" />
            {t('settings:ecommerce.retry')}
          </Button>
        </CardContent>
      </Card>
    )
  }

  const health = query.data
  const fallback = t('settings:ecommerce.notAvailable')

  return (
    <Card aria-live="polite">
      <CardHeader className="gap-3 sm:flex-row sm:items-start sm:justify-between sm:space-y-0">
        <div className="space-y-1.5">
          <CardTitle className="flex items-center gap-2">
            <PlugZap className="h-5 w-5" />
            {t('settings:ecommerce.title')}
          </CardTitle>
          <CardDescription>{t('settings:ecommerce.description')}</CardDescription>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={() => query.refetch()}
          disabled={query.isFetching}
        >
          <RefreshCw className={query.isFetching ? 'h-4 w-4 animate-spin' : 'h-4 w-4'} />
          {query.isFetching
            ? t('settings:ecommerce.refreshing')
            : t('settings:ecommerce.refresh')}
        </Button>
      </CardHeader>
      <CardContent className="space-y-5">
        <div className="space-y-2">
          <Label htmlFor="ecommerce-store-url" className="flex items-center gap-2">
            <Store className="h-4 w-4 text-muted-foreground" />
            {t('settings:ecommerce.storeUrl')}
          </Label>
          <Input
            id="ecommerce-store-url"
            value={health.storeUrl ?? fallback}
            readOnly
            className="font-mono text-sm"
          />
        </div>

        <div className="grid gap-4 sm:grid-cols-3">
          <div className="space-y-2 rounded-lg border p-3">
            <p className="text-xs font-medium text-muted-foreground">
              {t('settings:ecommerce.connection')}
            </p>
            <ConnectionBadge status={health.connectionStatus} />
          </div>
          <div className="space-y-2 rounded-lg border p-3">
            <p className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
              <Activity className="h-3.5 w-3.5" />
              {t('settings:ecommerce.health')}
            </p>
            <HealthBadge status={health.healthStatus} />
          </div>
          <div className="space-y-2 rounded-lg border p-3">
            <p className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
              <ShieldCheck className="h-3.5 w-3.5" />
              {t('settings:ecommerce.secret')}
            </p>
            <Badge variant={health.secretConfigured ? 'default' : 'outline'}>
              {health.secretConfigured
                ? t('settings:ecommerce.secretConfigured')
                : t('settings:ecommerce.secretNotConfigured')}
            </Badge>
          </div>
        </div>

        <dl className="grid gap-2 text-sm text-muted-foreground sm:grid-cols-2">
          <div>
            <dt className="font-medium text-foreground">{t('settings:ecommerce.lastChecked')}</dt>
            <dd>{formatTimestamp(health.lastCheckedAt, i18n.language, fallback)}</dd>
          </div>
          <div>
            <dt className="font-medium text-foreground">{t('settings:ecommerce.lastConnected')}</dt>
            <dd>{formatTimestamp(health.lastConnectedAt, i18n.language, fallback)}</dd>
          </div>
        </dl>

        <div className="grid gap-3 border-t pt-4 sm:grid-cols-2">
          <div className="flex items-center gap-3 rounded-lg bg-muted/40 p-3">
            <ArrowLeftRight className="h-4 w-4 text-muted-foreground" />
            <div>
              <p className="text-xs text-muted-foreground">
                {t('settings:ecommerce.mappedEntities')}
              </p>
              <p className="font-medium tabular-nums">{health.mappedEntityCount}</p>
            </div>
          </div>
          <div className="flex items-center gap-3 rounded-lg bg-muted/40 p-3">
            <Webhook className="h-4 w-4 text-muted-foreground" />
            <div>
              <p className="text-xs text-muted-foreground">
                {t('settings:ecommerce.webhookEvents')}
              </p>
              <p className="font-medium tabular-nums">{health.webhookEventCount}</p>
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
