import { useState } from 'react'
import { useParams, useRouter } from '@tanstack/react-router'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useGet3Query, useDelete3Mutation } from '@/shared/api'
import {
  listTenantUsers,
  useCreateTenantUserMutation,
  useUpdateTenantUserMutation,
  useDeleteTenantUserMutation,
} from '@/shared/api'
import type { User, Role } from '@/shared/api'
import { Button } from '@/shared/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'
import { Badge } from '@/shared/ui/badge'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/shared/ui/dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui/select'
import { ArrowLeft, Plus, Pencil, Trash2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'

// Inline role listing — roles are stable seeds (admin, operator, businessOwner).
// Fetch from API if the dropdown ever needs to be dynamic.
const ROLES: Role[] = [
  { id: 'admin', name: 'admin' },
  { id: 'operator', name: 'operator' },
  { id: 'businessOwner', name: 'businessOwner' },
]

export function TenantDetailPage() {
  const { id } = useParams({ from: '/_authenticated/admin/tenants/$id' })
  const router = useRouter()
  const { t } = useTranslation('admin')

  // Tenant data
  const { data: tenant, isLoading: tenantLoading } = useGet3Query({ path: { id } })
  const deleteTenantMutation = useDelete3Mutation()
  const queryClient = useQueryClient()

  // Users in this tenant — only fetch when tenant code is available
  const tenantCode = tenant?.code
  const { data: users, isLoading: usersLoading, refetch: refetchUsers } = useQuery({
    queryKey: ['listTenantUsers', tenantCode],
    queryFn: async ({ signal }) => {
      const { data } = await listTenantUsers({ path: { tenantId: tenantCode! }, signal, throwOnError: true })
      return data
    },
    enabled: !!tenantCode,
  })
  const createUserMutation = useCreateTenantUserMutation()
  const updateUserMutation = useUpdateTenantUserMutation()
  const deleteUserMutation = useDeleteTenantUserMutation()

  const userList = users ?? []

  // User dialog state
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingUser, setEditingUser] = useState<User | null>(null)
  const [saving, setSaving] = useState(false)
  const [userForm, setUserForm] = useState<Partial<User>>({
    email: '',
    firstName: '',
    lastName: '',
    role: ROLES[0],
    active: true,
  })

  const openCreateUser = () => {
    setEditingUser(null)
    setUserForm({ email: '', firstName: '', lastName: '', role: ROLES[0], active: true })
    setDialogOpen(true)
  }

  const openEditUser = (user: User) => {
    setEditingUser(user)
    setUserForm({ ...user })
    setDialogOpen(true)
  }

  const handleSaveUser = async () => {
    if (!tenantCode) return
    setSaving(true)
    try {
      // Send role as {name: "..."} only — the backend's resolveRole()
      // supports name-based lookup. Omitting the fake string ID avoids
      // Jackson/Hibernate UUID deserialization errors.
      const body = {
        ...userForm,
        role: userForm.role?.name ? { name: userForm.role.name } : userForm.role,
      }
      if (editingUser) {
        await updateUserMutation.mutateAsync({
          path: { tenantId: tenantCode, id: editingUser.id! },
          body,
        })
      } else {
        await createUserMutation.mutateAsync({
          path: { tenantId: tenantCode },
          body,
        })
      }
      setDialogOpen(false)
      refetchUsers()
    } catch (err) {
      console.error(err)
    } finally {
      setSaving(false)
    }
  }

  const handleDeleteUser = async (user: User) => {
    if (!tenantCode) return
    if (!confirm(t('confirmDeleteUser'))) return
    try {
      await deleteUserMutation.mutateAsync({ path: { tenantId: tenantCode, id: user.id! } })
      refetchUsers()
    } catch (err) {
      console.error(err)
    }
  }

  const handleDeleteTenant = async () => {
    if (!confirm(t('confirmDeleteTenant'))) return
    try {
      await deleteTenantMutation.mutateAsync({ path: { id } })
      queryClient.invalidateQueries({ queryKey: ['list3'] })
      router.navigate({ to: '/admin/tenants' })
    } catch (err) {
      console.error(err)
    }
  }

  if (tenantLoading) {
    return <p className="text-muted-foreground py-8 text-center">Loading...</p>
  }

  if (!tenant) {
    return <p className="text-muted-foreground py-8 text-center">Tenant not found</p>
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Button variant="ghost" size="icon" onClick={() => router.navigate({ to: '/admin/tenants' })}>
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <div>
            <h1 className="text-3xl font-bold tracking-tight">{tenant.name}</h1>
            <p className="text-muted-foreground mt-1">
              {t('tenantCode')}: {tenant.code}
            </p>
          </div>
        </div>
        <Button variant="destructive" size="sm" onClick={handleDeleteTenant}>
          <Trash2 className="h-4 w-4" />
          {t('deleteTenant')}
        </Button>
      </div>

      {/* Tenant info */}
      <section className="border-b border-border/50 pb-7 mb-7">
        <h2 className="text-lg font-semibold mb-4">{t('tenantDetails')}</h2>
        <div className="grid grid-cols-2 gap-4 text-sm">
          <div>
            <span className="text-muted-foreground">{t('tenantCode')}:</span>
            <span className="ml-2 font-mono">{tenant.code}</span>
          </div>
          <div>
            <span className="text-muted-foreground">{t('tenantName')}:</span>
            <span className="ml-2">{tenant.name}</span>
          </div>
          <div>
            <span className="text-muted-foreground">{t('active')}:</span>
            <Badge variant={tenant.active ? 'default' : 'secondary'} className="ml-2">
              {tenant.active ? t('active') : t('inactive')}
            </Badge>
          </div>
          <div>
            <span className="text-muted-foreground">{t('totalUsers')}:</span>
            <span className="ml-2">{userList.length}</span>
          </div>
        </div>
      </section>

      {/* Users section */}
      <section>
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold">{t('tenantUsers')}</h2>
          <Button size="sm" onClick={openCreateUser}>
            <Plus className="h-4 w-4" />
            {t('createUser')}
          </Button>
        </div>
        {usersLoading ? (
          <p className="text-sm text-muted-foreground py-8 text-center">Loading...</p>
        ) : userList.length === 0 ? (
          <p className="text-sm text-muted-foreground py-8 text-center">{t('noUsersFound')}</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('userEmail')}</TableHead>
                <TableHead>{t('userFirstName')}</TableHead>
                <TableHead>{t('userLastName')}</TableHead>
                <TableHead>{t('userRole')}</TableHead>
                <TableHead>{t('userStatus')}</TableHead>
                <TableHead className="w-[100px]"></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {userList.map((user) => (
                <TableRow key={user.id!}>
                  <TableCell className="font-medium">{user.email}</TableCell>
                  <TableCell>{user.firstName || '-'}</TableCell>
                  <TableCell>{user.lastName || '-'}</TableCell>
                  <TableCell>
                    <Badge variant="outline">{user.role?.name}</Badge>
                  </TableCell>
                  <TableCell>
                    <Badge variant={user.active ? 'default' : 'secondary'}>
                      {user.active ? t('userActive') : t('userInactive')}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-1">
                      <Button variant="ghost" size="sm" onClick={() => openEditUser(user)}>
                        <Pencil className="h-3.5 w-3.5" />
                      </Button>
                      <Button variant="ghost" size="sm" onClick={() => handleDeleteUser(user)}>
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

      {/* Create/Edit User Dialog */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editingUser ? t('editUser') : t('createUser')}</DialogTitle>
            <DialogDescription>
              {editingUser ? t('editUser') : t('createUser')}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="user-email">{t('userEmail')}</Label>
              <Input
                id="user-email"
                value={userForm.email ?? ''}
                onChange={(e) => setUserForm({ ...userForm, email: e.target.value })}
                placeholder="user@example.com"
              />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="user-firstName">{t('userFirstName')}</Label>
                <Input
                  id="user-firstName"
                  value={userForm.firstName ?? ''}
                  onChange={(e) => setUserForm({ ...userForm, firstName: e.target.value })}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="user-lastName">{t('userLastName')}</Label>
                <Input
                  id="user-lastName"
                  value={userForm.lastName ?? ''}
                  onChange={(e) => setUserForm({ ...userForm, lastName: e.target.value })}
                />
              </div>
            </div>
            <div className="space-y-2">
              <Label htmlFor="user-role">{t('userRole')}</Label>
              <Select
                value={userForm.role?.name ?? 'operator'}
                onValueChange={(val) => {
                  const role = ROLES.find((r) => r.name === val) ?? ROLES[0]
                  setUserForm({ ...userForm, role })
                }}
              >
                <SelectTrigger id="user-role">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {ROLES.map((role) => (
                    <SelectItem key={role.id} value={role.name!}>
                      {role.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="flex items-center gap-2">
              <input
                type="checkbox"
                id="user-active"
                checked={userForm.active ?? true}
                onChange={(e) => setUserForm({ ...userForm, active: e.target.checked })}
                className="h-4 w-4 rounded border-gray-300"
              />
              <Label htmlFor="user-active">{t('userActive')}</Label>
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <Button variant="outline" onClick={() => setDialogOpen(false)}>
                {t('cancel', { ns: 'common' })}
              </Button>
              <Button onClick={handleSaveUser} disabled={saving}>
                {saving ? t('saving', { ns: 'common' }) : t('save', { ns: 'common' })}
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}
