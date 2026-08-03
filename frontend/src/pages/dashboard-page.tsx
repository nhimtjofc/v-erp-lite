import { useEffect, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { getUser } from '@/shared/auth'
import { listUsers, useCreateUserMutation, useDeleteUserMutation, useHealthQuery, useListBusinessesQuery, useMeQuery, useMySubscriptionQuery } from '@/shared/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Trash2, UserPlus } from 'lucide-react'
import type { User } from 'oidc-client-ts'
import type { User as TenantUser } from '@/shared/api'

const FEATURE_DETAILS: Record<string, { label: string; benefit: string }> = {
  catalog: { label: 'Danh m\u1ee5c & s\u1ea3n ph\u1ea9m', benefit: 'Chu\u1ea9n h\u00f3a h\u00e0ng h\u00f3a tr\u01b0\u1edbc khi b\u00e1n.' },
  customers: { label: 'Kh\u00e1ch h\u00e0ng', benefit: 'L\u01b0u l\u1ecbch s\u1eed \u0111\u1ec3 ch\u0103m s\u00f3c v\u00e0 b\u00e1n l\u1ea1i.' },
  orders: { label: '\u0110\u01a1n h\u00e0ng', benefit: 'Theo d\u00f5i quy tr\u00ecnh b\u00e1n h\u00e0ng t\u1eadp trung.' },
  inventory: { label: 'T\u1ed3n kho', benefit: 'Ki\u1ec3m so\u00e1t s\u1ed1 l\u01b0\u1ee3ng h\u00e0ng c\u00f3 th\u1ec3 b\u00e1n.' },
  multiBusiness: { label: 'Nhi\u1ec1u \u0111\u01a1n v\u1ecb kinh doanh', benefit: 'Qu\u1ea3n l\u00fd nhi\u1ec1u c\u1eeda h\u00e0ng ho\u1eb7c chi nh\u00e1nh trong c\u00f9ng tenant.' },
  teamCollaboration: { label: 'C\u1ed9ng t\u00e1c \u0111\u1ed9i nh\u00f3m', benefit: 'M\u1edf r\u1ed9ng s\u1ed1 ng\u01b0\u1eddi d\u00f9ng theo vai tr\u00f2 v\u1eadn h\u00e0nh.' },
  vouchers: { label: 'Khuy\u1ebfn m\u1ea1i', benefit: 'T\u1ea1o voucher cho c\u00e1c chi\u1ebfn d\u1ecbch b\u00e1n h\u00e0ng.' },
  reports: { label: 'B\u00e1o c\u00e1o', benefit: 'Theo d\u00f5i doanh thu \u0111\u1ec3 ra quy\u1ebft \u0111\u1ecbnh.' },
  notifications: { label: 'Th\u00f4ng b\u00e1o', benefit: 'Nh\u1eafc vi\u1ec7c v\u00e0 c\u1eadp nh\u1eadt v\u1eadn h\u00e0nh k\u1ecbp th\u1eddi.' },
  ecommerce: { label: 'B\u00e1n h\u00e0ng \u0111a k\u00eanh', benefit: 'K\u1ebft n\u1ed1i v\u00e0 \u0111\u1ed3ng b\u1ed9 \u0111\u01a1n h\u00e0ng online.' },
  onlinePayments: { label: 'Thanh to\u00e1n tr\u1ef1c tuy\u1ebfn', benefit: 'Nh\u1eadn thanh to\u00e1n s\u1ed1 trong lu\u1ed3ng \u0111\u01a1n h\u00e0ng.' },
  walletPayouts: { label: 'V\u00ed & chi tr\u1ea3', benefit: '\u0110\u1ed1i so\u00e1t s\u1ed1 d\u01b0 v\u00e0 x\u1eed l\u00fd chi tr\u1ea3.' },
}

const LIMIT_LABELS: Record<string, string> = {
  maxUsers: 'ng\u01b0\u1eddi d\u00f9ng',
  maxBusinesses: '\u0111\u01a1n v\u1ecb kinh doanh',
  maxProducts: 's\u1ea3n ph\u1ea9m',
  maxChannels: 'k\u00eanh b\u00e1n h\u00e0ng',
}

export function DashboardPage() {
  const { t } = useTranslation()
  const [user, setUser] = useState<User | null>(null)
  const { data: healthRaw } = useHealthQuery()
  const { data: customers } = useListBusinessesQuery()
  const { data: subscription } = useMySubscriptionQuery()
  const { data: me } = useMeQuery()
  const isOwner = me?.role?.name === 'businessOwner'
  const queryClient = useQueryClient()
  const createMemberMutation = useCreateUserMutation()
  const deleteMemberMutation = useDeleteUserMutation()
  const [memberForm, setMemberForm] = useState({ email: '', firstName: '', lastName: '' })
  const [memberError, setMemberError] = useState<string | null>(null)
  const { data: members = [] } = useQuery({
    queryKey: ['team-members'],
    queryFn: async ({ signal }) => {
      const { data } = await listUsers({ signal, throwOnError: true })
      return data ?? []
    },
    enabled: isOwner,
  })

  const addMember = async () => {
    if (!memberForm.email.trim()) return
    setMemberError(null)
    try {
      await createMemberMutation.mutateAsync({
        body: {
          ...memberForm,
          email: memberForm.email.trim(),
          active: true,
          role: { id: 'operator', name: 'operator' },
        } as TenantUser,
      })
      setMemberForm({ email: '', firstName: '', lastName: '' })
      await queryClient.invalidateQueries({ queryKey: ['team-members'] })
    } catch {
      setMemberError('\\u0110\\u00e3 x\\u1ea3y ra l\\u1ed7i khi th\\u00eam th\\u00e0nh vi\\u00ean. Vui l\\u00f2ng th\\u1eed l\\u1ea1i.')
    }
  }

  const removeMember = async (id: string) => {
    if (!confirm('\\u0110\\u00f3ng t\\u00e0i kho\\u1ea3n c\\u1ee7a th\\u00e0nh vi\\u00ean n\\u00e0y?')) return
    setMemberError(null)
    try {
      await deleteMemberMutation.mutateAsync({ path: { id } })
      await queryClient.invalidateQueries({ queryKey: ['team-members'] })
    } catch {
      setMemberError('Kh\\u00f4ng th\\u1ec3 x\\u00f3a th\\u00e0nh vi\\u00ean. Vui l\\u00f2ng th\\u1eed l\\u1ea1i.')
    }
  }
  const health = healthRaw as { status?: string; service?: string } | undefined
  useEffect(() => {
    getUser().then(setUser)
  }, [])

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">{t('dashboard:title')}</h1>
        <p className="text-muted-foreground mt-1">
          {t('dashboard:welcome', { name: user?.profile?.name })}
        </p>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium">{t('dashboard:systemStatus')}</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{health?.status ?? '...'}</div>
            <p className="text-xs text-muted-foreground mt-1">
              {health?.service ?? t('common:loading')}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium">{t('dashboard:customers')}</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{customers ? customers.length : '...'}</div>
            <p className="text-xs text-muted-foreground mt-1">{t('dashboard:customersCount')}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium">{t('dashboard:tenant')}</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold truncate">
              {user?.profile?.azp ?? 'default'}
            </div>
            <p className="text-xs text-muted-foreground mt-1">{t('dashboard:activeTenant')}</p>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between gap-4 pb-2">
          <div>
            <CardTitle className="text-sm font-medium">{'G\u00f3i thu\u00ea bao'}</CardTitle>
              <p className="mt-1 text-xs text-muted-foreground">{'Th\u00eam operator \u0111\u1ec3 c\u00f9ng v\u1eadn h\u00e0nh tenant. S\u1ed1 l\u01b0\u1ee3ng ph\u1ee5 thu\u1ed9c h\u1ea1n m\u1ee9c g\u00f3i.'}</p>
          </div>
          {subscription && (
            <Badge variant={subscription.readOnly ? 'destructive' : 'default'}>{subscription.status}</Badge>
          )}
        </CardHeader>
        <CardContent>
          {subscription ? (
            <div className="space-y-5">
              <div className="grid gap-4 text-sm md:grid-cols-3">
                <div>
                  <p className="text-2xl font-bold">{subscription.planName}</p>
                  <p className="mt-1 text-muted-foreground">{subscription.productSlice}</p>
                </div>
                <div>
                  <p className="text-muted-foreground">{'Chu k\u1ef3 thanh to\u00e1n'}</p>
                  <p className="mt-1 font-medium">{subscription.billingPeriod === 'ANNUAL' ? 'H\u00e0ng n\u0103m' : 'H\u00e0ng th\u00e1ng'}</p>
                </div>
                <div>
                  <p className="text-muted-foreground">{'Hi\u1ec7u l\u1ef1c \u0111\u1ebfn'}</p>
                  <p className="mt-1 font-medium">
                    {subscription.currentPeriodEndsAt
                      ? new Date(subscription.currentPeriodEndsAt).toLocaleDateString('vi-VN')
                      : 'Kh\u00f4ng gi\u1edbi h\u1ea1n'}
                  </p>
                </div>
              </div>

              <div>
                <p className="mb-2 text-sm font-medium">{'T\u00ednh n\u0103ng v\u00e0 tr\u01b0\u1eddng h\u1ee3p s\u1eed d\u1ee5ng'}</p>
                <div className="grid gap-3 md:grid-cols-2">
                  {Object.entries(subscription.features).filter(([, enabled]) => enabled).map(([feature]) => {
                    const detail = FEATURE_DETAILS[feature] ?? { label: feature, benefit: '\u0110\u00e3 \u0111\u01b0\u1ee3c k\u00edch ho\u1ea1t trong g\u00f3i hi\u1ec7n t\u1ea1i.' }
                    return (
                      <div key={feature} className="rounded-md border border-border/60 p-3">
                        <p className="font-medium">{detail.label}</p>
                        <p className="mt-1 text-xs text-muted-foreground">{detail.benefit}</p>
                      </div>
                    )
                  })}
                </div>
              </div>

              <div>
                <p className="mb-2 text-sm font-medium">{'H\u1ea1n m\u1ee9c g\u00f3i'}</p>
                <div className="flex flex-wrap gap-2">
                  {Object.entries(subscription.limits).map(([limit, value]) => (
                    <Badge key={limit} variant="outline">
                      {value.toLocaleString('vi-VN')} {LIMIT_LABELS[limit] ?? limit}
                    </Badge>
                  ))}
                </div>
              </div>
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">{'\u0110ang t\u1ea3i th\u00f4ng tin g\u00f3i thu\u00ea bao...'}</p>
          )}
        </CardContent>
      </Card>
      {isOwner && subscription?.features.teamCollaboration && (
        <Card>
          <CardHeader className="flex flex-row items-center justify-between gap-4 pb-2">
            <div>
              <CardTitle className="text-sm font-medium">{'Th\u00e0nh vi\u00ean \u0111\u1ed9i nh\u00f3m'}</CardTitle>
              <p className="mt-1 text-xs text-muted-foreground">{'Th\u00eam operator \u0111\u1ec3 c\u00f9ng v\u1eadn h\u00e0nh tenant. S\u1ed1 l\u01b0\u1ee3ng ph\u1ee5 thu\u1ed9c h\u1ea1n m\u1ee9c g\u00f3i.'}</p>
            </div>
            <Badge variant="secondary">operator</Badge>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid gap-3 md:grid-cols-4">
              <Input
                value={memberForm.email}
                onChange={(event) => setMemberForm((current) => ({ ...current, email: event.target.value }))}
                placeholder="email@company.com"
                type="email"
              />
              <Input
                value={memberForm.firstName}
                onChange={(event) => setMemberForm((current) => ({ ...current, firstName: event.target.value }))}
                placeholder={'T\u00ean'}
              />
              <Input
                value={memberForm.lastName}
                onChange={(event) => setMemberForm((current) => ({ ...current, lastName: event.target.value }))}
                placeholder={'H\u1ecd'}
              />
              <Button onClick={addMember} disabled={!memberForm.email.trim() || createMemberMutation.isPending}>
                <UserPlus className="h-4 w-4" />
                {createMemberMutation.isPending ? '\\u0110ang th\\u00eam...' : 'Th\\u00eam th\\u00e0nh vi\\u00ean'}
              </Button>
            </div>
            {memberError && <p className="text-sm text-destructive">{memberError}</p>}
            <div className="divide-y rounded-md border">
              {members.filter((member) => member.id !== me?.id).map((member) => (
                <div key={member.id} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
                  <div>
                    <p className="font-medium">{member.firstName} {member.lastName}</p>
                    <p className="text-xs text-muted-foreground">{member.email}</p>
                  </div>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => removeMember(member.id!)}
                    disabled={deleteMemberMutation.isPending || member.role?.name !== 'operator'}
                    title={member.role?.name === 'operator' ? 'X\u00f3a operator' : 'Ch\u1ec9 x\u00f3a operator'}
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>
              ))}
              {members.filter((member) => member.id !== me?.id).length === 0 && (
                <p className="px-3 py-4 text-sm text-muted-foreground">{'Ch\u01b0a c\u00f3 th\u00e0nh vi\u00ean n\u00e0o trong \u0111\u1ed9i nh\u00f3m.'}</p>
              )}
            </div>
          </CardContent>
        </Card>
      )}    </div>
  )
}
