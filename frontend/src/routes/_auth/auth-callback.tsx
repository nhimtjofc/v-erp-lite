import { createFileRoute, redirect } from '@tanstack/react-router'
import { handleCallback } from '@/shared/auth'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { AuthCallbackPage } from '@/pages/auth-callback-page'

export const Route = createFileRoute('/_auth/auth-callback')({
  loader: async () => {
    try {
      await handleCallback()
    } catch (err) {
      console.error('Auth callback error:', err)
      return { error: 'Authentication failed. Please try again.' }
    }
    throw redirect({ to: '/dashboard' })
  },
  pendingComponent: () => (
    <div className="flex min-h-screen items-center justify-center">
      <Card className="w-[380px]">
        <CardHeader className="text-center">
          <CardTitle>Signing you in...</CardTitle>
        </CardHeader>
        <CardContent className="text-center text-sm text-muted-foreground">
          Please wait while we complete authentication.
        </CardContent>
      </Card>
    </div>
  ),
  component: AuthCallbackPage,
})
