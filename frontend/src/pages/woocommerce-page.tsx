import { useTranslation } from 'react-i18next'
import {
  useGetCatalogSyncSummaryQuery,
  useMeQuery,
} from '@/shared/api'
import { useWooCommerceHealthQuery } from '@/shared/api/woocommerce'
import { Button } from '@/shared/ui/button'
import { Badge } from '@/shared/ui/badge'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/shared/ui/card'
import {
  PlugZap,
  Activity,
  ArrowLeftRight,
  RefreshCw,
  ShieldCheck,
  Store,
  Webhook,
  Package,
  CheckCircle2,
  AlertTriangle,
  LoaderCircle,
  Clock,
  ExternalLink,
} from 'lucide-react'

function formatTimestamp(value: string | null | undefined, locale: string, fallback: string): string {
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
        {t('settings:woocommerce.connectionStatuses.connected')}
      </Badge>
    )
  }
  if (status === 'ERROR') {
    return <Badge variant="destructive">{t('settings:woocommerce.connectionStatuses.error')}</Badge>
  }
  if (status === 'CONFIGURED') {
    return (
      <Badge variant="outline" className="border-success/40 text-success">
        {t('settings:woocommerce.connectionStatuses.configured')}
      </Badge>
    )
  }
  if (status === 'DISABLED') {
    return <Badge variant="secondary">{t('settings:woocommerce.connectionStatuses.disabled')}</Badge>
  }
  if (status === 'NOT_CONFIGURED') {
    return <Badge variant="outline">{t('settings:woocommerce.connectionStatuses.notConfigured')}</Badge>
  }
  return <Badge variant="secondary">{t('settings:woocommerce.connectionStatuses.disconnected')}</Badge>
}

function HealthBadge({ status }: { status: string }) {
  const { t } = useTranslation()
  if (status === 'UP') {
    return (
      <Badge variant="outline" className="border-success/40 text-success">
        {t('settings:woocommerce.healthStatuses.up')}
      </Badge>
    )
  }
  if (status === 'DOWN') {
    return <Badge variant="destructive">{t('settings:woocommerce.healthStatuses.down')}</Badge>
  }
  if (status === 'DEGRADED') {
    return <Badge variant="secondary">{t('settings:woocommerce.healthStatuses.degraded')}</Badge>
  }
  return <Badge variant="outline">{t('settings:woocommerce.healthStatuses.unknown')}</Badge>
}

export function WooCommercePage() {
  const { t, i18n } = useTranslation()
  const { data: me } = useMeQuery()
  const healthQuery = useWooCommerceHealthQuery(me?.tenantId)
  const syncSummaryQuery = useGetCatalogSyncSummaryQuery()

  const isLoading = healthQuery.isLoading || syncSummaryQuery.isLoading
  const health = healthQuery.data ?? null
  const syncSummary = syncSummaryQuery.data

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <LoaderCircle className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    )
  }

  if (!health) {
    return (
      <div className="space-y-6">
        <div>
          <h2 className="text-lg font-semibold flex items-center gap-2">
            <PlugZap className="h-5 w-5" />
            WooCommerce Integration
          </h2>
          <p className="text-sm text-muted-foreground mt-1">Manage your WooCommerce store connection and catalog sync</p>
        </div>
        <Card className="border-destructive/40">
          <CardContent className="flex flex-col items-center justify-center py-12 text-center">
            <AlertTriangle className="h-10 w-10 text-destructive mb-3" />
            <p className="text-sm font-medium text-destructive">Unable to load integration status</p>
            <Button variant="outline" size="sm" className="mt-3" onClick={() => healthQuery.refetch()}>
              <RefreshCw className="h-4 w-4 mr-1" /> Retry
            </Button>
          </CardContent>
        </Card>
      </div>
    )
  }

  const fallback = 'N/A'
  const totalProducts = syncSummary?.totalProducts ?? 0
  const syncedProducts = syncSummary?.syncedProducts ?? 0
  const syncPercent = totalProducts > 0 ? Math.round((syncedProducts / totalProducts) * 100) : 0
  const unsyncedProducts = totalProducts - syncedProducts

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold flex items-center gap-2">
            <PlugZap className="h-5 w-5" />
            WooCommerce Integration
          </h2>
          <p className="text-sm text-muted-foreground mt-1">Manage your WooCommerce store connection and catalog sync</p>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={() => {
            healthQuery.refetch()
            syncSummaryQuery.refetch()
          }}
          disabled={healthQuery.isFetching || syncSummaryQuery.isFetching}
        >
          <RefreshCw className={(healthQuery.isFetching || syncSummaryQuery.isFetching) ? 'h-4 w-4 animate-spin mr-1' : 'h-4 w-4 mr-1'} />
          Refresh
        </Button>
      </div>

      {/* Connection Status */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Store className="h-4 w-4" />
            Connection Status
          </CardTitle>
          <CardDescription>WooCommerce store connection and health information</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {/* Store URL */}
          <div className="flex items-center gap-3 rounded-lg border p-3 bg-muted/30">
            <Store className="h-4 w-4 text-muted-foreground shrink-0" />
            <div className="flex-1 min-w-0">
              <p className="text-xs text-muted-foreground">Store URL</p>
              <p className="text-sm font-mono font-medium truncate">
                {health.storeUrl || fallback}
              </p>
            </div>
            {health.storeUrl && (
              <a
                href={health.storeUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="text-muted-foreground hover:text-foreground transition-colors"
              >
                <ExternalLink className="h-4 w-4" />
              </a>
            )}
          </div>

          {/* Status Badges */}
          <div className="grid gap-3 sm:grid-cols-3">
            <div className="flex items-center gap-3 rounded-lg border p-3">
              <Activity className="h-4 w-4 text-muted-foreground shrink-0" />
              <div>
                <p className="text-xs text-muted-foreground">Connection</p>
                <div className="mt-1"><ConnectionBadge status={health.connectionStatus ?? 'DISCONNECTED'} /></div>
              </div>
            </div>
            <div className="flex items-center gap-3 rounded-lg border p-3">
              <Activity className="h-4 w-4 text-muted-foreground shrink-0" />
              <div>
                <p className="text-xs text-muted-foreground">Health</p>
                <div className="mt-1"><HealthBadge status={health.healthStatus ?? 'UNKNOWN'} /></div>
              </div>
            </div>
            <div className="flex items-center gap-3 rounded-lg border p-3">
              <ShieldCheck className="h-4 w-4 text-muted-foreground shrink-0" />
              <div>
                <p className="text-xs text-muted-foreground">API Secret</p>
                <div className="mt-1">
                  <Badge variant={health.secretConfigured ? 'default' : 'outline'}>
                    {health.secretConfigured ? 'Configured' : 'Not Set'}
                  </Badge>
                </div>
              </div>
            </div>
          </div>

          {/* Timestamps */}
          <div className="grid gap-3 sm:grid-cols-2 text-sm">
            <div className="flex items-center gap-2 text-muted-foreground">
              <Clock className="h-3.5 w-3.5" />
              <span className="font-medium text-foreground">Last Checked:</span>
              {formatTimestamp(health.lastCheckedAt, i18n.language, fallback)}
            </div>
            <div className="flex items-center gap-2 text-muted-foreground">
              <Clock className="h-3.5 w-3.5" />
              <span className="font-medium text-foreground">Last Connected:</span>
              {formatTimestamp(health.lastConnectedAt, i18n.language, fallback)}
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Integration Stats */}
      <div className="grid gap-4 sm:grid-cols-2">
        <Card>
          <CardContent className="flex items-center gap-4 p-4">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary/10">
              <ArrowLeftRight className="h-5 w-5 text-primary" />
            </div>
            <div>
              <p className="text-2xl font-bold">{health.mappedEntityCount}</p>
              <p className="text-xs text-muted-foreground">Mapped Entities</p>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="flex items-center gap-4 p-4">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-amber-500/10">
              <Webhook className="h-5 w-5 text-amber-600" />
            </div>
            <div>
              <p className="text-2xl font-bold">{health.webhookEventCount}</p>
              <p className="text-xs text-muted-foreground">Webhook Events</p>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Catalog Sync */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Package className="h-4 w-4" />
            Catalog Sync
          </CardTitle>
          <CardDescription>Product synchronization status between ERP and WooCommerce</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {syncSummary ? (
            <>
              {/* Sync Progress */}
              <div className="space-y-2">
                <div className="flex items-center justify-between text-sm">
                  <span className="text-muted-foreground">Sync Progress</span>
                  <span className="font-bold">{syncPercent}%</span>
                </div>
                <div className="h-2.5 w-full rounded-full bg-muted overflow-hidden">
                  <div
                    className="h-full rounded-full bg-gradient-to-r from-violet-500 to-fuchsia-500 transition-all duration-500"
                    style={{ width: `${syncPercent}%` }}
                  />
                </div>
              </div>

              {/* Stats Grid */}
              <div className="grid gap-3 sm:grid-cols-3">
                <div className="rounded-lg border p-3 text-center">
                  <p className="text-2xl font-bold">{totalProducts}</p>
                  <p className="text-xs text-muted-foreground">Total Products</p>
                </div>
                <div className="rounded-lg border p-3 text-center">
                  <p className="text-2xl font-bold text-success">{syncedProducts}</p>
                  <p className="text-xs text-muted-foreground">Synced</p>
                </div>
                <div className="rounded-lg border p-3 text-center">
                  <p className="text-2xl font-bold text-amber-600">{unsyncedProducts}</p>
                  <p className="text-xs text-muted-foreground">Pending</p>
                </div>
              </div>

              {/* Last Sync */}
              {syncSummary.lastSyncTime && (
                <div className="flex items-center gap-2 text-sm text-muted-foreground rounded-lg border p-3 bg-muted/30">
                  <CheckCircle2 className="h-4 w-4 text-success shrink-0" />
                  <span>Last sync: {formatTimestamp(syncSummary.lastSyncTime, i18n.language, fallback)}</span>
                </div>
              )}
            </>
          ) : (
            <div className="text-center py-8 text-muted-foreground">
              <Package className="h-8 w-8 mx-auto mb-2 opacity-30" />
              <p className="text-sm">No sync data available</p>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
