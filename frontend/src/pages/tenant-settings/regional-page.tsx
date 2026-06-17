import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import i18n from '@/i18n/config'
import {
  useGetTenantSettingsQuery,
  useGetEffectiveSettingsQuery,
  usePutTenantSettingsMutation,
  useMeQuery,
} from '@/shared/api'
import { Button } from '@/shared/ui/button'
import { Label } from '@/shared/ui/label'
import { Badge } from '@/shared/ui/badge'
import { Globe, Save, RotateCcw, Check, X } from 'lucide-react'
import {
  type RegionalSettings,
  type SettingsMap,
  TIMEZONES,
  LANGUAGES,
  DATE_FORMATS,
  TIME_FORMATS,
  findLabel,
  Select,
} from './settings-shared'

export function RegionalPage() {
  const { t } = useTranslation()
  const { data: me } = useMeQuery()
  const { data: tenantSettings } = useGetTenantSettingsQuery()
  const { data: effectiveSettings } = useGetEffectiveSettingsQuery()
  const mutation = usePutTenantSettingsMutation()

  const isAdmin = me?.role?.name === 'admin'

  const [regional, setRegional] = useState<RegionalSettings>({})
  const [savedRegional, setSavedRegional] = useState<RegionalSettings>({})
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null)

  const settings = tenantSettings as SettingsMap | undefined
  const effective = effectiveSettings as SettingsMap | undefined

  useEffect(() => {
    if (settings?.regional) {
      const r = settings.regional
      setRegional(r)
      setSavedRegional(r)
    }
  }, [settings])

  useEffect(() => {
    if (message) {
      const timer = setTimeout(() => setMessage(null), 4000)
      return () => clearTimeout(timer)
    }
  }, [message])

  const hasChanges = JSON.stringify(regional) !== JSON.stringify(savedRegional)

  const handleSave = () => {
    mutation.mutate(
      { body: { settings: { regional } } },
      {
        onSuccess: () => {
          setSavedRegional({ ...regional })
          setMessage({ type: 'success', text: t('common:saved') })
          if (regional.language) {
            localStorage.setItem('language', regional.language)
            i18n.changeLanguage(regional.language)
          }
        },
        onError: () => {
          setMessage({ type: 'error', text: t('common:error') })
        },
      },
    )
  }

  const handleReset = () => {
    setRegional({ ...savedRegional })
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <Globe className="h-6 w-6 text-muted-foreground" />
        <div className="flex-1">
          <div className="flex items-center gap-2">
            <h2 className="text-lg font-semibold">{t('settings:sectionRegional')}</h2>
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
          <Label htmlFor="timezone">{t('settings:timezone')}</Label>
          <Select
            value={regional.timezone ?? effective?.regional?.timezone ?? ''}
            onChange={(v) => setRegional((prev) => ({ ...prev, timezone: v }))}
            options={TIMEZONES.map((tz) => ({ value: tz, label: tz }))}
            placeholder={t('common:select')}
          />
          {effective?.regional?.timezone && !regional.timezone && (
            <p className="text-xs text-muted-foreground">
              {t('settings:effectiveDefault')}: {effective.regional.timezone}
            </p>
          )}
        </div>

        <div className="space-y-2">
          <Label htmlFor="language">{t('settings:language')}</Label>
          <Select
            value={regional.language ?? effective?.regional?.language ?? ''}
            onChange={(v) => setRegional((prev) => ({ ...prev, language: v }))}
            options={LANGUAGES}
            placeholder={t('common:select')}
          />
          {effective?.regional?.language && !regional.language && (
            <p className="text-xs text-muted-foreground">
              {t('settings:effectiveDefault')}: {findLabel(LANGUAGES, effective.regional.language)}
            </p>
          )}
        </div>

        <div className="space-y-2">
          <Label htmlFor="dateFormat">{t('settings:dateFormat')}</Label>
          <Select
            value={regional.dateFormat ?? effective?.regional?.dateFormat ?? ''}
            onChange={(v) => setRegional((prev) => ({ ...prev, dateFormat: v }))}
            options={DATE_FORMATS}
            placeholder={t('common:select')}
          />
          {effective?.regional?.dateFormat && !regional.dateFormat && (
            <p className="text-xs text-muted-foreground">
              {t('settings:effectiveDefault')}: {effective.regional.dateFormat}
            </p>
          )}
        </div>

        <div className="space-y-2">
          <Label htmlFor="timeFormat">{t('settings:timeFormat')}</Label>
          <Select
            value={regional.timeFormat ?? effective?.regional?.timeFormat ?? ''}
            onChange={(v) => setRegional((prev) => ({ ...prev, timeFormat: v }))}
            options={TIME_FORMATS}
            placeholder={t('common:select')}
          />
          {effective?.regional?.timeFormat && !regional.timeFormat && (
            <p className="text-xs text-muted-foreground">
              {t('settings:effectiveDefault')}: {findLabel(TIME_FORMATS, effective.regional.timeFormat)}
            </p>
          )}
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
