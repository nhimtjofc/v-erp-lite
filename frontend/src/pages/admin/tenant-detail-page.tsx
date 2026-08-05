import { useEffect, useState } from 'react'
import { useParams, useRouter } from '@tanstack/react-router'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  listTenantsQueryKey,
  SUBSCRIPTION_FINANCIAL_STATS_QUERY_KEY,
  subscriptionQueryKey,
  useAdminTenantSubscriptionQuery,
  useChangeTenantSubscriptionMutation,
  useMockSubscriptionTimeMutation,
  useDeleteTenantMutation,
  useGetTenantQuery,
  useSubscriptionPlansQuery,
} from '@/shared/api'
import {
  listTenantUsers,
  useCreateTenantUserMutation,
  useUpdateTenantUserMutation,
  useDeleteTenantUserMutation,
} from '@/shared/api'
import type { SubscriptionMockScenario, User, Role } from '@/shared/api'
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
import { ArrowLeft, Check, CircleDollarSign, FastForward, Pencil, Plus, RotateCcw, TimerReset, Trash2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'

// Inline role listing is safe here because roles are stable seeds (admin, operator, businessOwner).
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
  const { data: tenant, isLoading: tenantLoading } = useGetTenantQuery({ path: { id } })
  const deleteTenantMutation = useDeleteTenantMutation()
  const queryClient = useQueryClient()

  // Users in this tenant are fetched only when the tenant code is available.
  const tenantCode = tenant?.code
  const { data: subscription, isLoading: subscriptionLoading } = useAdminTenantSubscriptionQuery(tenantCode)
  const { data: plans = [] } = useSubscriptionPlansQuery()
  const changeSubscriptionMutation = useChangeTenantSubscriptionMutation()
  const mockSubscriptionTimeMutation = useMockSubscriptionTimeMutation()
  const [showcaseMessage, setShowcaseMessage] = useState('')
  const [subscriptionForm, setSubscriptionForm] = useState({
    planCode: 'BASE',
    billingPeriod: 'MONTHLY' as 'MONTHLY' | 'ANNUAL',
    status: 'ACTIVE' as 'TRIAL' | 'ACTIVE' | 'PAST_DUE' | 'SUSPENDED' | 'CANCELLED',
  })

  useEffect(() => {
    if (subscription) {
      setSubscriptionForm({
        planCode: subscription.planCode,
        billingPeriod: subscription.billingPeriod as 'MONTHLY' | 'ANNUAL',
        status: subscription.status as 'TRIAL' | 'ACTIVE' | 'PAST_DUE' | 'SUSPENDED' | 'CANCELLED',
      })
    }
  }, [subscription])

  const selectedPlan = plans.find((plan) => plan.code === subscriptionForm.planCode)

  const formatMoney = (amount: number, currency = 'VND') => new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency,
    maximumFractionDigits: 0,
  }).format(amount)

  const handleSaveSubscription = async () => {
    if (!tenantCode) return
    await changeSubscriptionMutation.mutateAsync({ tenantCode, ...subscriptionForm })
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: subscriptionQueryKey(tenantCode) }),
      queryClient.invalidateQueries({ queryKey: SUBSCRIPTION_FINANCIAL_STATS_QUERY_KEY }),
    ])
  }

  const runSubscriptionShowcase = async (scenario: SubscriptionMockScenario) => {
    if (!tenantCode) return
    if (scenario === 'EXPIRE_NOW' && !window.confirm('Cho subscription này hết hạn ngay để demo trạng thái khóa?')) return
    setShowcaseMessage('')
    try {
      await mockSubscriptionTimeMutation.mutateAsync({ tenantCode, scenario })
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: subscriptionQueryKey(tenantCode) }),
        queryClient.invalidateQueries({ queryKey: SUBSCRIPTION_FINANCIAL_STATS_QUERY_KEY }),
      ])
      setShowcaseMessage(scenario === 'EXPIRE_NOW'
        ? 'Subscription đã hết hạn để showcase.'
        : 'Đã khôi phục kỳ subscription chuẩn.')
    } catch (cause) {
      setShowcaseMessage(cause instanceof Error ? cause.message : 'Không thể cập nhật thời gian showcase.')
    }
  }

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
      // Send role as {name: "..."} because the backend's resolveRole() expects that shape.
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
      queryClient.invalidateQueries({ queryKey: listTenantsQueryKey() })
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

      {/* Subscription section */}
      <section className="rounded-lg border p-5">
        <div className="mb-5 flex items-start justify-between gap-4">
          <div>
            <div className="flex items-center gap-2">
              <CircleDollarSign className="h-5 w-5 text-muted-foreground" />
              <h2 className="text-lg font-semibold">{'G\u00f3i thu\u00ea bao'}</h2>
            </div>
            <p className="mt-1 text-sm text-muted-foreground">{'Qu\u1ea3n l\u00fd l\u00e1t s\u1ea3n ph\u1ea9m v\u00e0 h\u1ea1n m\u1ee9c v\u1eadn h\u00e0nh c\u1ee7a tenant.'}</p>
          </div>
          {subscription && (
            <Badge variant={subscription.readOnly ? 'destructive' : 'default'}>{subscription.status}</Badge>
          )}
        </div>

        {subscriptionLoading ? (
          <p className="py-4 text-sm text-muted-foreground">{'\u0110ang t\u1ea3i thu\u00ea bao...'}</p>
        ) : (
          <div className="space-y-5">
            <div className="grid gap-4 md:grid-cols-3">
              <div className="space-y-2">
                <Label htmlFor="subscription-plan">{'G\u00f3i'}</Label>
                <Select
                  value={subscriptionForm.planCode}
                  onValueChange={(planCode) => { if (planCode) setSubscriptionForm((current) => ({ ...current, planCode })) }}
                >
                  <SelectTrigger id="subscription-plan"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {plans.map((plan) => (
                      <SelectItem key={plan.code} value={plan.code}>{plan.name}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="subscription-period">{'Chu k\u1ef3'}</Label>
                <Select
                  value={subscriptionForm.billingPeriod}
                  onValueChange={(billingPeriod) => setSubscriptionForm((current) => ({
                    ...current,
                    billingPeriod: billingPeriod as 'MONTHLY' | 'ANNUAL',
                  }))}
                >
                  <SelectTrigger id="subscription-period"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="MONTHLY">{'H\u00e0ng th\u00e1ng'}</SelectItem>
                    <SelectItem value="ANNUAL">{'H\u00e0ng n\u0103m'}</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="subscription-status">{'Tr\u1ea1ng th\u00e1i'}</Label>
                <Select
                  value={subscriptionForm.status}
                  onValueChange={(status) => setSubscriptionForm((current) => ({
                    ...current,
                    status: status as 'TRIAL' | 'ACTIVE' | 'PAST_DUE' | 'SUSPENDED' | 'CANCELLED',
                  }))}
                >
                  <SelectTrigger id="subscription-status"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="TRIAL">{'D\u00f9ng th\u1eed'}</SelectItem>
                    <SelectItem value="ACTIVE">{'\u0110ang ho\u1ea1t \u0111\u1ed9ng'}</SelectItem>
                    <SelectItem value="PAST_DUE">{'Qu\u00e1 h\u1ea1n thanh to\u00e1n'}</SelectItem>
                    <SelectItem value="SUSPENDED">{'T\u1ea1m ng\u01b0ng'}</SelectItem>
                    <SelectItem value="EXPIRED" disabled>Hết hạn</SelectItem>
                    <SelectItem value="CANCELLED">{'\u0110\u00e3 h\u1ee7y'}</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>

            {selectedPlan && (
              <div className="rounded-md bg-muted/50 p-4 text-sm">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p><span className="text-muted-foreground">{'L\u00e1t s\u1ea3n ph\u1ea9m:'}</span> <span className="font-medium">{selectedPlan.productSlice}</span></p>
                  <p className="font-semibold">
                    {formatMoney(subscriptionForm.billingPeriod === 'ANNUAL' ? selectedPlan.annualPriceMinor : selectedPlan.monthlyPriceMinor, selectedPlan.currency)}
                    <span className="ml-1 text-xs font-normal text-muted-foreground">/{subscriptionForm.billingPeriod === 'ANNUAL' ? 'n\u0103m' : 'th\u00e1ng'}</span>
                  </p>
                </div>
                <div className="mt-3 flex flex-wrap gap-2">
                  {Object.entries(selectedPlan.limits).map(([key, value]) => (
                    <Badge key={key} variant="outline">{key}: {value.toLocaleString('vi-VN')}</Badge>
                  ))}
                </div>
                <div className="mt-3 flex flex-wrap gap-2">
                  {Object.entries(selectedPlan.features).filter(([, enabled]) => enabled).map(([feature]) => (
                    <Badge key={feature} variant="secondary"><Check className="h-3 w-3" />{feature}</Badge>
                  ))}
                </div>
              </div>
            )}

            {subscription?.currentPeriodEndsAt && (
              <p className="text-sm text-muted-foreground">{'K\u1ef3 hi\u1ec7n t\u1ea1i k\u1ebft th\u00fac:'} {new Date(subscription.currentPeriodEndsAt).toLocaleString('vi-VN')}</p>
            )}

            <div className="rounded-lg border border-amber-500/30 bg-amber-500/5 p-4">
              <div className="flex items-start gap-3">
                <TimerReset className="mt-0.5 h-5 w-5 text-amber-600 dark:text-amber-400" />
                <div>
                  <p className="font-semibold">Công cụ showcase subscription (Sandbox)</p>
                  <p className="mt-1 text-sm text-muted-foreground">Rút ngắn kỳ hạn để demo trạng thái sắp hết hạn hoặc hết hạn. Chỉ admin có thể sử dụng.</p>
                </div>
              </div>
              <div className="mt-4 flex flex-wrap gap-2">

                <Button type="button" size="sm" variant="destructive" onClick={() => runSubscriptionShowcase('EXPIRE_NOW')} disabled={mockSubscriptionTimeMutation.isPending}>
                  <FastForward className="h-4 w-4" />Hết hạn ngay
                </Button>
                <Button type="button" size="sm" variant="outline" onClick={() => runSubscriptionShowcase('RESET_PERIOD')} disabled={mockSubscriptionTimeMutation.isPending}>
                  <RotateCcw className="h-4 w-4" />Khôi phục kỳ chuẩn
                </Button>
              </div>
              {showcaseMessage && <p className="mt-3 text-sm text-muted-foreground">{showcaseMessage}</p>}
            </div>
            {changeSubscriptionMutation.isError && (
              <p className="text-sm text-destructive">{'Kh\u00f4ng th\u1ec3 c\u1eadp nh\u1eadt g\u00f3i. Vui l\u00f2ng th\u1eed l\u1ea1i.'}</p>
            )}
            <div className="flex justify-end">
              <Button onClick={handleSaveSubscription} disabled={!tenantCode || changeSubscriptionMutation.isPending}>
                {changeSubscriptionMutation.isPending ? '\u0110ang c\u1eadp nh\u1eadt...' : 'C\u1eadp nh\u1eadt g\u00f3i'}
              </Button>
            </div>
          </div>
        )}
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
