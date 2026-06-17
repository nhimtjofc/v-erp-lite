import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { getUser, login } from '@/shared/auth'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'
import { useRouter } from '@tanstack/react-router'

export function LoginPage() {
  const router = useRouter()
  const { t } = useTranslation()
  const [checking, setChecking] = useState(true)

  useEffect(() => {
    getUser().then((user) => {
      if (user) {
        router.navigate({ to: '/dashboard' })
      } else {
        setChecking(false)
      }
    })
  }, [])

  if (checking) {
    return (
      <Card className="w-[380px]">
        <CardHeader className="text-center">
          <CardTitle>{t('nav:appName', 'V-ERP Lite')}</CardTitle>
          <CardDescription>{t('common:checking', 'Checking authentication...')}</CardDescription>
        </CardHeader>
      </Card>
    )
  }

  return (
    <Card className="w-[380px]">
      <CardHeader className="text-center">
        <CardTitle className="text-2xl">{t('nav:appName', 'V-ERP Lite')}</CardTitle>
        <CardDescription>{t('common:signInDescription', 'Sign in with your Keycloak account')}</CardDescription>
      </CardHeader>
      <CardContent>
        <Button className="w-full" size="lg" onClick={() => login()}>
          {t('common:signIn', 'Sign in with Keycloak')}
        </Button>
      </CardContent>
    </Card>
  )
}
