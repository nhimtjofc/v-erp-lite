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
import { Save, RotateCcw, Check, X, MessageSquare } from 'lucide-react'
import {
  type CommunicationSettings,
  type SettingsMap,
  NOTIFICATION_CHANNELS,
  Select,
} from './settings-shared'

export function CommunicationPage() {
  const { t } = useTranslation()
  const { data: me } = useMeQuery()
  const { data: tenantSettings } = useGetTenantSettingsQuery()
  const { data: effectiveSettings } = useGetEffectiveSettingsQuery()
  const mutation = usePutTenantSettingsMutation()

  const isAdmin = me?.role?.name === 'admin' || me?.role?.name === 'businessOwner' || me?.role?.name === 'operator'

  const [communication, setCommunication] = useState<CommunicationSettings>({})
  const [savedCommunication, setSavedCommunication] = useState<CommunicationSettings>({})
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null)

  const settings = tenantSettings as SettingsMap | undefined
  const effective = effectiveSettings as SettingsMap | undefined

  useEffect(() => {
    if (settings?.communication) {
      const c = settings.communication
      setCommunication(c)
      setSavedCommunication(c)
    }
  }, [settings])

  useEffect(() => {
    if (message) {
      const timer = setTimeout(() => setMessage(null), 4000)
      return () => clearTimeout(timer)
    }
  }, [message])

  const hasChanges = JSON.stringify(communication) !== JSON.stringify(savedCommunication)

  const handleSave = () => {
    mutation.mutate(
      { body: { settings: { communication } } },
      {
        onSuccess: () => {
          setSavedCommunication({ ...communication })
          setMessage({ type: 'success', text: t('common:saved') })
        },
        onError: () => {
          setMessage({ type: 'error', text: t('common:error') })
        },
      },
    )
  }

  const handleReset = () => {
    setCommunication({ ...savedCommunication })
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <MessageSquare className="h-6 w-6 text-muted-foreground" />
        <div className="flex-1">
          <div className="flex items-center gap-2">
            <h2 className="text-lg font-semibold">{t('settings:sectionCommunication')}</h2>
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
          <Label htmlFor="defaultEmailFrom">{t('settings:defaultEmailFrom')}</Label>
          <Input
            id="defaultEmailFrom"
            type="email"
            value={communication.defaultEmailFrom ?? effective?.communication?.defaultEmailFrom ?? ''}
            onChange={(e) =>
              setCommunication((prev) => ({
                ...prev,
                defaultEmailFrom: e.target.value,
              }))
            }
            disabled={!isAdmin}
            placeholder="noreply@example.com"
          />
          {effective?.communication?.defaultEmailFrom && !communication.defaultEmailFrom && (
            <p className="text-xs text-muted-foreground">
              {t('settings:effectiveDefault')}: {effective.communication.defaultEmailFrom}
            </p>
          )}
        </div>

        <div className="space-y-2">
          <Label htmlFor="defaultEmailSignature">{t('settings:defaultEmailSignature')}</Label>
          <textarea
            id="defaultEmailSignature"
            value={communication.defaultEmailSignature ?? effective?.communication?.defaultEmailSignature ?? ''}
            onChange={(e) =>
              setCommunication((prev) => ({
                ...prev,
                defaultEmailSignature: e.target.value,
              }))
            }
            disabled={!isAdmin}
            rows={4}
            className="flex w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 placeholder:text-muted-foreground"
            placeholder={t('settings:emailSignaturePlaceholder')}
          />
          {effective?.communication?.defaultEmailSignature && !communication.defaultEmailSignature && (
            <p className="text-xs text-muted-foreground">
              {t('settings:effectiveDefault')}: {effective.communication.defaultEmailSignature}
            </p>
          )}
        </div>

        <div className="space-y-2">
          <Label htmlFor="defaultNotificationChannel">{t('settings:defaultNotificationChannel')}</Label>
          <Select
            value={communication.defaultNotificationChannel ?? effective?.communication?.defaultNotificationChannel ?? ''}
            onChange={(v) =>
              setCommunication((prev) => ({
                ...prev,
                defaultNotificationChannel: v,
              }))
            }
            options={NOTIFICATION_CHANNELS}
            placeholder={t('common:select')}
            disabled={!isAdmin}
          />
          {effective?.communication?.defaultNotificationChannel && !communication.defaultNotificationChannel && (
            <p className="text-xs text-muted-foreground">
              {t('settings:effectiveDefault')}: {effective.communication.defaultNotificationChannel}
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
