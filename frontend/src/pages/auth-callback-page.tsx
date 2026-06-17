import { getRouteApi } from '@tanstack/react-router'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'

const routeApi = getRouteApi('/_auth/auth-callback')

export function AuthCallbackPage() {
  const data = routeApi.useLoaderData()

  return (
    <div className="flex min-h-screen items-center justify-center">
      <Card className="w-[380px]">
        <CardHeader>
          <CardTitle className="text-destructive">Authentication Error</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">
            {data?.error ?? 'Authentication failed.'}
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
