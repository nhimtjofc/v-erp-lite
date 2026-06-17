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
import { Save, RotateCcw, Check, X, DollarSign } from 'lucide-react'
import {
  type CurrencySettings,
  type SettingsMap,
  CURRENCIES,
  Select,
} from './settings-shared'

export function CurrencyPage() {
  const { t } = useTranslation()
  const { data: me } = useMeQuery()
  const { data: tenantSettings } = useGetTenantSettingsQuery()
  const { data: effectiveSettings } = useGetEffectiveSettingsQuery()
  const mutation = usePutTenantSettingsMutation()

  const isAdmin = me?.role?.name === 'admin'

  const [currency, setCurrency] = useState<CurrencySettings>({})
  const [savedCurrency, setSavedCurrency] = useState<CurrencySettings>({})
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null)

  const settings = tenantSettings as SettingsMap | undefined
  const effective = effectiveSettings as SettingsMap | undefined

  useEffect(() => {
    if (settings?.currency) {
      const c = settings.currency
      setCurrency(c)
      setSavedCurrency(c)
    }
  }, [settings])

  useEffect(() => {
    if (message) {
      const timer = setTimeout(() => setMessage(null), 4000)
      return () => clearTimeout(timer)
    }
  }, [message])

  const hasChanges = JSON.stringify(currency) !== JSON.stringify(savedCurrency)

  const handleSave = () => {
    mutation.mutate(
      { body: { settings: { currency } } },
      {
        onSuccess: () => {
          setSavedCurrency({ ...currency })
          setMessage({ type: 'success', text: t('common:saved') })
        },
        onError: () => {
          setMessage({ type: 'error', text: t('common:error') })
        },
      },
    )
  }

  const handleReset = () => {
    setCurrency({ ...savedCurrency })
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <DollarSign className="h-6 w-6 text-muted-foreground" />
        <div className="flex-1">
          <div className="flex items-center gap-2">
            <h2 className="text-lg font-semibold">{t('settings:sectionCurrency')}</h2>
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
          <Label htmlFor="currencyCode">{t('settings:currency')}</Label>
          <Select
            value={currency.code ?? effective?.currency?.code ?? ''}
            onChange={(v) => setCurrency((prev) => ({ ...prev, code: v }))}
            options={CURRENCIES}
            placeholder={t('common:select')}
          />
          {effective?.currency?.code && !currency.code && (
            <p className="text-xs text-muted-foreground">
              {t('settings:effectiveDefault')}: {effective.currency.code}
            </p>
          )}
        </div>

        <div className="space-y-2">
          <Label htmlFor="currencySymbol">{t('settings:currencySymbol')}</Label>
          <Input
            id="currencySymbol"
            value={currency.symbol ?? effective?.currency?.symbol ?? ''}
            onChange={(e) => setCurrency((prev) => ({ ...prev, symbol: e.target.value }))}
            disabled={!isAdmin}
          />
          {effective?.currency?.symbol && !currency.symbol && (
            <p className="text-xs text-muted-foreground">
              {t('settings:effectiveDefault')}: {effective.currency.symbol}
            </p>
          )}
        </div>

        <div className="space-y-2">
          <Label htmlFor="decimalPlaces">{t('settings:decimalPlaces')}</Label>
          <Input
            id="decimalPlaces"
            type="number"
            value={currency.decimalPlaces ?? effective?.currency?.decimalPlaces ?? ''}
            onChange={(e) =>
              setCurrency((prev) => ({
                ...prev,
                decimalPlaces: e.target.value ? Number(e.target.value) : undefined,
              }))
            }
            disabled={!isAdmin}
            min={0}
            max={10}
          />
          {effective?.currency?.decimalPlaces != null && currency.decimalPlaces == null && (
            <p className="text-xs text-muted-foreground">
              {t('settings:effectiveDefault')}: {effective.currency.decimalPlaces}
            </p>
          )}
        </div>

        <div className="space-y-2">
          <Label htmlFor="fiscalYearStart">{t('settings:fiscalYearStart')}</Label>
          <Input
            id="fiscalYearStart"
            value={currency.fiscalYearStart ?? effective?.currency?.fiscalYearStart ?? ''}
            onChange={(e) => setCurrency((prev) => ({ ...prev, fiscalYearStart: e.target.value }))}
            placeholder="MM-DD"
            disabled={!isAdmin}
          />
          {effective?.currency?.fiscalYearStart && !currency.fiscalYearStart && (
            <p className="text-xs text-muted-foreground">
              {t('settings:effectiveDefault')}: {effective.currency.fiscalYearStart}
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
