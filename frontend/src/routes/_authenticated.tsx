import { createFileRoute, Outlet, redirect } from '@tanstack/react-router'
import { getUser } from '@/shared/auth'
import { AppLayout } from '@/app/layouts/app-layout'

export const Route = createFileRoute('/_authenticated')({
  beforeLoad: async () => {
    const user = await getUser()
    if (!user) {
      throw redirect({ to: '/login' })
    }
  },
  component: () => (
    <AppLayout>
      <Outlet />
    </AppLayout>
  ),
})
