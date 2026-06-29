import { createFileRoute, Outlet, useRouter } from '@tanstack/react-router'
import { useMeQuery } from '@/shared/api/gen/@tanstack/react-query.gen'

export const Route = createFileRoute('/_authenticated/orders')({
  component: () => {
    const router = useRouter()
    const { data: me } = useMeQuery()

    if (me?.role?.name === 'operator') {
      router.navigate({ to: '/dashboard', replace: true })
      return null
    }

    return <Outlet />
  },
})
