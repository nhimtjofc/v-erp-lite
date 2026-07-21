import { createFileRoute, Outlet, useRouter, useRouterState } from '@tanstack/react-router'
import { useMeQuery } from '@/shared/api/gen/@tanstack/react-query.gen'
import { useEffect } from 'react'

export const Route = createFileRoute('/_authenticated/orders')({
  component: () => {
    const router = useRouter()
    const pathname = useRouterState({ select: (state) => state.location.pathname })
    const { data: me } = useMeQuery()
    const role = me?.role?.name
    const isStaff = role === 'admin' || role === 'businessOwner' || role === 'operator'
    const isStorefrontUser = role === 'customer' || role === 'user'
    const isStorefrontOrderFlow = pathname.startsWith('/orders/payment')
      || (/^\/orders\/[^/]+$/.test(pathname) && pathname !== '/orders/new')
    const hasAccess = isStaff || (isStorefrontUser && isStorefrontOrderFlow)

    useEffect(() => {
      if (role && !hasAccess) {
        router.navigate({ to: isStorefrontUser ? '/shop' : '/dashboard', replace: true })
      }
    }, [hasAccess, isStorefrontUser, role, router])

    if (!role) return null

    if (!hasAccess) return null

    return <Outlet />
  },
})
