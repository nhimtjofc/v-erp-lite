import { useEffect, useState, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import i18n from '@/i18n/config'
import {
  useGetTenantSettingsQuery,
  useGetEffectiveSettingsQuery,
  usePutTenantSettingsMutation,
  useMeQuery,
} from '@/shared/api'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Switch } from '@/shared/ui/switch'
import { Badge } from '@/shared/ui/badge'
import { Separator } from '@/shared/ui/separator'
import {
  Globe,
  DollarSign,
  Shield,
  Puzzle,
  MessageSquare,
  Save,
  RotateCcw,
  Check,
  X,
} from 'lucide-react'

// ── Types ──

interface RegionalSettings {
  timezone?: string
  language?: string
  dateFormat?: string
  timeFormat?: string
}

interface CurrencySettings {
  code?: string
  symbol?: string
  decimalPlaces?: number
  fiscalYearStart?: string
}

interface SecuritySettings {
  minPasswordLength?: number
  requireMfa?: boolean
  sessionTimeoutMinutes?: number
  maxLoginAttempts?: number
}

interface FeatureToggles {
  canManageCustomers?: boolean
  canManageBusiness?: boolean
  canManageUsers?: boolean
  canManageSettings?: boolean
  canManageRoles?: boolean
}

interface CommunicationSettings {
  defaultEmailFrom?: string
  defaultEmailSignature?: string
  defaultNotificationChannel?: string
}

interface SettingsMap {
  regional?: RegionalSettings
  currency?: CurrencySettings
  security?: SecuritySettings
  features?: FeatureToggles
  communication?: CommunicationSettings
}

// ── Constants ──

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

const LANGUAGES = [
  { value: 'en', label: 'English' },
  { value: 'vi', label: 'Tiếng Việt' },
  { value: 'ja', label: '日本語' },
  { value: 'zh', label: '中文' },
  { value: 'ko', label: '한국어' },
  { value: 'de', label: 'Deutsch' },
  { value: 'fr', label: 'Français' },
]

const DATE_FORMATS = [
  { value: 'YYYY-MM-DD', label: '2024-12-31' },
  { value: 'DD/MM/YYYY', label: '31/12/2024' },
  { value: 'MM/DD/YYYY', label: '12/31/2024' },
  { value: 'DD.MM.YYYY', label: '31.12.2024' },
]

const TIME_FORMATS = [
  { value: '24h', label: '14:30' },
  { value: '12h', label: '2:30 PM' },
]

const CURRENCIES = [
  { value: 'USD', label: 'USD ($)', symbol: '$' },
  { value: 'EUR', label: 'EUR (€)', symbol: '€' },
  { value: 'GBP', label: 'GBP (£)', symbol: '£' },
  { value: 'JPY', label: 'JPY (¥)', symbol: '¥' },
  { value: 'VND', label: 'VND (₫)', symbol: '₫' },
  { value: 'SGD', label: 'SGD ($)', symbol: 'S$' },
  { value: 'AUD', label: 'AUD ($)', symbol: 'A$' },
]

const NOTIFICATION_CHANNELS = [
  { value: 'email', label: 'Email' },
  { value: 'in-app', label: 'In-App' },
]

const FEATURE_KEYS: Array<keyof FeatureToggles> = [
  'canManageCustomers',
  'canManageBusiness',
  'canManageUsers',
  'canManageSettings',
  'canManageRoles',
]

// ── Helpers ──

function settingValue(
  settings: SettingsMap | undefined,
  category: keyof SettingsMap,
  key: string,
): unknown {
  const cat = settings?.[category]
  if (cat && typeof cat === 'object' && key in cat) {
    return (cat as Record<string, unknown>)[key]
  }
  return undefined
}

function findLabel(arr: { value: string; label: string }[], value: string | undefined): string {
  if (!value) return ''
  return arr.find((o) => o.value === value)?.label ?? value
}

function Select({
  value,
  onChange,
  options,
  placeholder,
}: {
  value: string
  onChange: (v: string) => void
  options: { value: string; label: string }[]
  placeholder?: string
}) {
  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className="flex h-9 w-full max-w-xs rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
    >
      {placeholder && <option value="">{placeholder}</option>}
      {options.map((o) => (
        <option key={o.value} value={o.value}>{o.label}</option>
      ))}
    </select>
  )
}

// ── Page Component ──

export function SettingsPage() {
  const { data: tenantSettingsRaw } = useGetTenantSettingsQuery()
  const { data: effectiveRaw } = useGetEffectiveSettingsQuery()
  const { data: profile } = useMeQuery()
  const { t } = useTranslation()
  const putMutation = usePutTenantSettingsMutation()

  const isAdmin = profile?.role?.name === 'admin'

  const tenantSettings = tenantSettingsRaw as SettingsMap | undefined
  const effectiveSettings = effectiveRaw as SettingsMap | undefined

  // Form state per category
  const [regional, setRegional] = useState<RegionalSettings>({})
  const [currency, setCurrency] = useState<CurrencySettings>({})
  const [security, setSecurity] = useState<SecuritySettings>({})
  const [features, setFeatures] = useState<FeatureToggles>({})
  const [communication, setCommunication] = useState<CommunicationSettings>({})

  // Initialize form from tenant settings
  useEffect(() => {
    if (tenantSettings) {
      if (tenantSettings.regional) setRegional(tenantSettings.regional)
      if (tenantSettings.currency) setCurrency(tenantSettings.currency)
      if (tenantSettings.security) setSecurity(tenantSettings.security)
      if (tenantSettings.features) setFeatures(tenantSettings.features)
      if (tenantSettings.communication) setCommunication(tenantSettings.communication)
    }
  }, [tenantSettings])

  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null)

  const handleSave = useCallback(async () => {
    setSaving(true)
    setMessage(null)
    try {
      const body: SettingsMap = {}
      if (Object.keys(regional).length) body.regional = regional
      if (Object.keys(currency).length) body.currency = currency
      if (Object.keys(security).length) body.security = security
      if (Object.keys(features).length) body.features = features
      if (Object.keys(communication).length) body.communication = communication

      await putMutation.mutateAsync({ body: { settings: body as unknown as Record<string, unknown> } })
      // Sync tenant default language to current user's UI
      if (regional.language) {
        localStorage.setItem('language', regional.language)
        i18n.changeLanguage(regional.language)
      }
      setMessage({ type: 'success', text: t('settings:settingsSaved') })
    } catch {
      setMessage({ type: 'error', text: t('settings:settingsSaveFailed') })
    } finally {
      setSaving(false)
      setTimeout(() => setMessage(null), 4000)
    }
  }, [regional, currency, security, features, communication, putMutation])

  const handleReset = useCallback(() => {
    setRegional(tenantSettings?.regional ?? {})
    setCurrency(tenantSettings?.currency ?? {})
    setSecurity(tenantSettings?.security ?? {})
    setFeatures(tenantSettings?.features ?? {})
    setCommunication(tenantSettings?.communication ?? {})
    setMessage(null)
  }, [tenantSettings])

  return (
    <div className="mx-auto max-w-3xl">
      {/* Header */}
      <div className="flex items-start justify-between gap-4 pb-8">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('settings:title')}</h1>
          <p className="text-muted-foreground mt-1">
            {t('settings:description')}
          </p>
        </div>
        <Badge variant={isAdmin ? 'default' : 'secondary'} className="shrink-0 mt-1">
          {isAdmin ? t('settings:admin') : t('settings:readOnly')}
        </Badge>
      </div>

      {!isAdmin && (
        <div className="rounded-lg border border-border/50 bg-muted/30 px-4 py-3 mb-8 text-sm text-muted-foreground">
          {t('settings:readOnlyNotice')}
        </div>
      )}

      {/* ── Regional ── */}
      <section className="border-b border-border/50 pb-7 mb-7">
        <div className="flex items-center gap-2 mb-4">
          <Globe className="h-5 w-5 text-muted-foreground" />
          <h2 className="text-base font-semibold">{t('settings:regional')}</h2>
        </div>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">{t('settings:timezone')}</Label>
            <Select
              value={regional.timezone ?? ''}
              onChange={(v) => setRegional((s) => ({ ...s, timezone: v || undefined }))}
              options={TIMEZONES.map((tz) => ({ value: tz, label: tz }))}
              placeholder={`${t('common:default')} — ${effectiveSettings?.regional?.timezone ?? ''}`}
            />
          </div>
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">{t('settings:language')}</Label>
            <Select
              value={regional.language ?? ''}
              onChange={(v) => setRegional((s) => ({ ...s, language: v || undefined }))}
              options={LANGUAGES}
              placeholder={`${t('common:default')} — ${findLabel(LANGUAGES, effectiveSettings?.regional?.language)}`}
            />
          </div>
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">{t('settings:dateFormat')}</Label>
            <Select
              value={regional.dateFormat ?? ''}
              onChange={(v) => setRegional((s) => ({ ...s, dateFormat: v || undefined }))}
              options={DATE_FORMATS}
              placeholder={`${t('common:default')} — ${findLabel(DATE_FORMATS, effectiveSettings?.regional?.dateFormat)}`}
            />
          </div>
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">{t('settings:timeFormat')}</Label>
            <Select
              value={regional.timeFormat ?? ''}
              onChange={(v) => setRegional((s) => ({ ...s, timeFormat: v || undefined }))}
              options={TIME_FORMATS}
              placeholder={`${t('common:default')} — ${findLabel(TIME_FORMATS, effectiveSettings?.regional?.timeFormat)}`}
            />
          </div>
        </div>
      </section>

      {/* ── Currency & Fiscal ── */}
      <section className="border-b border-border/50 pb-7 mb-7">
        <div className="flex items-center gap-2 mb-4">
          <DollarSign className="h-5 w-5 text-muted-foreground" />
          <h2 className="text-base font-semibold">{t('settings:currency')}</h2>
        </div>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">{t('settings:currency')}</Label>
              <Select
                value={currency.code ?? ''}
                onChange={(v) => {
                  const found = CURRENCIES.find((c) => c.value === v)
                  setCurrency((s) => ({
                    ...s,
                    code: v || undefined,
                    symbol: found?.symbol ?? s.symbol,
                  }))
                }}
                options={CURRENCIES.map((c) => ({ value: c.value, label: c.label }))}
                placeholder={`${t('common:default')} — ${findLabel(CURRENCIES.map((c) => ({ value: c.value, label: c.label })), effectiveSettings?.currency?.code)}`}
              />
          </div>
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">{t('settings:currencySymbol')}</Label>
            <Input
              value={currency.symbol ?? ''}
              onChange={(e) => setCurrency((s) => ({ ...s, symbol: e.target.value || undefined }))}
              placeholder="e.g. $, €, ₫"
              disabled={!isAdmin}
              className="max-w-xs"
            />
          </div>
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">{t('settings:decimalPlaces')}</Label>
            <Input
              type="number"
              min={0}
              max={4}
              value={currency.decimalPlaces ?? ''}
              onChange={(e) =>
                setCurrency((s) => ({
                  ...s,
                  decimalPlaces: e.target.value ? parseInt(e.target.value) : undefined,
                }))
              }
              placeholder="e.g. 2"
              disabled={!isAdmin}
              className="max-w-xs"
            />
            {effectiveSettings?.currency?.decimalPlaces !== undefined && (
              <p className="text-xs text-muted-foreground">
                {t('settings:effective')} {effectiveSettings.currency.decimalPlaces}
              </p>
            )}
          </div>
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">{t('settings:fiscalYearStart')}</Label>
            <Input
              value={currency.fiscalYearStart ?? ''}
              onChange={(e) =>
                setCurrency((s) => ({ ...s, fiscalYearStart: e.target.value || undefined }))
              }
              placeholder="e.g. 01-01"
              disabled={!isAdmin}
              className="max-w-xs"
            />
            {effectiveSettings?.currency?.fiscalYearStart && (
              <p className="text-xs text-muted-foreground">
                {t('settings:effective')} {effectiveSettings.currency.fiscalYearStart}
              </p>
            )}
          </div>
        </div>
      </section>

      {/* ── Security ── */}
      <section className="border-b border-border/50 pb-7 mb-7">
        <div className="flex items-center gap-2 mb-4">
          <Shield className="h-5 w-5 text-muted-foreground" />
          <h2 className="text-base font-semibold">{t('settings:security')}</h2>
        </div>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">{t('settings:minPasswordLength')}</Label>
            <Input
              type="number"
              min={4}
              max={128}
              value={security.minPasswordLength ?? ''}
              onChange={(e) =>
                setSecurity((s) => ({
                  ...s,
                  minPasswordLength: e.target.value ? parseInt(e.target.value) : undefined,
                }))
              }
              placeholder="e.g. 8"
              disabled={!isAdmin}
              className="max-w-xs"
            />
            {effectiveSettings?.security?.minPasswordLength !== undefined && (
              <p className="text-xs text-muted-foreground">
                {t('settings:effective')} {effectiveSettings.security.minPasswordLength}
              </p>
            )}
          </div>
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">{t('settings:sessionTimeout')}</Label>
            <Input
              type="number"
              min={5}
              max={1440}
              value={security.sessionTimeoutMinutes ?? ''}
              onChange={(e) =>
                setSecurity((s) => ({
                  ...s,
                  sessionTimeoutMinutes: e.target.value ? parseInt(e.target.value) : undefined,
                }))
              }
              placeholder="e.g. 60"
              disabled={!isAdmin}
              className="max-w-xs"
            />
            {effectiveSettings?.security?.sessionTimeoutMinutes !== undefined && (
              <p className="text-xs text-muted-foreground">
                {t('settings:effective')} {effectiveSettings.security.sessionTimeoutMinutes} min
              </p>
            )}
          </div>
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">{t('settings:maxLoginAttempts')}</Label>
            <Input
              type="number"
              min={1}
              max={20}
              value={security.maxLoginAttempts ?? ''}
              onChange={(e) =>
                setSecurity((s) => ({
                  ...s,
                  maxLoginAttempts: e.target.value ? parseInt(e.target.value) : undefined,
                }))
              }
              placeholder="e.g. 5"
              disabled={!isAdmin}
              className="max-w-xs"
            />
            {effectiveSettings?.security?.maxLoginAttempts !== undefined && (
              <p className="text-xs text-muted-foreground">
                {t('settings:effective')} {effectiveSettings.security.maxLoginAttempts}
              </p>
            )}
          </div>
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">{t('settings:requireMfa')}</Label>
            <div className="flex items-center gap-2 pt-1">
              <Switch
                checked={security.requireMfa ?? false}
                onCheckedChange={(v) => setSecurity((s) => ({ ...s, requireMfa: v || undefined }))}
                disabled={!isAdmin}
              />
              <span className="text-sm text-foreground/70">
                {security.requireMfa ? t('common:enabled') : t('common:disabled')}
              </span>
            </div>
            {effectiveSettings?.security?.requireMfa !== undefined && (
              <p className="text-xs text-muted-foreground">
                {t('settings:effective')} {effectiveSettings.security.requireMfa ? t('common:enabled') : t('common:disabled')}
              </p>
            )}
          </div>
        </div>
      </section>

      {/* ── Features ── */}
      <section className="border-b border-border/50 pb-7 mb-7">
        <div className="flex items-center gap-2 mb-4">
          <Puzzle className="h-5 w-5 text-muted-foreground" />
          <h2 className="text-base font-semibold">{t('settings:features')}</h2>
        </div>
        <div className="space-y-4">
          {FEATURE_KEYS.map((key) => (
            <div key={key} className="flex items-center justify-between gap-4">
              <div className="space-y-0.5">
                <Label className="text-sm font-medium">
                  {key.replace(/^canManage/, '').replace(/([A-Z])/g, ' $1').trim()}
                </Label>
                <p className="text-xs text-muted-foreground">{t(`settings:feature${key.replace(/^canManage/, '')}Desc`)}</p>
              </div>
              <div className="flex items-center gap-2">
                <Switch
                  checked={features[key] ?? false}
                  onCheckedChange={(v) =>
                    setFeatures((s) => ({ ...s, [key]: v || undefined }))
                  }
                  disabled={!isAdmin}
                />
                <span className="text-xs text-foreground/70 w-10">
                  {features[key] ? t('common:on') : t('common:off')}
                </span>
              </div>
            </div>
          ))}
        </div>
        {effectiveSettings?.features && (
          <details className="mt-4">
            <summary className="text-xs text-muted-foreground cursor-pointer hover:text-foreground">
              {t('settings:viewEffective')}
            </summary>
            <div className="mt-2 space-y-1">
              {Object.entries(effectiveSettings.features).map(([key, val]) => (
                <div key={key} className="flex items-center gap-2 text-xs text-muted-foreground">
                  <span className="w-40">{key.replace(/^canManage/, '').replace(/([A-Z])/g, ' $1').trim()}</span>
                  <Badge variant={val ? 'default' : 'secondary'} className="text-[10px] px-1.5 py-0">
                    {val ? t('common:on') : t('common:off')}
                  </Badge>
                </div>
              ))}
            </div>
          </details>
        )}
      </section>

      {/* ── Communication ── */}
      <section className="border-b border-border/50 pb-7 mb-7">
        <div className="flex items-center gap-2 mb-4">
          <MessageSquare className="h-5 w-5 text-muted-foreground" />
          <h2 className="text-base font-semibold">{t('settings:communication')}</h2>
        </div>
        <div className="space-y-4">
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">{t('settings:defaultEmailFrom')}</Label>
            <Input
              type="email"
              value={communication.defaultEmailFrom ?? ''}
              onChange={(e) =>
                setCommunication((s) => ({
                  ...s,
                  defaultEmailFrom: e.target.value || undefined,
                }))
              }
              placeholder="noreply@example.com"
              disabled={!isAdmin}
              className="max-w-sm"
            />
            {effectiveSettings?.communication?.defaultEmailFrom && (
              <p className="text-xs text-muted-foreground">
                {t('settings:effective')} {effectiveSettings.communication.defaultEmailFrom}
              </p>
            )}
          </div>
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">{t('settings:defaultEmailSignature')}</Label>
            <textarea
              value={communication.defaultEmailSignature ?? ''}
              onChange={(e) =>
                setCommunication((s) => ({
                  ...s,
                  defaultEmailSignature: e.target.value || undefined,
                }))
              }
              placeholder="Best regards,&#10;{{name}}&#10;{{company}}"
              disabled={!isAdmin}
              rows={3}
              className="flex w-full max-w-md rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 resize-y min-h-[60px]"
            />
          </div>
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">{t('settings:defaultNotificationChannel')}</Label>
            <Select
              value={communication.defaultNotificationChannel ?? ''}
              onChange={(v) =>
                setCommunication((s) => ({
                  ...s,
                  defaultNotificationChannel: v || undefined,
                }))
              }
              options={NOTIFICATION_CHANNELS}
              placeholder={`${t('common:default')} — ${findLabel(NOTIFICATION_CHANNELS, effectiveSettings?.communication?.defaultNotificationChannel)}`}
            />
          </div>
        </div>
      </section>

      {/* Actions */}
      {isAdmin && (
        <div className="sticky bottom-4 z-20 rounded-lg border bg-card/95 p-4 shadow-lg backdrop-blur supports-[backdrop-filter]:bg-card/80">
          {message && (
            <div
              className={`mb-3 flex items-center gap-2 text-sm ${
                message.type === 'success' ? 'text-success' : 'text-destructive'
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
              {t('settings:immediateEffect')}
            </p>
            <div className="flex gap-2">
              <Button variant="outline" onClick={handleReset} disabled={saving} className="gap-2">
                <RotateCcw className="h-4 w-4" />
                {t('common:reset')}
              </Button>
              <Button onClick={handleSave} disabled={saving} className="gap-2">
                <Save className="h-4 w-4" />
                {saving ? t('common:saving') : t('common:save')}
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
