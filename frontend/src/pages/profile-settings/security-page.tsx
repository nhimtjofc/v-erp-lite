import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation } from '@tanstack/react-query'
import {
  useChangePasswordMutation,
  useVerifyMfaMutation,
  useDisableMfaMutation,
  useMfaStatusQuery,
  setupMfa,
} from '@/shared/api'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Badge } from '@/shared/ui/badge'
import { Separator } from '@/shared/ui/separator'
import {
  KeyRound,
  Shield,
  ShieldOff,
  ShieldEllipsis,
  Copy,
  Check,
  X,
} from 'lucide-react'
import QRCode from 'qrcode'

export function ProfileSecurityPage() {
  const { t } = useTranslation()

  // Password state
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [passwordSaving, setPasswordSaving] = useState(false)
  const [passwordMessage, setPasswordMessage] = useState('')
  const [passwordError, setPasswordError] = useState('')
  const changePasswordMutation = useChangePasswordMutation()

  // MFA state
  const [mfaSetup, setMfaSetup] = useState(false)
  const [mfaSecret, setMfaSecret] = useState('')
  const [mfaProvisioningUri, setMfaProvisioningUri] = useState('')
  const [mfaCode, setMfaCode] = useState('')
  const [mfaSaving, setMfaSaving] = useState(false)
  const [mfaMessage, setMfaMessage] = useState('')
  const [mfaError, setMfaError] = useState('')
  const [qrDataUrl, setQrDataUrl] = useState('')
  const [copied, setCopied] = useState(false)
  const verifyMfaMutation = useVerifyMfaMutation()
  const disableMfaMutation = useDisableMfaMutation()
  const setupMfaMutation = useMutation({
    mutationFn: async () => {
      const resp = await setupMfa()
      return resp.data as unknown as { secret: string; provisioningUri: string }
    },
  })
  const { data: mfaStatusData, isLoading: mfaStatusLoading } =
    useMfaStatusQuery()
  const mfaEnabled =
    (mfaStatusData as { mfaEnabled?: boolean } | undefined)?.mfaEnabled ??
    false
  const mfaStatus: 'loading' | 'enabled' | 'disabled' = mfaStatusLoading
    ? 'loading'
    : mfaEnabled
      ? 'enabled'
      : 'disabled'

  // Password handlers
  async function handlePasswordChange() {
    if (newPassword !== confirmPassword) {
      setPasswordError(t('profile:passwordsDoNotMatch'))
      return
    }
    if (newPassword.length < 8) {
      setPasswordError(t('profile:passwordMinLength') || 'Password must be at least 8 characters')
      return
    }
    setPasswordSaving(true)
    setPasswordMessage('')
    setPasswordError('')
    try {
      await changePasswordMutation.mutateAsync({ body: { newPassword } })
      setPasswordMessage(t('profile:passwordUpdated'))
      setNewPassword('')
      setConfirmPassword('')
    } catch {
      setPasswordError(t('profile:passwordChangeFailed'))
    } finally {
      setPasswordSaving(false)
      setTimeout(() => {
        setPasswordMessage('')
        setPasswordError('')
      }, 4000)
    }
  }

  // MFA handlers
  async function handleSetupMfa() {
    setMfaError('')
    try {
      const data = await setupMfaMutation.mutateAsync()
      setMfaSecret(data.secret)
      setMfaProvisioningUri(data.provisioningUri)
      setMfaSetup(true)
    } catch {
      setMfaError(t('profile:mfaSetupFailed'))
    }
  }

  useEffect(() => {
    if (!mfaProvisioningUri) return
    QRCode.toDataURL(mfaProvisioningUri, { width: 200, margin: 2 })
      .then(setQrDataUrl)
      .catch((err) => console.error('QR generation failed:', err))
  }, [mfaProvisioningUri])

  async function handleVerifyMfa() {
    setMfaSaving(true)
    setMfaMessage('')
    setMfaError('')
    try {
      await verifyMfaMutation.mutateAsync({ body: { code: mfaCode } })
      setMfaMessage(t('profile:mfaEnabledSuccess'))
      setMfaSetup(false)
      setMfaCode('')
    } catch {
      setMfaError(t('profile:mfaInvalidCode'))
    } finally {
      setMfaSaving(false)
      setTimeout(() => {
        setMfaMessage('')
        setMfaError('')
      }, 4000)
    }
  }

  async function handleDisableMfa() {
    setMfaSaving(true)
    setMfaError('')
    try {
      await disableMfaMutation.mutateAsync({})
      setMfaMessage(t('profile:mfaDisabled'))
    } catch {
      setMfaError(t('profile:mfaDisableFailed'))
    } finally {
      setMfaSaving(false)
      setTimeout(() => {
        setMfaMessage('')
        setMfaError('')
      }, 4000)
    }
  }

  function handleCopySecret() {
    navigator.clipboard.writeText(mfaSecret).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    })
  }

  return (
    <div className="mx-auto max-w-3xl">
      {/* Page Header */}
      <div className="pb-8">
        <h1 className="text-3xl font-bold tracking-tight">
          {t('nav:security') || 'Security'}
        </h1>
        <p className="text-muted-foreground mt-1">
          {t('profile:securityDesc') ||
            'Manage your password and multi-factor authentication settings'}
        </p>
      </div>

      {/* Password Change */}
      <section className="border-b border-border/50 pb-7 mb-7">
        <div className="flex items-center gap-2 mb-4">
          <KeyRound className="h-5 w-5 text-muted-foreground" />
          <h2 className="text-base font-semibold">{t('profile:password')}</h2>
        </div>
        <div className="space-y-4 max-w-sm">
          <div className="space-y-1.5">
            <Label htmlFor="newPassword">{t('profile:newPassword')}</Label>
            <Input
              id="newPassword"
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              placeholder={t('profile:newPasswordPlaceholder')}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="confirmPassword">
              {t('profile:confirmPassword')}
            </Label>
            <Input
              id="confirmPassword"
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              placeholder={t('profile:confirmPasswordPlaceholder')}
            />
          </div>
          {passwordMessage && (
            <p className="text-sm text-success">{passwordMessage}</p>
          )}
          {passwordError && (
            <p className="text-sm text-destructive">{passwordError}</p>
          )}
          <Button
            onClick={handlePasswordChange}
            disabled={
              passwordSaving ||
              !newPassword ||
              newPassword !== confirmPassword ||
              newPassword.length < 8
            }
            size="sm"
            className="gap-2"
          >
            {passwordSaving
              ? t('common:saving')
              : t('profile:changePassword')}
          </Button>
        </div>
      </section>

      {/* MFA */}
      <section className="border-b border-border/50 pb-7 mb-7">
        <div className="flex items-center gap-2 mb-4">
          <Shield className="h-5 w-5 text-muted-foreground" />
          <h2 className="text-base font-semibold">{t('profile:mfa')}</h2>
        </div>

        {mfaStatus === 'loading' && (
          <p className="text-sm text-muted-foreground">
            {t('profile:mfaChecking')}
          </p>
        )}

        {mfaStatus === 'enabled' && (
          <div className="space-y-4">
            <Badge variant="default">{t('profile:mfaEnabled')}</Badge>
            <p className="text-sm text-muted-foreground">
              {t('profile:mfaEnabledDesc')}
            </p>
            <Button
              variant="destructive"
              size="sm"
              onClick={handleDisableMfa}
              disabled={mfaSaving}
              className="gap-2"
            >
              <ShieldOff className="h-4 w-4" />
              {mfaSaving ? t('common:saving') : t('profile:disableMfa')}
            </Button>
          </div>
        )}

        {mfaStatus === 'disabled' && !mfaSetup && (
          <Button
            onClick={handleSetupMfa}
            disabled={setupMfaMutation.isPending || mfaSaving}
            size="sm"
            className="gap-2"
          >
            <ShieldEllipsis className="h-4 w-4" />
            {setupMfaMutation.isPending ? t('common:saving') : t('profile:setupMfa')}
          </Button>
        )}

        {mfaSetup && (
          <div className="space-y-4">
            <div className="flex justify-center">
              {qrDataUrl ? (
                <img
                  src={qrDataUrl}
                  alt="MFA QR Code"
                  className="rounded-lg border"
                  width={200}
                  height={200}
                />
              ) : (
                <div className="flex h-[200px] w-[200px] items-center justify-center rounded-lg border bg-muted">
                  <span className="text-xs text-muted-foreground">
                    {t('common:loading')}
                  </span>
                </div>
              )}
            </div>
            <p className="text-sm text-muted-foreground">
              {t('profile:mfaScanInstruction')}
            </p>
            <div className="space-y-1.5">
              <Label className="text-xs text-muted-foreground">
                {t('profile:mfaSecretKey')}
              </Label>
              <div className="flex items-center gap-2 max-w-sm">
                <code className="flex-1 rounded-md border bg-muted px-3 py-2 font-mono text-sm">
                  {mfaSecret}
                </code>
                <Button
                  variant="outline"
                  size="icon"
                  className="h-9 w-9 shrink-0"
                  onClick={handleCopySecret}
                >
                  {copied ? (
                    <Check className="h-4 w-4 text-success" />
                  ) : (
                    <Copy className="h-4 w-4" />
                  )}
                </Button>
              </div>
            </div>
            <Separator />
            <div className="space-y-1.5">
              <Label htmlFor="mfaCode">{t('profile:mfaCodeLabel')}</Label>
              <Input
                id="mfaCode"
                value={mfaCode}
                onChange={(e) => setMfaCode(e.target.value)}
                placeholder="000000"
                maxLength={6}
                className="w-40"
              />
            </div>
            {mfaMessage && (
              <p className="text-sm text-success">{mfaMessage}</p>
            )}
            {mfaError && (
              <p className="text-sm text-destructive">{mfaError}</p>
            )}
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setMfaSetup(false)}
              >
                {t('common:cancel')}
              </Button>
              <Button
                size="sm"
                onClick={handleVerifyMfa}
                disabled={mfaSaving || mfaCode.length !== 6}
                className="gap-2"
              >
                {mfaSaving
                  ? t('common:saving')
                  : t('profile:verifyAndEnable')}
              </Button>
            </div>
          </div>
        )}
      </section>

      {/* Messages */}
      {mfaMessage && !mfaSetup && (
        <div className="flex items-center gap-2 text-sm text-success mb-4">
          <Check className="h-4 w-4" />
          {mfaMessage}
        </div>
      )}
      {mfaError && !mfaSetup && (
        <div className="flex items-center gap-2 text-sm text-destructive mb-4">
          <X className="h-4 w-4" />
          {mfaError}
        </div>
      )}
    </div>
  )
}
