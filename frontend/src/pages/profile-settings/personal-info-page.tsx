import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { getUser } from '@/shared/auth'
import { useMeQuery, useUpdateMeMutation } from '@/shared/api'
import { Badge } from '@/shared/ui/badge'
import { Avatar, AvatarFallback } from '@/shared/ui/avatar'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Separator } from '@/shared/ui/separator'
import type { User as OidcUser } from 'oidc-client-ts'
import {
  Briefcase,
  Link,
  Mail,
  Phone,
  Clock,
  User as UserIcon,
  Pencil,
  Check,
  X,
  AlertTriangle,
  Shield,
} from 'lucide-react'

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

function getInitials(oidcUser: OidcUser | null) {
  if (oidcUser?.profile?.name) {
    return oidcUser.profile.name
      .split(' ')
      .map((n) => n[0])
      .join('')
      .toUpperCase()
      .slice(0, 2)
  }
  return oidcUser?.profile?.email?.[0]?.toUpperCase() ?? '?'
}

function getDisplayName(oidcUser: OidcUser | null) {
  return (
    oidcUser?.profile?.name ||
    oidcUser?.profile?.preferred_username ||
    'User'
  )
}

export function PersonalInfoPage() {
  const [oidcUser, setOidcUser] = useState<OidcUser | null>(null)
  const { data: profile, isLoading } = useMeQuery()
  const updateMeMutation = useUpdateMeMutation()
  const { t } = useTranslation()

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

  function resetFields() {
    if (!profile) return
    setFirstName(profile.firstName ?? '')
    setLastName(profile.lastName ?? '')
    setPhone(profile.phone ?? '')
    setJobTitle(profile.jobTitle ?? '')
    setBio(profile.bio ?? '')
    setTimezone(profile.timezone ?? 'UTC')
    setWebsiteUrl(profile.websiteUrl ?? '')
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
          websiteUrl: websiteUrl || undefined,
          timezone: timezone || undefined,
        },
      })
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
          <h1 className="text-3xl font-bold tracking-tight">{t('nav:personalInfo')}</h1>
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
      <section className="border-b border-border/50 pb-7 mb-7">
        <div className="flex items-center gap-2 mb-4">
          <AlertTriangle className="h-5 w-5 text-muted-foreground" />
          <h2 className="text-base font-semibold text-destructive">{t('profile:keycloak')}</h2>
        </div>
        {!editing && (
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
        )}
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
