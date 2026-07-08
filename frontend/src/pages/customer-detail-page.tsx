import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useParams, useRouter } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { useGetCustomerQuery, useUpdateCustomerMutation, useDeleteCustomerMutation } from '@/shared/api'
import type { Customer } from '@/shared/api'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Badge } from '@/shared/ui/badge'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'

export function CustomerDetailPage() {
  const { t } = useTranslation()
  const { id } = useParams({ from: '/_authenticated/customers/$id' })
  const router = useRouter()
  const { data: customer, isLoading } = useGetCustomerQuery({ path: { id } })
  const updateMutation = useUpdateCustomerMutation()
  const deleteMutation = useDeleteCustomerMutation()
  const [editing, setEditing] = useState(false)
  const [saving, setSaving] = useState(false)
  const [form, setForm] = useState<Partial<Customer>>({})
  const queryClient = useQueryClient()

  useEffect(() => {
    if (customer) setForm(customer)
  }, [customer])

  const handleSave = async () => {
    setSaving(true)
    try {
      await updateMutation.mutateAsync({ path: { id }, body: form })
      setEditing(false)
      queryClient.invalidateQueries({ queryKey: ['list3'] })
    } catch (err) {
      console.error(err)
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    if (!confirm(t('common:confirmDelete', 'Are you sure?'))) return
    try {
      await deleteMutation.mutateAsync({ path: { id } })
      router.navigate({ to: '/customers' })
    } catch (err) {
      console.error(err)
    }
  }

  if (isLoading) {
    return <p className="text-muted-foreground py-8 text-center">{t('common:loading', 'Loading...')}</p>
  }

  if (!customer) {
    return <p className="text-destructive py-8 text-center">{t('common:customerNotFound', 'Customer not found')}</p>
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">
            {[customer.firstName, customer.lastName].filter(Boolean).join(' ') || t('common:unnamed', 'Unnamed')}
          </h1>
          <p className="text-muted-foreground mt-1">{t('common:customerDetails', 'Customer Details')}</p>
        </div>
        <div className="flex gap-2">
          {editing ? (
            <>
              <Button variant="outline" onClick={() => { setEditing(false); setForm(customer) }}>
                {t('common:cancel', 'Cancel')}
              </Button>
              <Button onClick={handleSave} disabled={saving}>
                {saving ? t('common:saving', 'Saving...') : t('common:save', 'Save')}
              </Button>
            </>
          ) : (
            <>
              <Button variant="outline" onClick={() => setEditing(true)}>{t('common:edit', 'Edit')}</Button>
              <Button variant="destructive" onClick={handleDelete}>{t('common:delete', 'Delete')}</Button>
            </>
          )}
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t('common:information', 'Information')}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label htmlFor="field-firstName">{t('common:firstName', 'First Name')}</Label>
              {editing ? (
                <Input id="field-firstName" value={form.firstName || ''} onChange={(e) => setForm({ ...form, firstName: e.target.value })} />
              ) : (
                <p className="text-sm">{customer.firstName || '-'}</p>
              )}
            </div>
            <div className="space-y-2">
              <Label htmlFor="field-lastName">{t('common:lastName', 'Last Name')}</Label>
              {editing ? (
                <Input id="field-lastName" value={form.lastName || ''} onChange={(e) => setForm({ ...form, lastName: e.target.value })} />
              ) : (
                <p className="text-sm">{customer.lastName || '-'}</p>
              )}
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="field-email">{t('common:email', 'Email')}</Label>
            {editing ? (
              <Input id="field-email" value={form.email || ''} onChange={(e) => setForm({ ...form, email: e.target.value })} />
            ) : (
              <p className="text-sm">{customer.email || '-'}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="field-phone">{t('common:phone', 'Phone')}</Label>
            {editing ? (
              <Input id="field-phone" value={form.phone || ''} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
            ) : (
              <p className="text-sm">{customer.phone || '-'}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="field-address">{t('common:address', 'Address')}</Label>
            {editing ? (
              <Input id="field-address" value={form.address || ''} onChange={(e) => setForm({ ...form, address: e.target.value })} />
            ) : (
              <p className="text-sm">{customer.address || '-'}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label>{t('common:status', 'Status')}</Label>
            <Badge variant={customer.active ? 'default' : 'secondary'}>
              {customer.active ? t('common:active', 'Active') : t('common:inactive', 'Inactive')}
            </Badge>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
