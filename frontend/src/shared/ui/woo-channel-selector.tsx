import { useMeQuery, useListBusinessesQuery } from '@/shared/api'
import { useWooChannel } from '@/shared/woo-channel'
import { useTranslation } from 'react-i18next'
import { Store, CircleDot } from 'lucide-react'

const STATUS_TONE: Record<string, string> = {
  CONNECTED: 'text-emerald-600 dark:text-emerald-400',
  CONFIGURED: 'text-amber-600 dark:text-amber-400',
  DISCONNECTED: 'text-muted-foreground',
  DISABLED: 'text-muted-foreground',
  ERROR: 'text-destructive',
}

/**
 * Business/channel picker shared across the WooCommerce staff surfaces.
 * Selecting a business updates the app-wide selection consumed by the
 * Integrations, Orders (Woo Sync) and catalog views.
 */
export function WooChannelSelector({ className }: { className?: string }) {
  const { t } = useTranslation()
  const { data: me } = useMeQuery()
  const { data: businesses, isLoading } = useListBusinessesQuery()
  const { businessId, setBusinessId, health } = useWooChannel(me?.tenantId)

  const list = businesses ?? []
  const status = health.data?.connectionStatus
  const tone = status ? STATUS_TONE[status] ?? 'text-muted-foreground' : 'text-muted-foreground'

  return (
    <div className={`flex flex-wrap items-center gap-2 ${className ?? ''}`}>
      <Store className="h-4 w-4 shrink-0 text-muted-foreground" />
      <select
        value={businessId ?? ''}
        onChange={(e) => setBusinessId(e.target.value || undefined)}
        disabled={isLoading}
        aria-label={t('settings:woocommerce.businessSelector')}
        className="h-9 min-w-[12rem] rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
      >
        <option value="">{t('settings:woocommerce.defaultChannel')}</option>
        {list.map((b) => (
          <option key={b.id} value={b.id}>
            {b.name ?? b.id}
          </option>
        ))}
      </select>

      {health.isFetching ? (
        <span className="text-xs text-muted-foreground animate-pulse">
          {t('settings:woocommerce.checkingChannel')}
        </span>
      ) : status ? (
        <span className={`flex items-center gap-1 text-xs font-medium ${tone}`}>
          <CircleDot className="h-3 w-3" />
          {status}
        </span>
      ) : null}
    </div>
  )
}
