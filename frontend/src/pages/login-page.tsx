import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { getUser, login, loginWithGoogle } from '@/shared/auth'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'
import { useRouter } from '@tanstack/react-router'
import { useRegisterMutation } from '@/shared/api'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'

export function LoginPage() {
  const router = useRouter()
  const { t } = useTranslation()
  const [checking, setChecking] = useState(true)
  const [isRegister, setIsRegister] = useState(false)

  // Registration Form State
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  const registerMutation = useRegisterMutation()

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
      <Card className="w-[380px] border-border shadow-2xl">
        <CardHeader className="text-center">
          <CardTitle className="bg-gradient-to-r from-primary to-indigo-600 bg-clip-text text-transparent">{t('nav:appName', 'V-ERP Lite')}</CardTitle>
          <CardDescription>{t('common:checking', 'Checking authentication...')}</CardDescription>
        </CardHeader>
      </Card>
    )
  }

  const handleRegisterSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!firstName.trim() || !lastName.trim() || !email.trim() || !password.trim()) {
      setError('Please fill in all required fields')
      return
    }
    if (password.length < 8) {
      setError('Password must be at least 8 characters')
      return
    }

    setError('')
    setSuccess('')

    registerMutation.mutate(
      {
        body: {
          firstName: firstName.trim(),
          lastName: lastName.trim(),
          email: email.trim().toLowerCase(),
          phone: phone.trim() || undefined,
          password: password,
        },
      },
      {
        onSuccess: () => {
          setSuccess('Đăng ký tài khoản thành công! Bạn có thể đăng nhập ngay.')
          setFirstName('')
          setLastName('')
          setEmail('')
          setPhone('')
          setPassword('')
        },
        onError: (err: any) => {
          setError(err?.body?.message || err?.message || 'Đăng ký thất bại')
        },
      }
    )
  }

  if (isRegister) {
    return (
      <Card className="w-[400px] border-border shadow-2xl transition-all duration-300">
        <CardHeader className="text-center pb-2">
          <CardTitle className="text-2xl font-black bg-gradient-to-r from-primary to-indigo-600 bg-clip-text text-transparent">
            {t('nav:appName', 'V-ERP Lite')}
          </CardTitle>
          <CardDescription className="text-sm font-semibold text-muted-foreground">
            Đăng ký tài khoản khách hàng mới
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {error && (
            <div className="text-xs font-semibold text-destructive bg-destructive/10 p-2.5 rounded-lg border border-destructive/20">
              {error}
            </div>
          )}
          {success && (
            <div className="text-xs font-semibold text-success bg-success/10 p-2.5 rounded-lg border border-success/20">
              {success}
            </div>
          )}

          <form onSubmit={handleRegisterSubmit} className="space-y-3">
            <div className="grid grid-cols-2 gap-2">
              <div>
                <Label className="text-xs font-bold text-muted-foreground uppercase">Họ *</Label>
                <Input
                  className="h-9 text-sm"
                  placeholder="Họ"
                  value={lastName}
                  onChange={(e) => setLastName(e.target.value)}
                  required
                />
              </div>
              <div>
                <Label className="text-xs font-bold text-muted-foreground uppercase">Tên *</Label>
                <Input
                  className="h-9 text-sm"
                  placeholder="Tên"
                  value={firstName}
                  onChange={(e) => setFirstName(e.target.value)}
                  required
                />
              </div>
            </div>

            <div>
              <Label className="text-xs font-bold text-muted-foreground uppercase">Số điện thoại</Label>
              <Input
                className="h-9 text-sm"
                placeholder="Số điện thoại"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
              />
            </div>

            <div>
              <Label className="text-xs font-bold text-muted-foreground uppercase">Email *</Label>
              <Input
                type="email"
                className="h-9 text-sm"
                placeholder="email@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </div>

            <div>
              <Label className="text-xs font-bold text-muted-foreground uppercase">Mật khẩu *</Label>
              <Input
                type="password"
                className="h-9 text-sm"
                placeholder="Mật khẩu (tối thiểu 8 ký tự)"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>

            <Button type="submit" className="w-full h-10 font-bold mt-2 cursor-pointer shadow-md" disabled={registerMutation.isPending}>
              {registerMutation.isPending ? 'Đang đăng ký...' : 'Đăng ký'}
            </Button>
          </form>

          <div className="text-center pt-2 border-t border-border/55">
            <button
              onClick={() => {
                setIsRegister(false)
                setError('')
                setSuccess('')
              }}
              className="text-xs font-semibold text-primary hover:underline cursor-pointer"
            >
              Đã có tài khoản? Đăng nhập bằng Keycloak
            </button>
          </div>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card className="w-[380px] border-border shadow-2xl transition-all duration-300">
      <CardHeader className="text-center pb-2">
        <CardTitle className="text-2xl font-black bg-gradient-to-r from-primary to-indigo-600 bg-clip-text text-transparent">
          {t('nav:appName', 'V-ERP Lite')}
        </CardTitle>
        <CardDescription>{t('common:signInDescription', 'Sign in with your Keycloak account')}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <Button className="w-full h-11 text-base font-bold shadow-lg cursor-pointer bg-indigo-600 hover:bg-indigo-700 text-white" size="lg" onClick={() => login()}>
          {t('common:signIn', 'Sign in with Keycloak')}
        </Button>
        <Button
          variant="outline"
          className="w-full h-11 text-sm font-bold shadow-md cursor-pointer flex items-center justify-center gap-2 border-border hover:bg-muted"
          size="lg"
          onClick={() => loginWithGoogle()}
        >
          <svg className="w-5 h-5" viewBox="0 0 24 24">
            <path
              fill="#4285F4"
              d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
            />
            <path
              fill="#34A853"
              d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
            />
            <path
              fill="#FBBC05"
              d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"
            />
            <path
              fill="#EA4335"
              d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"
            />
          </svg>
          {t('common:signInGoogle', 'Sign in with Google')}
        </Button>
        <div className="text-center pt-2 border-t border-border/55">
          <button
            onClick={() => {
              setIsRegister(true)
              setError('')
              setSuccess('')
            }}
            className="text-xs font-semibold text-primary hover:underline cursor-pointer"
          >
            Chưa có tài khoản? Đăng ký tại đây
          </button>
        </div>
      </CardContent>
    </Card>
  )
}
