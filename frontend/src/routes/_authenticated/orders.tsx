import { createFileRoute, Outlet, useRouter } from '@tanstack/react-router'
import { useMeQuery } from '@/shared/api/gen/@tanstack/react-query.gen'

export const Route = createFileRoute('/_authenticated/orders')({
  component: () => {
    const router = useRouter()
    const { data: me } = useMeQuery()
    const role = me?.role?.name

    if (!role) return null

    if (role !== 'admin' && role !== 'businessOwner') {
      router.navigate({ to: '/dashboard', replace: true })
      return null
    }

    return <Outlet />
  },
})
