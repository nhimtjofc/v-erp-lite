import { createFileRoute, redirect } from '@tanstack/react-router'

export const Route = createFileRoute('/_authenticated/settings/woocommerce')({
  beforeLoad: () => {
    throw redirect({ to: '/settings/integrations' })
  },
})
