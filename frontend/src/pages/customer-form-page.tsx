import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import { useCreateCustomerMutation } from '@/shared/api'
import type { Customer } from '@/shared/api'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { useRouter } from '@tanstack/react-router'

export function CustomerFormPage() {
  const { t } = useTranslation()
  const router = useRouter()
  const queryClient = useQueryClient()
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [form, setForm] = useState({
    firstName: '',
    lastName: '',
    email: '',
    phone: '',
    address: '',
    notes: '',
  })

  const handleChange = (field: string) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    setForm((prev) => ({ ...prev, [field]: e.target.value }))
  }

  const createMutation = useCreateCustomerMutation()

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setSaving(true)
    setError(null)
    try {
      const customer = await createMutation.mutateAsync({ body: form }) as Customer
      await queryClient.invalidateQueries({ queryKey: ['list3'], refetchType: 'active' })
      await queryClient.refetchQueries({ queryKey: ['list3'], type: 'active' })
      router.navigate({ to: '/customers/$id', params: { id: customer.id! } })
    } catch (err) {
      setError(t('common:error', 'Failed to create customer'))
      console.error(err)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">{t('common:newCustomer', 'New Customer')}</h1>
        <p className="text-muted-foreground mt-1">{t('common:addCustomerRecord', 'Add a new customer record')}</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t('common:customerDetails', 'Customer Details')}</CardTitle>
          <CardDescription>{t('common:enterCustomerInfo', "Enter the customer's information")}</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="firstName">{t('common:firstName', 'First Name')}</Label>
                <Input id="firstName" value={form.firstName} onChange={handleChange('firstName')} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="lastName">{t('common:lastName', 'Last Name')}</Label>
                <Input id="lastName" value={form.lastName} onChange={handleChange('lastName')} />
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="email">{t('common:email', 'Email')}</Label>
              <Input id="email" type="email" value={form.email} onChange={handleChange('email')} />
            </div>

            <div className="space-y-2">
              <Label htmlFor="phone">{t('common:phone', 'Phone')}</Label>
              <Input id="phone" value={form.phone} onChange={handleChange('phone')} />
            </div>

            <div className="space-y-2">
              <Label htmlFor="address">{t('common:address', 'Address')}</Label>
              <Input id="address" value={form.address} onChange={handleChange('address')} />
            </div>

            <div className="space-y-2">
              <Label htmlFor="notes">Notes</Label>
              <textarea
                id="notes"
                className="flex min-h-[80px] w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                value={form.notes}
                onChange={handleChange('notes')}
              />
            </div>

            {error && (
              <p className="text-sm text-destructive">{error}</p>
            )}

            <div className="flex gap-2 justify-end">
              <Button type="button" variant="outline" onClick={() => router.navigate({ to: '/customers' })}>
                {t('common:cancel', 'Cancel')}
              </Button>
              <Button type="submit" disabled={saving}>
                {saving ? t('common:saving', 'Saving...') : t('common:createCustomer', 'Create Customer')}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
