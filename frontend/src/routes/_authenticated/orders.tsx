import { createFileRoute, Outlet, useRouter } from '@tanstack/react-router'
import { useMeQuery } from '@/shared/api/gen/@tanstack/react-query.gen'
import { useEffect } from 'react'

export const Route = createFileRoute('/_authenticated/orders')({
  component: () => {
    const router = useRouter()
    const { data: me } = useMeQuery()
    const role = me?.role?.name

    useEffect(() => {
      if (role && role !== 'admin' && role !== 'businessOwner' && role !== 'operator') {
        router.navigate({ to: '/dashboard', replace: true })
      }
    }, [role, router])

    if (!role) return null

    if (role !== 'admin' && role !== 'businessOwner' && role !== 'operator') {
      return null
    }

    return <Outlet />
  },
})
