import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import { LockKeyhole, RefreshCw } from 'lucide-react'
import { useListQuery, useCreateBusinessMutation, useUpdateBusinessMutation, useMeQuery, listQueryKey } from '@/shared/api'
import type { Business } from '@/shared/api'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'

export function BusinessPage() {
  const { t } = useTranslation()
  const { data: me, isLoading: isMeLoading } = useMeQuery()
  const roleName = me?.role?.name
  const isCustomer = roleName === 'customer'
  const isOperator = roleName === 'operator'
  const canManageBusiness = roleName === 'admin' || roleName === 'businessOwner'

  const { data: businesses, isLoading } = useListQuery()
  const createMutation = useCreateBusinessMutation()
  const updateMutation = useUpdateBusinessMutation()
  const [business, setBusiness] = useState<Business | null>(null)
  const [editing, setEditing] = useState(false)
  const [saving, setSaving] = useState(false)
  const [form, setForm] = useState<Partial<Business>>({})
  const queryClient = useQueryClient()

  useEffect(() => {
    if (businesses !== undefined) {
      const biz = businesses[0] || null
      setBusiness(biz)
      if (biz) setForm({ ...biz })
    }
  }, [businesses])

  const handleCreate = async () => {
    setSaving(true)
    try {
      const created = await createMutation.mutateAsync({ body: { name: 'My Business' } as Business }) as Business
      setBusiness(created)
      setForm(created)
      await queryClient.invalidateQueries({ queryKey: listQueryKey(), refetchType: 'active' })
      await queryClient.refetchQueries({ queryKey: listQueryKey(), type: 'active' })
      setEditing(true)
    } catch (err) {
      console.error(err)
    } finally {
      setSaving(false)
    }
  }

  const handleSave = async () => {
    if (!business) return
    setSaving(true)
    try {
      const updated = await updateMutation.mutateAsync({ path: { id: business.id! }, body: form })
      setBusiness(updated)
      setEditing(false)
      await queryClient.invalidateQueries({ queryKey: listQueryKey(), refetchType: 'active' })
      await queryClient.refetchQueries({ queryKey: listQueryKey(), type: 'active' })
    } catch (err) {
      console.error(err)
    } finally {
      setSaving(false)
    }
  }

  if (isLoading || isMeLoading) {
    return <p className="text-muted-foreground py-8 text-center">{t('common:loading', 'Loading...')}</p>
  }

  if (!roleName || isCustomer) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[300px] border border-dashed rounded-lg p-8 text-center space-y-4">
        <h2 className="text-xl font-bold text-destructive">Access Denied</h2>
        <p className="text-muted-foreground max-w-md">
          Only staff members are allowed to view business details.
        </p>
      </div>
    )
  }

  if (!business) {
    return (
      <div className="space-y-6">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('common:businessInformation', 'Business Information')}</h1>
          <p className="text-muted-foreground mt-1">{t('common:businessNotFound', 'No business record found.')}</p>
        </div>
        {canManageBusiness ? (
          <Button onClick={handleCreate} disabled={saving}>
            {saving ? t('common:saving', 'Creating...') : t('common:createBusiness', 'Create Business Profile')}
          </Button>
        ) : (
          <div className="flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-800 dark:bg-amber-950/30 dark:text-amber-100">
            <LockKeyhole className="mt-0.5 h-4 w-4 shrink-0" />
            <p>The owner has not assigned a business workspace to this operator yet.</p>
          </div>
        )}
      </div>
    )
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('common:businessInformation', 'Business Information')}</h1>
          <div className="mt-1 flex flex-wrap items-center gap-2">
            <p className="text-muted-foreground">
              {isOperator
                ? 'Shared workspace synchronized with the business owner'
                : t('common:manageBusinessProfile', 'Manage your business profile')}
            </p>
            {isOperator && <Badge variant="secondary">Operator · Read only</Badge>}
          </div>
        </div>
        {canManageBusiness && editing ? (
          <div className="flex gap-2">
            <Button variant="outline" onClick={() => { setEditing(false); setForm(business) }}>
              {t('common:cancel', 'Cancel')}
            </Button>
            <Button onClick={handleSave} disabled={saving}>
              {saving ? t('common:saving', 'Saving...') : t('common:save', 'Save')}
            </Button>
          </div>
        ) : canManageBusiness ? (
          <Button variant="outline" onClick={() => setEditing(true)}>{t('common:edit', 'Edit')}</Button>
        ) : null}
      </div>

      {isOperator && (
        <div className="flex items-start gap-3 rounded-lg border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-900 dark:border-blue-800 dark:bg-blue-950/30 dark:text-blue-100">
          <RefreshCw className="mt-0.5 h-4 w-4 shrink-0" />
          <p>
            You are working in <span className="font-semibold">{business.name}</span>. Products, orders and payment activity are synchronized with the owner; profile settings, wallet and payouts remain owner-only.
          </p>
        </div>
      )}

      <Card>
        <CardHeader>
          <CardTitle>{t('common:details', 'Details')}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="field-name">{t('common:businessName', 'Business Name')}</Label>
              {editing ? (
                <Input id="field-name" value={form.name || ''} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            ) : (
              <p className="text-sm font-medium">{business.name}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="field-address">{t('common:address', 'Address')}</Label>
              {editing ? (
                <Input id="field-address" value={form.address || ''} onChange={(e) => setForm({ ...form, address: e.target.value })} />
            ) : (
              <p className="text-sm">{business.address || '-'}</p>
            )}
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="field-phone">{t('common:phone', 'Phone')}</Label>
              {editing ? (
                <Input id="field-phone" value={form.phone || ''} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
              ) : (
                <p className="text-sm">{business.phone || '-'}</p>
              )}
            </div>
            <div className="space-y-2">
              <Label htmlFor="field-email">{t('common:email', 'Email')}</Label>
              {editing ? (
                <Input id="field-email" value={form.email || ''} onChange={(e) => setForm({ ...form, email: e.target.value })} />
              ) : (
                <p className="text-sm">{business.email || '-'}</p>
              )}
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="field-taxId">{t('common:taxId', 'Tax ID')}</Label>
              {editing ? (
                <Input id="field-taxId" value={form.taxId || ''} onChange={(e) => setForm({ ...form, taxId: e.target.value })} />
            ) : (
              <p className="text-sm">{business.taxId || '-'}</p>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
