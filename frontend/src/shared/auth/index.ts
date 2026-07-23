import { User, UserManager, WebStorageStateStore } from 'oidc-client-ts'

const KEYCLOAK_URL = import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8181'
const KEYCLOAK_REALM = import.meta.env.VITE_KEYCLOAK_REALM || 'v-erp-lite'
const KEYCLOAK_CLIENT_ID = import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'v-erp-app'

const origin = window.location.origin

const userManager = new UserManager({
  authority: `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}`,
  client_id: KEYCLOAK_CLIENT_ID,
  redirect_uri: `${origin}/auth-callback`,
  post_logout_redirect_uri: `${origin}/login`,
  response_type: 'code',
  scope: 'openid profile email roles',
  userStore: new WebStorageStateStore({ store: window.localStorage }),
  automaticSilentRenew: false,
})

import { useAuthStore } from '../store/auth-store'

// Mirror tokens to plain localStorage keys so the hey-api client can read them synchronously
userManager.events.addUserLoaded((user) => {
  localStorage.setItem('access_token', user.access_token)
  if (user.refresh_token) {
    localStorage.setItem('refresh_token', user.refresh_token)
  }
  useAuthStore.getState().setUser(user)
})

// Clear mirrored tokens on logout
userManager.events.addUserUnloaded(() => {
  localStorage.removeItem('access_token')
  localStorage.removeItem('refresh_token')
  useAuthStore.getState().setUser(null)
})

// Bootstrap: if a user session already exists, mirror its tokens now
userManager.getUser().then((user) => {
  if (user) {
    localStorage.setItem('access_token', user.access_token)
    if (user.refresh_token) {
      localStorage.setItem('refresh_token', user.refresh_token)
    }
    useAuthStore.getState().setUser(user)
  }
  useAuthStore.getState().setInitialized(true)
})

export { userManager }
export type { User }

export async function getUser(): Promise<User | null> {
  const user = await userManager.getUser()
  return user
}

export async function login(): Promise<void> {
  await userManager.signinRedirect()
}

export async function loginWithGoogle(): Promise<void> {
  await userManager.signinRedirect({
    extraQueryParams: { kc_idp_hint: 'google' },
  })
}

export async function handleCallback(): Promise<User> {
  const user = await userManager.signinRedirectCallback()
  return user
}

export async function logout(): Promise<void> {
  const authority = `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}`
  const oidcUserKey = `oidc.user:${authority}:${KEYCLOAK_CLIENT_ID}`
  const storedUser = localStorage.getItem(oidcUserKey)
  let idTokenHint: string | undefined

  if (storedUser) {
    try {
      idTokenHint = (JSON.parse(storedUser) as { id_token?: string }).id_token
    } catch {
      // A corrupt stored session must not prevent the user from signing out.
    }
  }

  // Clear all local authentication state synchronously so no stale user can
  // redirect /login back into the authenticated application.
  localStorage.removeItem('access_token')
  localStorage.removeItem('refresh_token')
  localStorage.removeItem(oidcUserKey)
  useAuthStore.getState().setUser(null)

  const logoutUrl = new URL(`${authority}/protocol/openid-connect/logout`)
  logoutUrl.searchParams.set('client_id', KEYCLOAK_CLIENT_ID)
  logoutUrl.searchParams.set('post_logout_redirect_uri', `${origin}/login`)
  if (idTokenHint) {
    logoutUrl.searchParams.set('id_token_hint', idTokenHint)
  }

  window.location.replace(logoutUrl.toString())
}

export async function getAccessToken(): Promise<string | null> {
  const user = await userManager.getUser()
  return user?.access_token ?? null
}

export async function forgotPassword(): Promise<void> {
  const origin = window.location.origin
  const redirectUri = encodeURIComponent(`${origin}/login`)
  window.location.href = `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/login-actions/reset-credentials?client_id=${KEYCLOAK_CLIENT_ID}&redirect_uri=${redirectUri}`
}
