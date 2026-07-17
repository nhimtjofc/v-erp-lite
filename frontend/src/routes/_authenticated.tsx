import { useEffect } from 'react'
import { createFileRoute, Outlet, redirect, useRouter, useRouterState } from '@tanstack/react-router'
import { getUser } from '@/shared/auth'
import { useMeQuery } from '@/shared/api'
import { AppLayout } from '@/app/layouts/app-layout'
import { CustomerLayout } from '@/app/layouts/customer-layout'
import { ChatWidget } from '@/shared/ui/chat-widget'

export const Route = createFileRoute('/_authenticated')({
  beforeLoad: async () => {
    const user = await getUser()
    if (!user) {
      throw redirect({ to: '/login' })
    }
  },
  component: AuthenticatedShell,
})

function AuthenticatedShell() {
  const router = useRouter()
  const pathname = useRouterState({ select: (state) => state.location.pathname })
  const { data: me, isLoading } = useMeQuery()
  const roleName = me?.role?.name
  const isStorefrontUser = roleName === 'customer' || roleName === 'user'

  useEffect(() => {
    if (!isStorefrontUser) return

    const customerAllowed =
      pathname.startsWith('/shop') ||
      pathname.startsWith('/cart') ||
      pathname.startsWith('/orders') ||
      pathname.startsWith('/profile')

    if (!customerAllowed) {
      router.navigate({ to: '/shop', replace: true })
    }
  }, [isStorefrontUser, pathname, router])

  if (isLoading) {
    return (
      <div className="flex h-screen items-center justify-center bg-background text-sm text-muted-foreground">
        Loading workspace...
      </div>
    )
  }

  if (isStorefrontUser) {
    return (
      <>
        <CustomerLayout>
          <Outlet />
        </CustomerLayout>
        <ChatWidget />
      </>
    )
  }

  return (
    <>
      <AppLayout>
        <Outlet />
      </AppLayout>
      <ChatWidget />
    </>
  )
}
