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
import { Switch } from '@/shared/ui/switch'
import { Badge } from '@/shared/ui/badge'
import { Save, RotateCcw, Check, X, Puzzle } from 'lucide-react'
import {
  type FeatureToggles,
  type SettingsMap,
  FEATURE_KEYS,
} from './settings-shared'

const FEATURE_LABELS: Record<string, string> = {
  canManageCustomers: 'settings:featureDescriptions.canManageCustomers',
  canManageBusiness: 'settings:featureDescriptions.canManageBusiness',
  canManageUsers: 'settings:featureDescriptions.canManageUsers',
  canManageSettings: 'settings:featureDescriptions.canManageSettings',
  canManageRoles: 'settings:featureDescriptions.canManageRoles',
}

export function FeaturesPage() {
  const { t } = useTranslation()
  const { data: me } = useMeQuery()
  const { data: tenantSettings } = useGetTenantSettingsQuery()
  const { data: effectiveSettings } = useGetEffectiveSettingsQuery()
  const mutation = usePutTenantSettingsMutation()

  const isAdmin = me?.role?.name === 'admin' || me?.role?.name === 'businessOwner' || me?.role?.name === 'operator'

  const [features, setFeatures] = useState<FeatureToggles>({})
  const [savedFeatures, setSavedFeatures] = useState<FeatureToggles>({})
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null)

  const settings = tenantSettings as SettingsMap | undefined
  const effective = effectiveSettings as SettingsMap | undefined

  useEffect(() => {
    if (settings?.features) {
      const f = settings.features
      setFeatures(f)
      setSavedFeatures(f)
    }
  }, [settings])

  useEffect(() => {
    if (message) {
      const timer = setTimeout(() => setMessage(null), 4000)
      return () => clearTimeout(timer)
    }
  }, [message])

  const hasChanges = JSON.stringify(features) !== JSON.stringify(savedFeatures)

  const handleSave = () => {
    mutation.mutate(
      { body: { settings: { features } } },
      {
        onSuccess: () => {
          setSavedFeatures({ ...features })
          setMessage({ type: 'success', text: t('common:saved') })
        },
        onError: () => {
          setMessage({ type: 'error', text: t('common:error') })
        },
      },
    )
  }

  const handleReset = () => {
    setFeatures({ ...savedFeatures })
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <Puzzle className="h-6 w-6 text-muted-foreground" />
        <div className="flex-1">
          <div className="flex items-center gap-2">
            <h2 className="text-lg font-semibold">{t('settings:sectionFeatures')}</h2>
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

      {/* Feature toggles */}
      <div className="space-y-3">
        {FEATURE_KEYS.map((key) => {
          const savedValue = savedFeatures[key]
          const currentValue = features[key]
          const effectiveValue = effective?.features?.[key]

          return (
            <div key={key} className="flex items-center justify-between rounded-lg border p-4">
              <div className="space-y-0.5">
                <Label htmlFor={key}>{t(FEATURE_LABELS[key])}</Label>
                {effectiveValue != null && currentValue == null && (
                  <p className="text-xs text-muted-foreground">
                    {t('settings:effectiveDefault')}: {effectiveValue ? t('common:enabled') : t('common:disabled')}
                  </p>
                )}
              </div>
              <div className="flex items-center gap-2">
                {savedValue !== undefined && savedValue !== currentValue && currentValue !== undefined && (
                  <Badge variant="outline" className="text-[10px] px-1.5 py-0">
                    {t('common:changed')}
                  </Badge>
                )}
                <Switch
                  id={key}
                  checked={currentValue ?? effectiveValue ?? false}
                  onCheckedChange={(checked) =>
                    setFeatures((prev) => ({ ...prev, [key]: checked }))
                  }
                  disabled={!isAdmin}
                />
              </div>
            </div>
          )
        })}
      </div>

      {/* Effective features summary */}
      {effective?.features && (
        <details className="rounded-lg border">
          <summary className="cursor-pointer px-4 py-3 text-sm font-medium text-muted-foreground hover:text-foreground">
            {t('settings:effectiveFeatures')}
          </summary>
          <div className="border-t px-4 py-3 space-y-2">
            {FEATURE_KEYS.map((key) => {
              const effectiveValue = effective.features?.[key]
              return (
                <div key={key} className="flex items-center justify-between text-sm">
                  <span>{t(FEATURE_LABELS[key])}</span>
                  <Badge variant={effectiveValue ? 'default' : 'secondary'}>
                    {effectiveValue ? t('common:enabled') : t('common:disabled')}
                  </Badge>
                </div>
              )
            })}
          </div>
        </details>
      )}

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
