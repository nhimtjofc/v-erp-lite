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
    
    // If navigating to a completely different top-level route (e.g. /cart, /profile),
    // this layout is unmounting. Do not hijack the navigation.
    const isLeavingRoute = !pathname.startsWith('/orders')
    
    const isStorefrontOrderFlow = pathname === '/orders' || pathname === '/orders/' || pathname.startsWith('/orders/payment')
      || (/^\/orders\/[^/]+$/.test(pathname) && pathname !== '/orders/new')
      
    const hasAccess = isStaff || (isStorefrontUser && isStorefrontOrderFlow)

    useEffect(() => {
      if (role && !hasAccess && !isLeavingRoute) {
        router.navigate({ to: isStorefrontUser ? '/shop' : '/dashboard', replace: true })
      }
    }, [hasAccess, isStorefrontUser, role, router, isLeavingRoute])

    if (!role) return null

    if (!hasAccess && !isLeavingRoute) return null

    return <Outlet />
  },
})
