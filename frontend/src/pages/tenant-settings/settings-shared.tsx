import type { ComponentProps } from 'react'

// ── Types ──

export interface RegionalSettings {
  timezone?: string
  language?: string
  dateFormat?: string
  timeFormat?: string
}

export interface CurrencySettings {
  code?: string
  symbol?: string
  decimalPlaces?: number
  fiscalYearStart?: string
}

export interface SecuritySettings {
  minPasswordLength?: number
  requireMfa?: boolean
  sessionTimeoutMinutes?: number
  maxLoginAttempts?: number
}

export interface FeatureToggles {
  canManageCustomers?: boolean
  canManageBusiness?: boolean
  canManageUsers?: boolean
  canManageSettings?: boolean
  canManageRoles?: boolean
}

export interface CommunicationSettings {
  defaultEmailFrom?: string
  defaultEmailSignature?: string
  defaultNotificationChannel?: string
}

export interface SettingsMap {
  regional?: RegionalSettings
  currency?: CurrencySettings
  security?: SecuritySettings
  features?: FeatureToggles
  communication?: CommunicationSettings
}

// ── Constants ──

export const TIMEZONES = [
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

export const LANGUAGES = [
  { value: 'en', label: 'English' },
  { value: 'vi', label: 'Tiếng Việt' },
  { value: 'ja', label: '日本語' },
  { value: 'zh', label: '中文' },
  { value: 'ko', label: '한국어' },
  { value: 'de', label: 'Deutsch' },
  { value: 'fr', label: 'Français' },
]

export const DATE_FORMATS = [
  { value: 'YYYY-MM-DD', label: '2024-12-31' },
  { value: 'DD/MM/YYYY', label: '31/12/2024' },
  { value: 'MM/DD/YYYY', label: '12/31/2024' },
  { value: 'DD.MM.YYYY', label: '31.12.2024' },
]

export const TIME_FORMATS = [
  { value: '24h', label: '14:30' },
  { value: '12h', label: '2:30 PM' },
]

export const CURRENCIES = [
  { value: 'USD', label: 'USD ($)', symbol: '$' },
  { value: 'EUR', label: 'EUR (€)', symbol: '€' },
  { value: 'GBP', label: 'GBP (£)', symbol: '£' },
  { value: 'JPY', label: 'JPY (¥)', symbol: '¥' },
  { value: 'VND', label: 'VND (₫)', symbol: '₫' },
  { value: 'SGD', label: 'SGD ($)', symbol: 'S$' },
  { value: 'AUD', label: 'AUD ($)', symbol: 'A$' },
]

export const NOTIFICATION_CHANNELS = [
  { value: 'email', label: 'Email' },
  { value: 'in-app', label: 'In-App' },
]

export const FEATURE_KEYS: Array<keyof FeatureToggles> = [
  'canManageCustomers',
  'canManageBusiness',
  'canManageUsers',
  'canManageSettings',
  'canManageRoles',
]

// ── Helpers ──

export function settingValue(
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

export function findLabel(arr: { value: string; label: string }[], value: string | undefined): string {
  if (!value) return ''
  return arr.find((o) => o.value === value)?.label ?? value
}

export function Select({
  value,
  onChange,
  options,
  placeholder,
  disabled,
}: {
  value: string
  onChange: (v: string) => void
  options: { value: string; label: string }[]
  placeholder?: string
  disabled?: boolean
}) {
  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value)}
      disabled={disabled}
      className="flex h-9 w-full max-w-xs rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
    >
      {placeholder && <option value="">{placeholder}</option>}
      {options.map((o) => (
        <option key={o.value} value={o.value}>{o.label}</option>
      ))}
    </select>
  )
}
