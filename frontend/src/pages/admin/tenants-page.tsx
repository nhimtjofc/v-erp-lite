import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useList7Query, useCreate6Mutation, useUpdate5Mutation, useDelete6Mutation } from '@/shared/api'
import type { Tenant } from '@/shared/api'
import { Button } from '@/shared/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'
import { Badge } from '@/shared/ui/badge'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/shared/ui/dialog'
import { Link } from '@tanstack/react-router'
import { Plus, Pencil, Trash2, Users } from 'lucide-react'
import { useTranslation } from 'react-i18next'

export function TenantsPage() {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()
  const { data: tenants, isLoading } = useList7Query()
  const createMutation = useCreate6Mutation()
  const updateMutation = useUpdate5Mutation()
  const deleteMutation = useDelete6Mutation()

  const tenantList = tenants ?? []

  // Dialog state
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingTenant, setEditingTenant] = useState<Tenant | null>(null)
  const [saving, setSaving] = useState(false)
  const [form, setForm] = useState<Partial<Tenant>>({ code: '', name: '', active: true })

  const openCreate = () => {
    setEditingTenant(null)
    setForm({ code: '', name: '', active: true })
    setDialogOpen(true)
  }

  const openEdit = (tenant: Tenant) => {
    setEditingTenant(tenant)
    setForm({ ...tenant })
    setDialogOpen(true)
  }

  const handleSave = async () => {
    setSaving(true)
    try {
      if (editingTenant) {
        await updateMutation.mutateAsync({ path: { id: editingTenant.id! }, body: form })
      } else {
        await createMutation.mutateAsync({ body: form })
      }
      setDialogOpen(false)
      queryClient.invalidateQueries({ queryKey: ['list7'] })
    } catch (err) {
      console.error(err)
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async (tenant: Tenant) => {
    if (!confirm(t('confirmDeleteTenant'))) return
    try {
      await deleteMutation.mutateAsync({ path: { id: tenant.id! } })
      queryClient.invalidateQueries({ queryKey: ['list7'] })
    } catch (err) {
      console.error(err)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('tenants')}</h1>
          <p className="text-muted-foreground mt-1">{t('pageTitle')}</p>
        </div>
        <Button onClick={openCreate}>
          <Plus className="h-4 w-4" />
          {t('createTenant')}
        </Button>
      </div>

      <section>
        <h2 className="text-lg font-semibold mb-4">{t('tenants')}</h2>
        {isLoading ? (
          <p className="text-sm text-muted-foreground py-8 text-center">Loading...</p>
        ) : tenantList.length === 0 ? (
          <p className="text-sm text-muted-foreground py-8 text-center">{t('noTenantsFound')}</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('tenantCode')}</TableHead>
                <TableHead>{t('tenantName')}</TableHead>
                <TableHead>{t('active')}</TableHead>
                <TableHead className="w-[180px]"></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {tenantList.map((tenant) => (
                <TableRow key={tenant.id!}>
                  <TableCell className="font-mono text-sm">{tenant.code}</TableCell>
                  <TableCell className="font-medium">{tenant.name}</TableCell>
                  <TableCell>
                    <Badge variant={tenant.active ? 'default' : 'secondary'}>
                      {tenant.active ? t('active') : t('inactive')}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-1">
                      <Link to="/admin/tenants/$id" params={{ id: tenant.id! }}>
                        <Button variant="ghost" size="sm">
                          <Users className="h-3.5 w-3.5" />
                        </Button>
                      </Link>
                      <Button variant="ghost" size="sm" onClick={() => openEdit(tenant)}>
                        <Pencil className="h-3.5 w-3.5" />
                      </Button>
                      <Button variant="ghost" size="sm" onClick={() => handleDelete(tenant)}>
                        <Trash2 className="h-3.5 w-3.5" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </section>

      {/* Create/Edit Dialog */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editingTenant ? t('editTenant') : t('createTenant')}</DialogTitle>
            <DialogDescription>
              {editingTenant ? t('editTenant') : t('createTenant')}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="code">{t('tenantCode')}</Label>
              <Input
                id="code"
                value={form.code ?? ''}
                onChange={(e) => setForm({ ...form, code: e.target.value })}
                placeholder="e.g. acme-corp"
                disabled={!!editingTenant}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="name">{t('tenantName')}</Label>
              <Input
                id="name"
                value={form.name ?? ''}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                placeholder="e.g. Acme Corp"
              />
            </div>
            <div className="flex items-center gap-2">
              <input
                type="checkbox"
                id="active"
                checked={form.active ?? true}
                onChange={(e) => setForm({ ...form, active: e.target.checked })}
                className="h-4 w-4 rounded border-gray-300"
              />
              <Label htmlFor="active">{t('active')}</Label>
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <Button variant="outline" onClick={() => setDialogOpen(false)}>
                {t('cancel', { ns: 'common' })}
              </Button>
              <Button onClick={handleSave} disabled={saving}>
                {saving ? t('saving', { ns: 'common' }) : t('save', { ns: 'common' })}
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}
