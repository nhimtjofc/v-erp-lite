import { useTranslation } from 'react-i18next'
import { Link } from '@tanstack/react-router'
import { useMeQuery } from '@/shared/api'
import { useWooChannel } from '@/shared/woo-channel'
import { WooChannelSelector } from '@/shared/ui/woo-channel-selector'
import { PlugZap, Receipt, ShoppingBag, ChevronRight } from 'lucide-react'
import { WooCommerceIntegrationPanel } from './woocommerce-integration-panel'

export function IntegrationsPage() {
  const { t } = useTranslation()
  const { data: me } = useMeQuery()
  const { businessId } = useWooChannel(me?.tenantId)

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
              Woo ID, trạng thái đồng bộ và retry/reconcile (tab “WooCommerce Sync”).
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
              Tổng hợp đồng bộ catalog ERP → WooCommerce.
            </p>
          </div>
          <ChevronRight className="h-4 w-4 shrink-0 text-muted-foreground transition-transform group-hover:translate-x-0.5" />
        </Link>
      </div>
    </div>
  )
}
