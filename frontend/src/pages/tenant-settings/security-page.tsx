import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import {
  useGetTenantSettingsQuery,
  useGetEffectiveSettingsQuery,
  usePutTenantSettingsMutation,
  useMeQuery,
} from '@/shared/api'
import { Button } from '@/shared/ui/button'
import { Label } from '@/shared/ui/label'
import { Badge } from '@/shared/ui/badge'
import { Input } from '@/shared/ui/input'
import { Switch } from '@/shared/ui/switch'
import { Save, RotateCcw, Check, X, Shield } from 'lucide-react'
import {
  type SecuritySettings,
  type SettingsMap,
} from './settings-shared'

export function SecurityPage() {
  const { t } = useTranslation()
  const { data: me } = useMeQuery()
  const { data: tenantSettings } = useGetTenantSettingsQuery()
  const { data: effectiveSettings } = useGetEffectiveSettingsQuery()
  const mutation = usePutTenantSettingsMutation()

  const isAdmin = me?.role?.name === 'admin' || me?.role?.name === 'businessOwner' || me?.role?.name === 'operator'

  const [security, setSecurity] = useState<SecuritySettings>({})
  const [savedSecurity, setSavedSecurity] = useState<SecuritySettings>({})
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null)

  const settings = tenantSettings as SettingsMap | undefined
  const effective = effectiveSettings as SettingsMap | undefined

  useEffect(() => {
    if (settings?.security) {
      const s = settings.security
      setSecurity(s)
      setSavedSecurity(s)
    }
  }, [settings])

  useEffect(() => {
    if (message) {
      const timer = setTimeout(() => setMessage(null), 4000)
      return () => clearTimeout(timer)
    }
  }, [message])

  const hasChanges = JSON.stringify(security) !== JSON.stringify(savedSecurity)

  const handleSave = () => {
    mutation.mutate(
      { body: { settings: { security } } },
      {
        onSuccess: () => {
          setSavedSecurity({ ...security })
          setMessage({ type: 'success', text: t('common:saved') })
        },
        onError: () => {
          setMessage({ type: 'error', text: t('common:error') })
        },
      },
    )
  }

  const handleReset = () => {
    setSecurity({ ...savedSecurity })
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <Shield className="h-6 w-6 text-muted-foreground" />
        <div className="flex-1">
          <div className="flex items-center gap-2">
            <h2 className="text-lg font-semibold">{t('settings:sectionSecurity')}</h2>
            {isAdmin && (
              <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                {t('settings:adminBadge')}
              </Badge>
            )}
          </div>
        </div>
      </div>

      {/* Read-only notice */}
      {!isAdmin && (
        <div className="rounded-md border border-amber-200 bg-amber-50 dark:border-amber-800 dark:bg-amber-950/30 px-4 py-3 text-sm text-amber-800 dark:text-amber-200">
          {t('settings:readOnlyBadge')}
        </div>
      )}

      {/* Message */}
      {message && (
        <div
          className={`flex items-center gap-2 rounded-md border px-4 py-3 text-sm ${
            message.type === 'success'
              ? 'border-green-200 bg-green-50 text-green-800 dark:border-green-800 dark:bg-green-950/30 dark:text-green-200'
              : 'border-red-200 bg-red-50 text-red-800 dark:border-red-800 dark:bg-red-950/30 dark:text-red-200'
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

      {/* Form fields */}
      <div className="space-y-5">
        <div className="space-y-2">
          <Label htmlFor="minPasswordLength">{t('settings:minPasswordLength')}</Label>
          <Input
            id="minPasswordLength"
            type="number"
            value={security.minPasswordLength ?? effective?.security?.minPasswordLength ?? ''}
            onChange={(e) =>
              setSecurity((prev) => ({
                ...prev,
                minPasswordLength: e.target.value ? Number(e.target.value) : undefined,
              }))
            }
            disabled={!isAdmin}
            min={4}
            max={128}
          />
          {effective?.security?.minPasswordLength != null && security.minPasswordLength == null && (
            <p className="text-xs text-muted-foreground">
              {t('settings:effectiveDefault')}: {effective.security.minPasswordLength}
            </p>
          )}
        </div>

        <div className="space-y-2">
          <Label htmlFor="sessionTimeoutMinutes">{t('settings:sessionTimeout')}</Label>
          <Input
            id="sessionTimeoutMinutes"
            type="number"
            value={security.sessionTimeoutMinutes ?? effective?.security?.sessionTimeoutMinutes ?? ''}
            onChange={(e) =>
              setSecurity((prev) => ({
                ...prev,
                sessionTimeoutMinutes: e.target.value ? Number(e.target.value) : undefined,
              }))
            }
            disabled={!isAdmin}
            min={1}
            max={1440}
          />
          {effective?.security?.sessionTimeoutMinutes != null && security.sessionTimeoutMinutes == null && (
            <p className="text-xs text-muted-foreground">
              {t('settings:effectiveDefault')}: {effective.security.sessionTimeoutMinutes}
            </p>
          )}
        </div>

        <div className="space-y-2">
          <Label htmlFor="maxLoginAttempts">{t('settings:maxLoginAttempts')}</Label>
          <Input
            id="maxLoginAttempts"
            type="number"
            value={security.maxLoginAttempts ?? effective?.security?.maxLoginAttempts ?? ''}
            onChange={(e) =>
              setSecurity((prev) => ({
                ...prev,
                maxLoginAttempts: e.target.value ? Number(e.target.value) : undefined,
              }))
            }
            disabled={!isAdmin}
            min={1}
            max={100}
          />
          {effective?.security?.maxLoginAttempts != null && security.maxLoginAttempts == null && (
            <p className="text-xs text-muted-foreground">
              {t('settings:effectiveDefault')}: {effective.security.maxLoginAttempts}
            </p>
          )}
        </div>

        <div className="flex items-center justify-between rounded-lg border p-4">
          <div className="space-y-0.5">
            <Label htmlFor="requireMfa">{t('settings:requireMfa')}</Label>
            {effective?.security?.requireMfa != null && security.requireMfa == null && (
              <p className="text-xs text-muted-foreground">
                {t('settings:effectiveDefault')}: {effective.security.requireMfa ? t('common:enabled') : t('common:disabled')}
              </p>
            )}
          </div>
          <Switch
            id="requireMfa"
            checked={security.requireMfa ?? effective?.security?.requireMfa ?? false}
            onCheckedChange={(checked) =>
              setSecurity((prev) => ({ ...prev, requireMfa: checked }))
            }
            disabled={!isAdmin}
          />
        </div>
      </div>

      {/* Sticky save bar */}
      {isAdmin && (
        <div className="sticky bottom-0 z-10 -mx-6 mt-8 border-t bg-background px-6 py-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              {mutation.isPending && (
                <span className="text-sm text-muted-foreground animate-pulse">{t('common:saving')}</span>
              )}
            </div>
            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={handleReset}
                disabled={!hasChanges || mutation.isPending}
              >
                <RotateCcw className="h-4 w-4" />
                {t('common:reset')}
              </Button>
              <Button
                size="sm"
                onClick={handleSave}
                disabled={!hasChanges || mutation.isPending}
              >
                <Save className="h-4 w-4" />
                {t('common:save')}
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
