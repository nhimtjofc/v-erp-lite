import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import i18n from '@/i18n/config'
import { getUser } from '@/shared/auth'
import {
  useMeQuery,
  useUpdateMeMutation,
  useChangePasswordMutation,
  useVerifyMfaMutation,
  useDisableMfaMutation,
  useMfaStatusQuery,
  useGetUserSettingsQuery,
  useGetEffectiveSettingsQuery,
  usePutUserSettingsMutation,
  setupMfa,
} from '@/shared/api'
import { Badge } from '@/shared/ui/badge'
import { Avatar, AvatarFallback } from '@/shared/ui/avatar'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Switch } from '@/shared/ui/switch'
import { Separator } from '@/shared/ui/separator'
import { useMutation } from '@tanstack/react-query'
import type { User as OidcUser } from 'oidc-client-ts'
import { useTheme } from '@/shared/hooks/use-theme'
import { getInitials } from '@/shared/utils/user'
import {
  Sun,
  Moon,
  Globe,
  Phone,
  Link,
  Briefcase,
  Mail,
  Shield,
  AlertTriangle,
  Pencil,
  Check,
  X,
  Clock,
  User as UserIcon,
  KeyRound,
  ShieldOff,
  ShieldEllipsis,
  Copy,
} from 'lucide-react'
import QRCode from 'qrcode'

const TIMEZONES = [
  'UTC',
  'America/New_York',
  'America/Chicago',
  'America/Denver',
  'America/Los_Angeles',
  'Europe/London',
  'Europe/Paris',
  'Europe/Berlin',
  'Asia/Tokyo',
  'Asia/Shanghai',
  'Asia/Singapore',
  'Australia/Sydney',
  'Pacific/Auckland',
]

function getDisplayName(oidcUser: OidcUser | null) {
  return (
    oidcUser?.profile?.name ||
    oidcUser?.profile?.preferred_username ||
    'User'
  )
}
export function ProfilePage() {
  const [oidcUser, setOidcUser] = useState<OidcUser | null>(null)
  const { data: profile, isLoading } = useMeQuery()
  const updateMeMutation = useUpdateMeMutation()
  const { data: userSettingsRaw } = useGetUserSettingsQuery()
  const { data: effectiveRaw } = useGetEffectiveSettingsQuery()
  const putUserSettingsMutation = usePutUserSettingsMutation()
  const { t } = useTranslation()
  const { isDark, setIsDark } = useTheme()

  const [editing, setEditing] = useState(false)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState<{
    type: 'success' | 'error'
    text: string
  } | null>(null)

  // Form fields
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [phone, setPhone] = useState('')
  const [jobTitle, setJobTitle] = useState('')
  const [bio, setBio] = useState('')
  const [timezone, setTimezone] = useState('UTC')
  const [websiteUrl, setWebsiteUrl] = useState('')

  // User preference overrides
  const [prefLanguage, setPrefLanguage] = useState('')
  const [prefDateFormat, setPrefDateFormat] = useState('')
  const [prefTimeFormat, setPrefTimeFormat] = useState('')

  // Effective tenant defaults for display
  const effectiveSettings = effectiveRaw as { regional?: Record<string, string> } | undefined
  const defaultLanguage = effectiveSettings?.regional?.language ?? 'en'
  const defaultDateFormat = effectiveSettings?.regional?.dateFormat ?? 'YYYY-MM-DD'
  const defaultTimeFormat = effectiveSettings?.regional?.timeFormat ?? '24h'

  const LANGUAGE_LABELS: Record<string, string> = {
    en: 'English', vi: 'Tiếng Việt', ja: '日本語', zh: '中文', ko: '한국어', de: 'Deutsch', fr: 'Français',
  }
  const DATE_FORMAT_LABELS: Record<string, string> = {
    'YYYY-MM-DD': 'YYYY-MM-DD (2024-12-31)',
    'DD/MM/YYYY': 'DD/MM/YYYY (31/12/2024)',
    'MM/DD/YYYY': 'MM/DD/YYYY (12/31/2024)',
    'DD.MM.YYYY': 'DD.MM.YYYY (31.12.2024)',
  }
  const TIME_FORMAT_LABELS: Record<string, string> = {
    '24h': '24-hour (14:30)',
    '12h': '12-hour (2:30 PM)',
  }

  // Password
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [passwordSaving, setPasswordSaving] = useState(false)
  const [passwordMessage, setPasswordMessage] = useState('')
  const [passwordError, setPasswordError] = useState('')
  const changePasswordMutation = useChangePasswordMutation()

  // MFA
  const [mfaSetup, setMfaSetup] = useState(false)
  const [mfaSecret, setMfaSecret] = useState('')
  const [mfaProvisioningUri, setMfaProvisioningUri] = useState('')
  const [mfaCode, setMfaCode] = useState('')
  const [mfaSaving, setMfaSaving] = useState(false)
  const [mfaMessage, setMfaMessage] = useState('')
  const [mfaError, setMfaError] = useState('')
  const [qrDataUrl, setQrDataUrl] = useState('')
  const verifyMfaMutation = useVerifyMfaMutation()
  const disableMfaMutation = useDisableMfaMutation()
  const setupMfaMutation = useMutation({
    mutationFn: async () => {
      const resp = await setupMfa()
      return resp.data as unknown as { secret: string; provisioningUri: string }
    },
  })
  const { data: mfaStatusData, isLoading: mfaStatusLoading } = useMfaStatusQuery()
  const mfaEnabled = (mfaStatusData as { mfaEnabled?: boolean } | undefined)?.mfaEnabled ?? false
  const mfaStatus: 'loading' | 'enabled' | 'disabled' = mfaStatusLoading ? 'loading' : mfaEnabled ? 'enabled' : 'disabled'

  useEffect(() => {
    getUser().then(setOidcUser)
  }, [])

  useEffect(() => {
    if (profile) {
      setFirstName(profile.firstName ?? '')
      setLastName(profile.lastName ?? '')
      setPhone(profile.phone ?? '')
      setJobTitle(profile.jobTitle ?? '')
      setBio(profile.bio ?? '')
      setTimezone(profile.timezone ?? 'UTC')
      setWebsiteUrl(profile.websiteUrl ?? '')
    }
  }, [profile])

  useEffect(() => {
    const us = userSettingsRaw as { regional?: Record<string, string> } | undefined
    if (us?.regional) {
      setPrefLanguage(us.regional.language || '')
      setPrefDateFormat(us.regional.dateFormat || '')
      setPrefTimeFormat(us.regional.timeFormat || '')
    }
  }, [userSettingsRaw])

  // Sync language from user settings to i18next on initial load
  useEffect(() => {
    const us = userSettingsRaw as { regional?: Record<string, string> } | undefined
    const lang = us?.regional?.language
    if (lang && lang !== i18n.language) {
      localStorage.setItem('language', lang)
      i18n.changeLanguage(lang)
    }
  }, [userSettingsRaw])

  function resetFields() {
    if (!profile) return
    setFirstName(profile.firstName ?? '')
    setLastName(profile.lastName ?? '')
    setPhone(profile.phone ?? '')
    setJobTitle(profile.jobTitle ?? '')
    setBio(profile.bio ?? '')
    setTimezone(profile.timezone ?? 'UTC')
    setWebsiteUrl(profile.websiteUrl ?? '')
    const us = userSettingsRaw as { regional?: Record<string, string> } | undefined
    setPrefLanguage(us?.regional?.language || '')
    setPrefDateFormat(us?.regional?.dateFormat || '')
    setPrefTimeFormat(us?.regional?.timeFormat || '')
  }

  async function handleSave() {
    setSaving(true)
    setMessage(null)
    try {
      await updateMeMutation.mutateAsync({
        body: {
          firstName: firstName || undefined,
          lastName: lastName || undefined,
          phone: phone || undefined,
          jobTitle: jobTitle || undefined,
          bio: bio || undefined,
          timezone: timezone || undefined,
          websiteUrl: websiteUrl || undefined,
        },
      })
      // Save user preference overrides
      const regional: Record<string, string> = {}
      if (prefLanguage) regional.language = prefLanguage
      if (prefDateFormat) regional.dateFormat = prefDateFormat
      if (prefTimeFormat) regional.timeFormat = prefTimeFormat
      if (Object.keys(regional).length > 0) {
        await putUserSettingsMutation.mutateAsync({ body: { settings: { regional } } as Record<string, unknown> })
      }
      // Sync language to i18next immediately
      if (prefLanguage) {
        localStorage.setItem('language', prefLanguage)
        i18n.changeLanguage(prefLanguage)
      }
      setEditing(false)
      setMessage({ type: 'success', text: t('profile:profileUpdated') })
    } catch {
      setMessage({ type: 'error', text: t('profile:profileUpdateFailed') })
    } finally {
      setSaving(false)
      setTimeout(() => setMessage(null), 4000)
    }
  }

  function handleCancel() {
    resetFields()
    setEditing(false)
    setMessage(null)
  }

  async function handlePasswordChange() {
    if (newPassword !== confirmPassword) {
      setPasswordError(t('profile:passwordsDoNotMatch'))
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
      setTimeout(() => { setPasswordMessage(''); setPasswordError('') }, 4000)
    }
  }

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
      setTimeout(() => { setMfaMessage(''); setMfaError('') }, 4000)
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
      setTimeout(() => { setMfaMessage(''); setMfaError('') }, 4000)
    }
  }

  const initials = getInitials(oidcUser)
  const displayName = getDisplayName(oidcUser)

  if (isLoading) {
    return (
      <p className="text-muted-foreground py-12 text-center">Loading profile...</p>
    )
  }

  return (
    <div className="mx-auto max-w-3xl">
      {/* Page Header */}
      <div className="flex items-start justify-between gap-4 pb-8">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('profile:title')}</h1>
          <p className="text-muted-foreground mt-1">
            {t('profile:description')}
          </p>
        </div>
        {!editing && (
          <Button
            variant="outline"
            size="sm"
            onClick={() => setEditing(true)}
            className="shrink-0 gap-2"
          >
            <Pencil className="h-3.5 w-3.5" />
            {t('common:edit')}
          </Button>
        )}
      </div>

      {/* Identity */}
      <section className="border-b border-border/50 pb-7 mb-7">
        <div className="flex flex-col items-center gap-4 sm:flex-row sm:items-start">
          <Avatar className="h-20 w-20 ring-2 ring-border">
            <AvatarFallback className="text-xl font-semibold">
              {initials}
            </AvatarFallback>
          </Avatar>
          <div className="flex-1 text-center sm:text-left">
            <h2 className="text-xl font-semibold tracking-tight">
              {displayName}
            </h2>
            {editing ? (
              <div className="mt-2 grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="jobTitle">{t('profile:jobTitle')}</Label>
                  <div className="relative">
                    <Briefcase className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
                    <Input
                      id="jobTitle"
                      value={jobTitle}
                      onChange={(e) => setJobTitle(e.target.value)}
                  placeholder={t('profile:jobTitlePlaceholder')}
                       className="pl-9"
                     />
                   </div>
                 </div>
                 <div className="space-y-1.5">
                   <Label htmlFor="website">{t('profile:website')}</Label>
                  <div className="relative">
                    <Link className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
                    <Input
                      id="website"
                      type="url"
                      value={websiteUrl}
                      onChange={(e) => setWebsiteUrl(e.target.value)}
                      placeholder={t('profile:websitePlaceholder')}
                       className="pl-9"
                     />
                   </div>
                 </div>
               </div>
             ) : (
               <div className="mt-1 space-y-0.5">
                 <p className="text-sm text-muted-foreground">
                   {profile?.jobTitle || t('profile:noJobTitle')}
                </p>
                <p className="text-sm text-muted-foreground">
                  {oidcUser?.profile?.email}
                </p>
                {profile?.websiteUrl && (
                  <a
                    href={profile.websiteUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-1 text-sm text-primary hover:underline"
                  >
                    <Link className="h-3.5 w-3.5" />
                    {profile.websiteUrl}
                  </a>
                )}
              </div>
            )}
          </div>
        </div>
      </section>

      {/* About */}
      <section className="border-b border-border/50 pb-7 mb-7">
        <div className="flex items-center gap-2 mb-4">
          <UserIcon className="h-5 w-5 text-muted-foreground" />
          <h2 className="text-base font-semibold">{t('profile:about')}</h2>
        </div>
        {editing ? (
          <div className="space-y-1.5">
            <Label htmlFor="bio">{t('profile:bio')}</Label>
            <textarea
              id="bio"
              value={bio}
              onChange={(e) => setBio(e.target.value)}
              placeholder={t('profile:bioPlaceholder')}
               rows={4}
              className="flex w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 resize-y min-h-[80px]"
            />
          </div>
        ) : (
          <p className="text-sm leading-relaxed text-foreground/80">
            {profile?.bio || (
              <span className="italic text-muted-foreground">
                {t('profile:noBio')}
              </span>
            )}
          </p>
        )}
      </section>

      {/* Contact & Work */}
      <section className="border-b border-border/50 pb-7 mb-7">
        <div className="grid grid-cols-1 gap-8 md:grid-cols-2">
          <div>
            <div className="flex items-center gap-2 mb-4">
              <Mail className="h-5 w-5 text-muted-foreground" />
              <h2 className="text-base font-semibold">{t('profile:contact')}</h2>
            </div>
            <div className="space-y-4">
              <div className="space-y-1.5">
                <Label className="text-xs text-muted-foreground">{t('profile:email')}</Label>
                {editing ? (
                  <Input
                    value={profile?.email || oidcUser?.profile?.email || ''}
                    disabled
                    className="bg-muted/50"
                  />
                ) : (
                  <p className="text-sm font-medium">
                    {profile?.email || oidcUser?.profile?.email || '-'}
                  </p>
                )}
              </div>
              <div className="space-y-1.5">
                <Label className="text-xs text-muted-foreground">{t('profile:phone')}</Label>
                {editing ? (
                  <div className="relative">
                    <Phone className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
                    <Input
                      id="phone"
                      type="tel"
                      value={phone}
                      onChange={(e) => setPhone(e.target.value)}
                      placeholder={t('profile:phonePlaceholder')}
                      className="pl-9"
                    />
                  </div>
                ) : (
                  <p className="text-sm font-medium">
                    {profile?.phone || <span className="text-muted-foreground">-</span>}
                  </p>
                )}
              </div>
            </div>
          </div>
          <div>
            <div className="flex items-center gap-2 mb-4">
              <Briefcase className="h-5 w-5 text-muted-foreground" />
              <h2 className="text-base font-semibold">{t('profile:work')}</h2>
            </div>
            <div className="space-y-4">
              <div className="space-y-1.5">
                <Label className="text-xs text-muted-foreground">{t('profile:jobTitle')}</Label>
                 {editing ? (
                   <Input
                     value={jobTitle}
                     onChange={(e) => setJobTitle(e.target.value)}
                     placeholder={t('profile:jobTitlePlaceholder')}
                  />
                ) : (
                  <p className="text-sm font-medium">
                    {profile?.jobTitle || <span className="text-muted-foreground">-</span>}
                  </p>
                )}
              </div>
              <div className="space-y-1.5">
                <Label className="text-xs text-muted-foreground">{t('profile:timezone')}</Label>
                 {editing ? (
                   <div className="relative">
                     <Clock className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
                     <select
                       id="timezone"
                       value={timezone}
                       onChange={(e) => setTimezone(e.target.value)}
                       className="flex h-9 w-full appearance-none rounded-md border border-input bg-transparent pl-9 pr-8 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
                     >
                       {TIMEZONES.map((tz) => (
                         <option key={tz} value={tz}>{tz}</option>
                       ))}
                     </select>
                   </div>
                 ) : (
                   <p className="text-sm font-medium">
                     {profile?.timezone || 'UTC'}
                   </p>
                 )}
               </div>
              <div className="space-y-1.5">
                <Label className="text-xs text-muted-foreground">{t('profile:tenant')}</Label>
                <p className="text-sm font-medium">
                  {profile?.tenantId || 'default'}
                </p>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Preferences */}
      <section className="border-b border-border/50 pb-7 mb-7">
        <div className="flex items-center gap-2 mb-4">
          <Globe className="h-5 w-5 text-muted-foreground" />
          <h2 className="text-base font-semibold">{t('profile:preferences')}</h2>
        </div>
        <div className="flex items-center justify-between gap-4 mb-4">
          <div className="space-y-0.5">
            <Label className="text-sm font-medium">{t('profile:appearance')}</Label>
             <p className="text-xs text-muted-foreground">
               {t('profile:appearanceDesc')}
             </p>
          </div>
          <div className="flex items-center gap-2">
            <Sun className="h-4 w-4 text-muted-foreground" />
            <Switch
              checked={isDark}
              onCheckedChange={setIsDark}
              aria-label="Toggle dark mode"
            />
            <Moon className="h-4 w-4 text-muted-foreground" />
          </div>
        </div>
        <Separator className="mb-4" />
        {/* Language */}
        <div className="flex items-center justify-between gap-4 mb-4">
          <div className="space-y-0.5">
            <Label className="text-sm font-medium">{t('profile:language')}</Label>
             <p className="text-xs text-muted-foreground">
               {t('profile:languageDesc')}
             </p>
          </div>
          {editing ? (
            <select
              value={prefLanguage}
              onChange={(e) => setPrefLanguage(e.target.value)}
              className="h-9 rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring max-w-[180px]"
            >
                <option value="">{t('common:default')} — {LANGUAGE_LABELS[defaultLanguage] || defaultLanguage}</option>
                <option value="en">English</option>
                <option value="vi">Tiếng Việt</option>
                <option value="ja">日本語</option>
                <option value="zh">中文</option>
                <option value="ko">한국어</option>
                <option value="de">Deutsch</option>
                <option value="fr">Français</option>
              </select>
            ) : (
              <Badge variant="secondary">{prefLanguage ? LANGUAGE_LABELS[prefLanguage] || prefLanguage : LANGUAGE_LABELS[defaultLanguage] || defaultLanguage}</Badge>
          )}
        </div>
        {/* Date Format */}
        <div className="flex items-center justify-between gap-4 mb-4">
          <div className="space-y-0.5">
            <Label className="text-sm font-medium">{t('profile:dateFormat')}</Label>
             <p className="text-xs text-muted-foreground">
               {t('profile:dateFormatDesc')}
             </p>
          </div>
          {editing ? (
            <select
              value={prefDateFormat}
              onChange={(e) => setPrefDateFormat(e.target.value)}
              className="h-9 rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring max-w-[180px]"
            >
                <option value="">{t('common:default')} — {DATE_FORMAT_LABELS[defaultDateFormat] || defaultDateFormat}</option>
                <option value="YYYY-MM-DD">YYYY-MM-DD (2024-12-31)</option>
                <option value="DD/MM/YYYY">DD/MM/YYYY (31/12/2024)</option>
                <option value="MM/DD/YYYY">MM/DD/YYYY (12/31/2024)</option>
                <option value="DD.MM.YYYY">DD.MM.YYYY (31.12.2024)</option>
              </select>
            ) : (
              <Badge variant="secondary">{prefDateFormat ? DATE_FORMAT_LABELS[prefDateFormat] || prefDateFormat : DATE_FORMAT_LABELS[defaultDateFormat] || defaultDateFormat}</Badge>
          )}
        </div>
        {/* Time Format */}
        <div className="flex items-center justify-between gap-4 mb-4">
          <div className="space-y-0.5">
            <Label className="text-sm font-medium">{t('profile:timeFormat')}</Label>
             <p className="text-xs text-muted-foreground">
               {t('profile:timeFormatDesc')}
             </p>
          </div>
          {editing ? (
            <select
              value={prefTimeFormat}
              onChange={(e) => setPrefTimeFormat(e.target.value)}
              className="h-9 rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring max-w-[180px]"
            >
                <option value="">{t('common:default')} — {TIME_FORMAT_LABELS[defaultTimeFormat] || defaultTimeFormat}</option>
                <option value="24h">24-hour (14:30)</option>
                <option value="12h">12-hour (2:30 PM)</option>
              </select>
            ) : (
              <Badge variant="secondary">{prefTimeFormat ? TIME_FORMAT_LABELS[prefTimeFormat] || prefTimeFormat : TIME_FORMAT_LABELS[defaultTimeFormat] || defaultTimeFormat}</Badge>
          )}
        </div>
        <Separator className="mb-4" />
        <div className="flex items-center justify-between gap-4">
          <div className="space-y-0.5">
            <Label className="text-sm font-medium">{t('profile:timezone')}</Label>
             <p className="text-xs text-muted-foreground">
               {t('profile:timezoneDesc')}
             </p>
          </div>
          {editing ? (
            <select
              value={timezone}
              onChange={(e) => setTimezone(e.target.value)}
              className="h-9 rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
            >
              {TIMEZONES.map((tz) => (
                <option key={tz} value={tz}>{tz}</option>
              ))}
            </select>
          ) : (
            <Badge variant="secondary">{profile?.timezone || 'UTC'}</Badge>
          )}
        </div>
      </section>

      {/* Password */}
      <section className="border-b border-border/50 pb-7 mb-7">
        <div className="flex items-center gap-2 mb-4">
          <KeyRound className="h-5 w-5 text-muted-foreground" />
          <h2 className="text-base font-semibold">{t('profile:password')}</h2>
        </div>
        <div className="space-y-4 max-w-sm">
          <div className="space-y-1.5">
            <Label htmlFor="newPassword">{t('profile:newPassword')}</Label>
             <Input id="newPassword" type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} placeholder={t('profile:newPasswordPlaceholder')} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="confirmPassword">{t('profile:confirmPassword')}</Label>
             <Input id="confirmPassword" type="password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} placeholder={t('profile:confirmPasswordPlaceholder')} />
          </div>
          {passwordMessage && <p className="text-sm text-success">{passwordMessage}</p>}
          {passwordError && <p className="text-sm text-destructive">{passwordError}</p>}
          <Button onClick={handlePasswordChange} disabled={passwordSaving || !newPassword || newPassword !== confirmPassword || newPassword.length < 6} size="sm" className="gap-2">
            {passwordSaving ? t('common:saving') : t('profile:changePassword')}
          </Button>
        </div>
      </section>

      {/* MFA */}
      <section className="border-b border-border/50 pb-7 mb-7">
        <div className="flex items-center gap-2 mb-4">
          <Shield className="h-5 w-5 text-muted-foreground" />
          <h2 className="text-base font-semibold">{t('profile:mfa')}</h2>
        </div>

        {mfaStatus === 'loading' && <p className="text-sm text-muted-foreground">{t('profile:mfaChecking')}</p>}

        {mfaStatus === 'enabled' && (
          <div className="space-y-4">
            <Badge variant="default">{t('profile:mfaEnabled')}</Badge>
             <p className="text-sm text-muted-foreground">{t('profile:mfaEnabledDesc')}</p>
            <Button variant="destructive" size="sm" onClick={handleDisableMfa} disabled={mfaSaving} className="gap-2">
              <ShieldOff className="h-4 w-4" />
              {mfaSaving ? t('common:saving') : t('profile:disableMfa')}
            </Button>
          </div>
        )}

        {mfaStatus === 'disabled' && !mfaSetup && (
          <Button onClick={handleSetupMfa} disabled={setupMfaMutation.isPending || mfaSaving} size="sm" className="gap-2">
            <ShieldEllipsis className="h-4 w-4" />
            {setupMfaMutation.isPending ? t('common:saving') : t('profile:setupMfa')}
          </Button>
        )}

        {mfaSetup && (
          <div className="space-y-4">
            <div className="flex justify-center">
              {qrDataUrl ? (
                <img src={qrDataUrl} alt="MFA QR Code" className="rounded-lg border" width={200} height={200} />
              ) : (
                <div className="flex h-[200px] w-[200px] items-center justify-center rounded-lg border bg-muted">
                  <span className="text-xs text-muted-foreground">{t('common:loading')}</span>
                </div>
              )}
            </div>
            <p className="text-sm text-muted-foreground">
               {t('profile:mfaScanInstruction')}
             </p>
            <div className="space-y-1.5">
              <Label className="text-xs text-muted-foreground">{t('profile:mfaSecretKey')}</Label>
              <div className="flex items-center gap-2 max-w-sm">
                <code className="flex-1 rounded-md border bg-muted px-3 py-2 font-mono text-sm">{mfaSecret}</code>
                <Button variant="outline" size="icon" className="h-9 w-9 shrink-0" onClick={() => { navigator.clipboard.writeText(mfaSecret) }}>
                  <Copy className="h-4 w-4" />
                </Button>
              </div>
            </div>
            <Separator />
            <div className="space-y-1.5">
              <Label htmlFor="mfaCode">{t('profile:mfaCodeLabel')}</Label>
              <Input id="mfaCode" value={mfaCode} onChange={(e) => setMfaCode(e.target.value)} placeholder="000000" maxLength={6} className="w-40" />
            </div>
            {mfaMessage && <p className="text-sm text-success">{mfaMessage}</p>}
            {mfaError && <p className="text-sm text-destructive">{mfaError}</p>}
            <div className="flex gap-2">
              <Button variant="outline" size="sm" onClick={() => setMfaSetup(false)}>{t('common:cancel')}</Button>
               <Button size="sm" onClick={handleVerifyMfa} disabled={mfaSaving || mfaCode.length !== 6} className="gap-2">
                 {mfaSaving ? t('common:saving') : t('profile:verifyAndEnable')}
              </Button>
            </div>
          </div>
        )}
      </section>

      {/* Account */}
      <section className="border-b border-border/50 pb-7 mb-7">
        <div className="flex items-center gap-2 mb-4">
          <Shield className="h-5 w-5 text-muted-foreground" />
          <h2 className="text-base font-semibold">{t('profile:account')}</h2>
        </div>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">{t('profile:role')}</Label>
            <div><Badge variant="secondary">{profile?.role?.name || 'user'}</Badge></div>
          </div>
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">{t('profile:status')}</Label>
            <div>
              <Badge variant={profile?.active !== false ? 'default' : 'secondary'}>
                {profile?.active !== false ? t('profile:active') : t('profile:inactive')}
              </Badge>
            </div>
          </div>
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">{t('profile:created')}</Label>
            <p className="text-sm font-medium">
              {profile?.createdAt ? new Date(profile.createdAt).toLocaleDateString() : '-'}
            </p>
          </div>
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">{t('profile:lastUpdated')}</Label>
            <p className="text-sm font-medium">
              {profile?.updatedAt ? new Date(profile.updatedAt).toLocaleDateString() : '-'}
            </p>
          </div>
        </div>
      </section>

      {/* Keycloak Connection */}
      <section className="pb-7">
        <div className="flex items-center gap-2 mb-4">
          <AlertTriangle className="h-5 w-5 text-muted-foreground" />
          <h2 className="text-base font-semibold text-destructive">{t('profile:keycloak')}</h2>
        </div>
        <div className="space-y-3">
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground">{t('profile:keycloakSubject')}</Label>
            <p className="break-all font-mono text-xs text-muted-foreground">
              {oidcUser?.profile?.sub || '-'}
            </p>
          </div>
          <div className="space-y-1">
            <Label className="text-xs text-muted-foreground">{t('profile:keycloakIssuer')}</Label>
            <p className="break-all font-mono text-xs text-muted-foreground">
              {oidcUser?.profile?.iss || '-'}
            </p>
          </div>
          <Separator />
          <p className="text-xs text-muted-foreground">
             {t('profile:keycloakNote')}
           </p>
        </div>
      </section>

      {/* Edit Actions */}
      {editing && (
        <div className="sticky bottom-4 z-20 rounded-lg border bg-card/95 p-4 shadow-lg backdrop-blur supports-[backdrop-filter]:bg-card/80">
          {message && (
            <div
              className={`mb-3 flex items-center gap-2 text-sm ${
                message.type === 'success'
                  ? 'text-success'
                  : 'text-destructive'
              }`}
            >
              {message.type === 'success' ? (
                <Check className="h-4 w-4" />
              ) : (
                <X className="h-4 w-4" />
              )}
              {message.text}
            </div>
          )}
          <div className="flex items-center justify-between gap-3">
            <p className="text-sm text-muted-foreground">
              {t('profile:unsavedChanges')}
            </p>
            <div className="flex gap-2">
              <Button
                variant="outline"
                onClick={handleCancel}
                disabled={saving}
                className="gap-2"
              >
                <X className="h-4 w-4" />
                {t('common:cancel')}
               </Button>
               <Button
                 onClick={handleSave}
                 disabled={saving}
                 className="gap-2"
               >
                 <Check className="h-4 w-4" />
                 {saving ? t('common:saving') : t('common:save')}
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Static message when not editing */}
      {!editing && message && (
        <div
          className={`flex items-center gap-2 text-sm mt-4 ${
            message.type === 'success'
              ? 'text-success'
              : 'text-destructive'
          }`}
        >
          {message.type === 'success' ? (
            <Check className="h-4 w-4" />
          ) : (
            <X className="h-4 w-4" />
          )}
          {message.text}
        </div>
      )}
    </div>
  )
}
