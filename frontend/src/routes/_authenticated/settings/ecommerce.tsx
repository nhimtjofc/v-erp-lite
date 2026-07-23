import { createFileRoute, redirect } from '@tanstack/react-router'

export const Route = createFileRoute('/_authenticated/settings/ecommerce')({
  beforeLoad: () => {
    throw redirect({ to: '/settings/integrations' })
  },
})
