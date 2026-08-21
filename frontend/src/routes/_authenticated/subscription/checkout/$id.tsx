import { createFileRoute, Outlet } from '@tanstack/react-router'

export const Route = createFileRoute('/_authenticated/subscription/checkout/$id')({
  component: () => <Outlet />,
})