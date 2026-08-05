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
  catalog: { label: 'Danh mục & sản phẩm', benefit: 'Chuẩn hóa hàng hóa trước khi bán.' },
  customers: { label: 'Khách hàng', benefit: 'Lưu lịch sử để chăm sóc và bán lại.' },
  orders: { label: 'Đơn hàng', benefit: 'Theo dõi quy trình bán hàng tập trung.' },
  inventory: { label: 'Tồn kho', benefit: 'Kiểm soát số lượng hàng có thể bán.' },
  multiBusiness: { label: 'Nhiều đơn vị kinh doanh', benefit: 'Quản lý nhiều cửa hàng hoặc chi nhánh trong cùng tenant.' },
  teamCollaboration: { label: 'Cộng tác đội nhóm', benefit: 'Mở rộng số người dùng theo vai trò vận hành.' },
  vouchers: { label: 'Khuyến mại', benefit: 'Tạo voucher cho các chiến dịch bán hàng.' },
  reports: { label: 'Báo cáo', benefit: 'Theo dõi doanh thu để ra quyết định.' },
  notifications: { label: 'Thông báo', benefit: 'Nhắc việc và cập nhật vận hành kịp thời.' },
  ecommerce: { label: 'Bán hàng đa kênh', benefit: 'Kết nối và đồng bộ đơn hàng online.' },
  onlinePayments: { label: 'Thanh toán trực tuyến', benefit: 'Nhận thanh toán số trong luồng đơn hàng.' },
  walletPayouts: { label: 'Ví & chi trả', benefit: 'Đối soát số dư và xử lý chi trả.' },
}

const LIMIT_LABELS: Record<string, string> = {
  maxUsers: 'người dùng',
  maxBusinesses: 'đơn vị kinh doanh',
  maxProducts: 'sản phẩm',
  maxChannels: 'kênh bán hàng',
}

const SUBSCRIPTION_STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Đang hoạt động',
  TRIAL: 'Dùng thử',
  PAST_DUE: 'Quá hạn thanh toán',
  SUSPENDED: 'Tạm ngưng',
  EXPIRED: 'Hết hạn',
  CANCELLED: 'Đã hủy',
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
      setMemberError('Đã xảy ra lỗi khi thêm thành viên. Vui lòng thử lại.')
    }
  }

  const removeMember = async (id: string) => {
    if (!confirm('Đóng tài khoản của thành viên này?')) return
    setMemberError(null)
    try {
      await deleteMemberMutation.mutateAsync({ path: { id } })
      await queryClient.invalidateQueries({ queryKey: ['team-members'] })
    } catch {
      setMemberError('Không thể xóa thành viên. Vui lòng thử lại.')
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
            <CardTitle className="text-sm font-medium">{'Gói thuê bao'}</CardTitle>
              <p className="mt-1 text-xs text-muted-foreground">{'Thêm operator để cùng vận hành tenant. Số lượng phụ thuộc hạn mức gói.'}</p>
          </div>
          {subscription && (
            <Badge variant={subscription.readOnly ? 'destructive' : 'default'}>{SUBSCRIPTION_STATUS_LABELS[subscription.status] ?? subscription.status}</Badge>
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
                  <p className="text-muted-foreground">{'Chu kỳ thanh toán'}</p>
                  <p className="mt-1 font-medium">{subscription.billingPeriod === 'ANNUAL' ? 'Hàng năm' : 'Hàng tháng'}</p>
                </div>
                <div>
                  <p className="text-muted-foreground">{'Hiệu lực đến'}</p>
                  <p className="mt-1 font-medium">
                    {subscription.currentPeriodEndsAt
                      ? new Date(subscription.currentPeriodEndsAt).toLocaleDateString('vi-VN')
                      : 'Không giới hạn'}
                  </p>
                </div>
              </div>

              <div>
                <p className="mb-2 text-sm font-medium">{'Tính năng và trường hợp sử dụng'}</p>
                <div className="grid gap-3 md:grid-cols-2">
                  {Object.entries(subscription.features).filter(([, enabled]) => enabled).map(([feature]) => {
                    const detail = FEATURE_DETAILS[feature] ?? { label: feature, benefit: 'Đã được kích hoạt trong gói hiện tại.' }
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
                <p className="mb-2 text-sm font-medium">{'Hạn mức gói'}</p>
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
            <p className="text-sm text-muted-foreground">{'Đang tải thông tin gói thuê bao...'}</p>
          )}
        </CardContent>
      </Card>
      {isOwner && subscription?.features.teamCollaboration && (
        <Card>
          <CardHeader className="flex flex-row items-center justify-between gap-4 pb-2">
            <div>
              <CardTitle className="text-sm font-medium">{'Thành viên đội nhóm'}</CardTitle>
              <p className="mt-1 text-xs text-muted-foreground">{'Thêm operator để cùng vận hành tenant. Số lượng phụ thuộc hạn mức gói.'}</p>
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
                placeholder={'Tên'}
              />
              <Input
                value={memberForm.lastName}
                onChange={(event) => setMemberForm((current) => ({ ...current, lastName: event.target.value }))}
                placeholder={'Họ'}
              />
              <Button onClick={addMember} disabled={!memberForm.email.trim() || createMemberMutation.isPending}>
                <UserPlus className="h-4 w-4" />
                {createMemberMutation.isPending ? 'Đang thêm...' : 'Thêm thành viên'}
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
                    title={member.role?.name === 'operator' ? 'Xóa operator' : 'Chỉ xóa operator'}
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>
              ))}
              {members.filter((member) => member.id !== me?.id).length === 0 && (
                <p className="px-3 py-4 text-sm text-muted-foreground">{'Chưa có thành viên nào trong đội nhóm.'}</p>
              )}
            </div>
          </CardContent>
        </Card>
      )}    </div>
  )
}
