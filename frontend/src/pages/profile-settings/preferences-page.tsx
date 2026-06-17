import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import i18n from '@/i18n/config'
import {
  useGetUserSettingsQuery,
  useGetEffectiveSettingsQuery,
  usePutUserSettingsMutation,
} from '@/shared/api'
import { useTheme } from '@/shared/hooks/use-theme'
import { Button } from '@/shared/ui/button'
import { Label } from '@/shared/ui/label'
import { Switch } from '@/shared/ui/switch'
import { Badge } from '@/shared/ui/badge'
import { Separator } from '@/shared/ui/separator'
import { Globe, Sun, Moon, Save, RotateCcw, Check, X } from 'lucide-react'

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

const LANGUAGE_LABELS: Record<string, string> = {
  en: 'English',
  vi: 'Tiếng Việt',
  ja: '日本語',
  zh: '中文',
  ko: '한국어',
  de: 'Deutsch',
  fr: 'Français',
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

export function PreferencesPage() {
  const { t } = useTranslation()
  const { data: userSettingsRaw } = useGetUserSettingsQuery()
  const { data: effectiveRaw } = useGetEffectiveSettingsQuery()
  const putUserSettingsMutation = usePutUserSettingsMutation()
  const { isDark, setIsDark } = useTheme()

  const [prefLanguage, setPrefLanguage] = useState('')
  const [prefDateFormat, setPrefDateFormat] = useState('')
  const [prefTimeFormat, setPrefTimeFormat] = useState('')
  const [timezone, setTimezone] = useState('UTC')

  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState<{
    type: 'success' | 'error'
    text: string
  } | null>(null)

  const effectiveSettings = effectiveRaw as
    | { regional?: Record<string, string> }
    | undefined
  const defaultLanguage = effectiveSettings?.regional?.language ?? 'en'
  const defaultDateFormat = effectiveSettings?.regional?.dateFormat ?? 'YYYY-MM-DD'
  const defaultTimeFormat = effectiveSettings?.regional?.timeFormat ?? '24h'
  const defaultTimezone = effectiveSettings?.regional?.timezone ?? 'UTC'

  useEffect(() => {
    const us = userSettingsRaw as
      | { regional?: Record<string, string> }
      | undefined
    if (us?.regional) {
      setPrefLanguage(us.regional.language || '')
      setPrefDateFormat(us.regional.dateFormat || '')
      setPrefTimeFormat(us.regional.timeFormat || '')
    }
  }, [userSettingsRaw])

  // Sync language from user settings to i18next on initial load
  useEffect(() => {
    const us = userSettingsRaw as
      | { regional?: Record<string, string> }
      | undefined
    const lang = us?.regional?.language
    if (lang && lang !== i18n.language) {
      localStorage.setItem('language', lang)
      i18n.changeLanguage(lang)
    }
  }, [userSettingsRaw])

  function resetFields() {
    const us = userSettingsRaw as
      | { regional?: Record<string, string> }
      | undefined
    setPrefLanguage(us?.regional?.language || '')
    setPrefDateFormat(us?.regional?.dateFormat || '')
    setPrefTimeFormat(us?.regional?.timeFormat || '')
    setTimezone(us?.regional?.timezone || 'UTC')
  }

  async function handleSave() {
    setSaving(true)
    setMessage(null)
    try {
      const regional: Record<string, string> = {}
      if (prefLanguage) regional.language = prefLanguage
      if (prefDateFormat) regional.dateFormat = prefDateFormat
      if (prefTimeFormat) regional.timeFormat = prefTimeFormat
      if (timezone) regional.timezone = timezone
      if (Object.keys(regional).length > 0) {
        await putUserSettingsMutation.mutateAsync({
          body: { settings: { regional } } as Record<string, unknown>,
        })
      }
      // Sync language to i18next immediately
      if (prefLanguage) {
        localStorage.setItem('language', prefLanguage)
        i18n.changeLanguage(prefLanguage)
      }
      setMessage({ type: 'success', text: t('profile:profileUpdated') })
    } catch {
      setMessage({ type: 'error', text: t('profile:profileUpdateFailed') })
    } finally {
      setSaving(false)
      setTimeout(() => setMessage(null), 4000)
    }
  }

  return (
    <div className="mx-auto max-w-3xl">
      {/* Page Header */}
      <div className="pb-8">
        <h1 className="text-3xl font-bold tracking-tight">
          {t('nav:preferences')}
        </h1>
        <p className="text-muted-foreground mt-1">
          {t('profile:preferencesDesc') || 'Customize your language, format, and regional preferences'}
        </p>
      </div>

      {/* Theme */}
      <section className="border-b border-border/50 pb-7 mb-7">
        <div className="flex items-center justify-between gap-4 mb-1">
          <div className="space-y-0.5">
            <Label className="text-sm font-medium">
              {t('profile:appearance')}
            </Label>
            <p className="text-xs text-muted-foreground">
              {t('profile:appearanceDesc')}
            </p>
          </div>
          <div className="flex items-center gap-2">
            <Sun className="h-4 w-4 text-muted-foreground" />
            <Switch
              checked={isDark}
              onCheckedChange={setIsDark}
              aria-label={t('profile:toggleDarkMode') || 'Toggle dark mode'}
            />
            <Moon className="h-4 w-4 text-muted-foreground" />
          </div>
        </div>
      </section>

      {/* Regional Preferences */}
      <section className="border-b border-border/50 pb-7 mb-7">
        <div className="flex items-center gap-2 mb-4">
          <Globe className="h-5 w-5 text-muted-foreground" />
          <h2 className="text-base font-semibold">
            {t('profile:preferences')}
          </h2>
        </div>

        {/* Language */}
        <div className="flex items-center justify-between gap-4 mb-4">
          <div className="space-y-0.5">
            <Label className="text-sm font-medium">
              {t('profile:language')}
            </Label>
            <p className="text-xs text-muted-foreground">
              {t('profile:languageDesc')}
            </p>
          </div>
          <select
            value={prefLanguage}
            onChange={(e) => setPrefLanguage(e.target.value)}
            className="h-9 rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring max-w-[200px]"
          >
            <option value="">
              {t('common:default')} —{' '}
              {LANGUAGE_LABELS[defaultLanguage] || defaultLanguage}
            </option>
            {Object.entries(LANGUAGE_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </div>

        {/* Date Format */}
        <div className="flex items-center justify-between gap-4 mb-4">
          <div className="space-y-0.5">
            <Label className="text-sm font-medium">
              {t('profile:dateFormat')}
            </Label>
            <p className="text-xs text-muted-foreground">
              {t('profile:dateFormatDesc')}
            </p>
          </div>
          <select
            value={prefDateFormat}
            onChange={(e) => setPrefDateFormat(e.target.value)}
            className="h-9 rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring max-w-[220px]"
          >
            <option value="">
              {t('common:default')} —{' '}
              {DATE_FORMAT_LABELS[defaultDateFormat] || defaultDateFormat}
            </option>
            {Object.entries(DATE_FORMAT_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </div>

        {/* Time Format */}
        <div className="flex items-center justify-between gap-4 mb-4">
          <div className="space-y-0.5">
            <Label className="text-sm font-medium">
              {t('profile:timeFormat')}
            </Label>
            <p className="text-xs text-muted-foreground">
              {t('profile:timeFormatDesc')}
            </p>
          </div>
          <select
            value={prefTimeFormat}
            onChange={(e) => setPrefTimeFormat(e.target.value)}
            className="h-9 rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring max-w-[200px]"
          >
            <option value="">
              {t('common:default')} —{' '}
              {TIME_FORMAT_LABELS[defaultTimeFormat] || defaultTimeFormat}
            </option>
            {Object.entries(TIME_FORMAT_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </div>

        <Separator className="mb-4" />

        {/* Timezone */}
        <div className="flex items-center justify-between gap-4">
          <div className="space-y-0.5">
            <Label className="text-sm font-medium">
              {t('profile:timezone')}
            </Label>
            <p className="text-xs text-muted-foreground">
              {t('profile:timezoneDesc')}
            </p>
          </div>
          <select
            value={timezone}
            onChange={(e) => setTimezone(e.target.value)}
            className="h-9 rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
          >
            {TIMEZONES.map((tz) => (
              <option key={tz} value={tz}>
                {tz}
              </option>
            ))}
          </select>
        </div>
      </section>

      {/* Current Settings Summary */}
      <section className="border-b border-border/50 pb-7 mb-7">
        <div className="flex items-center gap-2 mb-4">
          <Globe className="h-5 w-5 text-muted-foreground" />
          <h2 className="text-base font-semibold">
            {t('profile:currentSettings') || 'Current Settings'}
          </h2>
        </div>
        <div className="flex flex-wrap gap-2">
          <Badge variant="secondary">
            {t('profile:language')}:{' '}
            {prefLanguage
              ? LANGUAGE_LABELS[prefLanguage] || prefLanguage
              : LANGUAGE_LABELS[defaultLanguage] || defaultLanguage}
          </Badge>
          <Badge variant="secondary">
            {t('profile:dateFormat')}:{' '}
            {prefDateFormat
              ? DATE_FORMAT_LABELS[prefDateFormat] || prefDateFormat
              : DATE_FORMAT_LABELS[defaultDateFormat] || defaultDateFormat}
          </Badge>
          <Badge variant="secondary">
            {t('profile:timeFormat')}:{' '}
            {prefTimeFormat
              ? TIME_FORMAT_LABELS[prefTimeFormat] || prefTimeFormat
              : TIME_FORMAT_LABELS[defaultTimeFormat] || defaultTimeFormat}
          </Badge>
          <Badge variant="secondary">
            {t('profile:timezone')}: {timezone || defaultTimezone}
          </Badge>
        </div>
      </section>

      {/* Messages */}
      {message && (
        <div
          className={`flex items-center gap-2 text-sm mb-4 ${
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

      {/* Actions */}
      <div className="flex items-center gap-3">
        <Button
          variant="outline"
          onClick={resetFields}
          disabled={saving}
          className="gap-2"
        >
          <RotateCcw className="h-4 w-4" />
          {t('common:reset') || 'Reset'}
        </Button>
        <Button
          onClick={handleSave}
          disabled={saving}
          className="gap-2"
        >
          {saving ? (
            t('common:saving')
          ) : (
            <>
              <Save className="h-4 w-4" />
              {t('common:save')}
            </>
          )}
        </Button>
      </div>
    </div>
  )
}
