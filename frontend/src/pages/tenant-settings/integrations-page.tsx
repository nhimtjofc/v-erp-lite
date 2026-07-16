import { useTranslation } from 'react-i18next'
import { Link } from '@tanstack/react-router'
import { useGetCatalogSyncSummaryQuery, useMeQuery } from '@/shared/api'
import { useWooChannel } from '@/shared/woo-channel'
import { WooChannelSelector } from '@/shared/ui/woo-channel-selector'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'
import {
  CheckCircle2,
  ChevronRight,
  Clock,
  LoaderCircle,
  Package,
  PlugZap,
  Receipt,
  RefreshCw,
  ShoppingBag,
} from 'lucide-react'
import { WooCommerceIntegrationPanel } from './woocommerce-integration-panel'

export function IntegrationsPage() {
  const { t, i18n } = useTranslation()
  const { data: me } = useMeQuery()
  const { businessId } = useWooChannel(me?.tenantId)
  const catalogSummaryQuery = useGetCatalogSyncSummaryQuery(
    businessId ? { query: { businessId } } : undefined,
  )
  const summary = catalogSummaryQuery.data
  const totalProducts = Number(summary?.totalProducts ?? 0)
  const syncedProducts = Number(summary?.syncedProducts ?? 0)
  const pendingProducts = Math.max(0, totalProducts - syncedProducts)
  const syncPercent = totalProducts > 0
    ? Math.round((syncedProducts / totalProducts) * 100)
    : 0

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <PlugZap className="h-6 w-6 text-muted-foreground" />
          <div>
            <h2 className="text-lg font-semibold">{t('nav:integrations')}</h2>
            <p className="text-sm text-muted-foreground">
              {t('settings:woocommerce.description')}
            </p>
          </div>
        </div>
        <WooChannelSelector />
      </div>

      {/* WooCommerce connection / health */}
      <WooCommerceIntegrationPanel tenantScope={me?.tenantId} businessId={businessId} />

      <Card>
        <CardHeader className="gap-3 sm:flex-row sm:items-start sm:justify-between sm:space-y-0">
          <div className="space-y-1.5">
            <CardTitle className="flex items-center gap-2 text-base">
              <Package className="h-4 w-4" />
              {t('settings:woocommerce.catalogSync')}
            </CardTitle>
            <CardDescription>
              {t('settings:woocommerce.catalogSyncDescription')}
            </CardDescription>
          </div>
          <Button
            variant="outline"
            size="sm"
            onClick={() => catalogSummaryQuery.refetch()}
            disabled={catalogSummaryQuery.isFetching}
          >
            <RefreshCw className={catalogSummaryQuery.isFetching ? 'h-4 w-4 animate-spin' : 'h-4 w-4'} />
            {catalogSummaryQuery.isFetching
              ? t('settings:woocommerce.refreshing')
              : t('settings:woocommerce.refresh')}
          </Button>
        </CardHeader>
        <CardContent className="space-y-4">
          {catalogSummaryQuery.isLoading ? (
            <div className="flex items-center gap-2 py-6 text-sm text-muted-foreground">
              <LoaderCircle className="h-4 w-4 animate-spin" />
              {t('settings:woocommerce.loading')}
            </div>
          ) : catalogSummaryQuery.isError || !summary ? (
            <p className="py-6 text-sm text-muted-foreground">
              {t('settings:woocommerce.noSyncData')}
            </p>
          ) : (
            <>
              <div className="space-y-2">
                <div className="flex items-center justify-between text-sm">
                  <span className="text-muted-foreground">
                    {t('settings:woocommerce.syncProgress')}
                  </span>
                  <span className="font-semibold tabular-nums">{syncPercent}%</span>
                </div>
                <div className="h-2.5 overflow-hidden rounded-full bg-muted">
                  <div
                    className="h-full rounded-full bg-primary transition-[width]"
                    style={{ width: `${syncPercent}%` }}
                  />
                </div>
              </div>

              <div className="grid gap-3 sm:grid-cols-3">
                <CatalogMetric label={t('settings:woocommerce.totalProducts')} value={totalProducts} />
                <CatalogMetric
                  label={t('settings:woocommerce.syncedProducts')}
                  value={syncedProducts}
                  tone="text-success"
                />
                <CatalogMetric
                  label={t('settings:woocommerce.pendingProducts')}
                  value={pendingProducts}
                  tone="text-amber-600 dark:text-amber-400"
                />
              </div>

              {summary.lastSyncTime && (
                <div className="flex items-center gap-2 rounded-lg border bg-muted/30 p-3 text-sm text-muted-foreground">
                  <CheckCircle2 className="h-4 w-4 shrink-0 text-success" />
                  <Clock className="h-4 w-4 shrink-0" />
                  <span>
                    {t('settings:woocommerce.lastSync', {
                      value: new Intl.DateTimeFormat(i18n.language, {
                        dateStyle: 'medium',
                        timeStyle: 'medium',
                      }).format(new Date(summary.lastSyncTime)),
                    })}
                  </span>
                </div>
              )}
            </>
          )}
        </CardContent>
      </Card>

      {/* Where to go next */}
      <div className="grid gap-3 sm:grid-cols-2">
        <Link
          to="/orders"
          className="group flex items-center gap-3 rounded-xl border border-border bg-card p-4 transition-colors hover:border-primary/40"
        >
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-indigo-50 dark:bg-indigo-900/20">
            <Receipt className="h-5 w-5 text-indigo-600 dark:text-indigo-400" />
          </div>
          <div className="min-w-0 flex-1">
            <div className="font-medium text-foreground">{t('nav:orders')}</div>
            <p className="text-xs text-muted-foreground">
              {t('settings:woocommerce.ordersHelp')}
            </p>
          </div>
          <ChevronRight className="h-4 w-4 shrink-0 text-muted-foreground transition-transform group-hover:translate-x-0.5" />
        </Link>

        <Link
          to="/products"
          className="group flex items-center gap-3 rounded-xl border border-border bg-card p-4 transition-colors hover:border-primary/40"
        >
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-emerald-50 dark:bg-emerald-900/20">
            <ShoppingBag className="h-5 w-5 text-emerald-600 dark:text-emerald-400" />
          </div>
          <div className="min-w-0 flex-1">
            <div className="font-medium text-foreground">{t('nav:products')}</div>
            <p className="text-xs text-muted-foreground">
              {t('settings:woocommerce.productsHelp')}
            </p>
          </div>
          <ChevronRight className="h-4 w-4 shrink-0 text-muted-foreground transition-transform group-hover:translate-x-0.5" />
        </Link>
      </div>
    </div>
  )
}

function CatalogMetric({
  label,
  value,
  tone,
}: {
  label: string
  value: number
  tone?: string
}) {
  return (
    <div className="rounded-lg border p-3 text-center">
      <p className={`text-2xl font-bold tabular-nums ${tone ?? ''}`}>{value}</p>
      <p className="text-xs text-muted-foreground">{label}</p>
    </div>
  )
}
