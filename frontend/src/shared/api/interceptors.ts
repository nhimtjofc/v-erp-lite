import { client } from './gen/client.gen'

// Registered once at app startup. Stashed in main.tsx import.

const retryClones = new WeakMap<Request, Request>()

const KC_URL = import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8181'
const KC_REALM = import.meta.env.VITE_KEYCLOAK_REALM || 'v-erp-lite'
const KC_CLIENT = import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'v-erp-app'

client.interceptors.request.use((request) => {
  retryClones.set(request, request.clone())
  return request
})

// ── Refresh coalescing (shared by 401 interceptor + proactive timer) ──

let refreshInFlight: Promise<boolean> | null = null

async function refreshOnce(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = refreshToken().finally(() => {
      refreshInFlight = null
    })
  }
  return refreshInFlight
}

// ── Proactive refresh timer (replaces automaticSilentRenew) ──

let refreshTimerId: ReturnType<typeof setTimeout> | null = null

/** Schedule a token refresh 60 s before expiry (min 10 s from now). */
function scheduleRefresh(expiresInSeconds: number) {
  if (refreshTimerId) clearTimeout(refreshTimerId)
  const delayMs = Math.max(10_000, (expiresInSeconds - 60) * 1000)
  refreshTimerId = setTimeout(async () => {
    refreshTimerId = null
    const ok = await refreshOnce()
    if (!ok) {
      // Transient failure — retry in 30 s.
      // If it was a permanent failure, failAuth() inside refreshToken()
      // already redirected to Keycloak — the page is navigating away.
      scheduleTimerRetry(30)
    }
  }, delayMs)
}

function scheduleTimerRetry(delaySec: number) {
  refreshTimerId = setTimeout(async () => {
    refreshTimerId = null
    const ok = await refreshOnce()
    if (!ok) {
      scheduleTimerRetry(30)
    }
  }, delaySec * 1000)
}

async function initAutoRefresh() {
  const { userManager } = await import('@/shared/auth')

  const user = await userManager.getUser()
  if (user?.expires_in && user.expires_in > 0) {
    scheduleRefresh(user.expires_in)
  }

  userManager.events.addUserLoaded((u) => {
    if (u.expires_in) {
      scheduleRefresh(u.expires_in)
    }
  })

  userManager.events.addUserUnloaded(() => {
    if (refreshTimerId) {
      clearTimeout(refreshTimerId)
      refreshTimerId = null
    }
  })
}

initAutoRefresh()

async function failAuth() {
  localStorage.removeItem('access_token')
  localStorage.removeItem('refresh_token')
  const authority = `${KC_URL}/realms/${KC_REALM}`
  const clientId = KC_CLIENT
  localStorage.removeItem(`oidc.user:${authority}:${clientId}`)
  const { userManager } = await import('@/shared/auth')
  await userManager.signinRedirect()
}

/** localStorage key where oidc-client-ts stores the User object. */
function oidcStoreKey(): string {
  const url = KC_URL
  const realm = KC_REALM
  const clientId = KC_CLIENT
  return `oidc.user:${url}/realms/${realm}:${clientId}`
}

/**
 * Refresh tokens by delegating to oidc-client-ts's signinSilent().
 *
 * signinSilent() uses the refresh token grant (POST /token) when
 * user.refresh_token is populated, avoiding the iframe path.
 *
 * Known library quirk (UserManager.ts:370-377): if Keycloak's response omits
 * refresh_token, the spread { ...args.state, ...response } overwrites the old
 * one with undefined. We detect this and restore from the backup in localStorage.
 */
async function refreshToken(): Promise<boolean> {
  const refreshTokenValue = localStorage.getItem('refresh_token')
  if (!refreshTokenValue) return false

  const { userManager } = await import('@/shared/auth')

  try {
    await userManager.signinSilent()

    // signinSilent fires userLoaded → auth/index.ts mirrors access_token
    // and initAutoRefresh schedules the next refresh. But it may have dropped
    // the refresh_token in the oidc-client-ts store. Restore if needed.
    const oidcKey = oidcStoreKey()
    const stored = localStorage.getItem(oidcKey)
    if (stored) {
      const parsed = JSON.parse(stored) as { refresh_token?: string }
      if (!parsed.refresh_token) {
        parsed.refresh_token = refreshTokenValue
        localStorage.setItem(oidcKey, JSON.stringify(parsed))
      }
    }

    return true
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : String(err)
    console.warn('[refreshToken] signinSilent failed:', msg)

    // Permanent failure — redirect to login
    if (
      /\b(invalid_grant|login_required|interaction_required)\b/i.test(msg) ||
      msg.includes("doesn't have required client")
    ) {
      await failAuth()
    }

    return false
  }
}

client.interceptors.response.use(async (response, request, options) => {
  if (response.status !== 401) return response

  const clone = retryClones.get(request)
  retryClones.delete(request)
  if (!clone) {
    await failAuth()
    return response
  }

  const refreshed = await refreshOnce()
  if (!refreshed) {
    await failAuth()
    return response
  }

  const token = localStorage.getItem('access_token')
  if (!token) {
    await failAuth()
    return response
  }

  clone.headers.set('Authorization', `Bearer ${token}`)
  const doFetch = options.fetch ?? globalThis.fetch
  return doFetch(clone)
})
