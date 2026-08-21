import { useSyncExternalStore } from 'react'
import { useEcommerceHealthQuery } from './api/ecommerce'

/**
 * Shared, app-wide selection of the active Ecommerce business/channel.
 *
 * The backend resolves one Ecommerce channel per business, so the user picks
 * a *business* and every Woo-scoped query (health, orders sync, reconcile) uses
 * the channel resolved from it. Selection is persisted to localStorage and
 * shared across pages via a tiny external store — no provider wiring required.
 */

const STORAGE_KEY = 'woo_selected_business_id'

let current: string | undefined =
  typeof window !== 'undefined'
    ? localStorage.getItem(STORAGE_KEY) ?? undefined
    : undefined

const listeners = new Set<() => void>()

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

function getSnapshot() {
  return current
}

export function setSelectedBusinessId(businessId: string | undefined) {
  current = businessId
  if (typeof window !== 'undefined') {
    if (businessId) localStorage.setItem(STORAGE_KEY, businessId)
    else localStorage.removeItem(STORAGE_KEY)
  }
  listeners.forEach((l) => l())
}

/** The currently selected businessId (or undefined = tenant default channel). */
export function useSelectedBusinessId() {
  return useSyncExternalStore(subscribe, getSnapshot, getSnapshot)
}

/**
 * Resolves the active Ecommerce channel for the selected business.
 * Returns the selection, the health snapshot, and the resolved channelId to
 * pass as `salesChannelId` to orders/catalog queries.
 */
export function useWooChannel(
  tenantScope: string | undefined,
  options?: { enabled?: boolean }
) {
  const businessId = useSelectedBusinessId()
  const health = useEcommerceHealthQuery(tenantScope, businessId, options)
  return {
    businessId,
    setBusinessId: setSelectedBusinessId,
    channelId: health.data?.channelId ?? undefined,
    health,
  }
}
