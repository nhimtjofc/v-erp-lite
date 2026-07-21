import { useEffect } from 'react'
import { createFileRoute, useRouter } from '@tanstack/react-router'
import { CartPage } from '@/pages/cart-page'
import { useMeQuery } from '@/shared/api'

export const Route = createFileRoute('/_authenticated/cart')({
  component: CartRoute,
})

function CartRoute() {
  const router = useRouter()
  const { data: me, isLoading } = useMeQuery()
  const roleName = me?.role?.name
  const isStorefrontUser = roleName === 'customer' || roleName === 'user'

  useEffect(() => {
    if (!isLoading && roleName && !isStorefrontUser) {
      router.navigate({ to: '/dashboard', replace: true })
    }
  }, [isLoading, isStorefrontUser, roleName, router])

  if (isLoading || !roleName || !isStorefrontUser) return null

  return <CartPage />
}
