import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import { useMeQuery } from '@/shared/api'
import {
  useAdminPayoutsQuery,
  useApprovePayoutMutation,
  useRejectPayoutMutation,
  payoutDataSource,
  PAYOUT_STATUS_META,
  type PayoutRequest,
} from '@/shared/api/payout'
import { formatMoneyMinor } from '@/shared/utils/currency'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Label } from '@/shared/ui/label'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/shared/ui/dialog'
import { Banknote, Check, X } from 'lucide-react'

/** Status filters offered to the admin. `undefined` = all statuses. */
const STATUS_FILTERS: { value: string | undefined; label: string }[] = [
  { value: 'PENDING', label: 'Chờ duyệt' },
  { value: 'APPROVED', label: 'Đã duyệt' },
  { value: 'REJECTED', label: 'Từ chối' },
  { value: undefined, label: 'Tất cả' },
]

function formatDate(value?: string): string {
  if (!value) return '—'
  return new Date(value).toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

type PayoutAction = 'approve' | 'reject'

/**
 * Approve/reject confirmation dialog. A reason is required to reject (the admin
 * must justify a denial) and optional as a note when approving.
 */
function PayoutActionDialog({
  action,
  payout,
  onClose,
}: {
  action: PayoutAction
  payout: PayoutRequest
  onClose: () => void
}) {
  const queryClient = useQueryClient()
  const approve = useApprovePayoutMutation()
  const reject = useRejectPayoutMutation()
  const mutation = action === 'approve' ? approve : reject

  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)

  const isReject = action === 'reject'
  const canSubmit = (!isReject || reason.trim() !== '') && !mutation.isPending

  const handleSubmit = async () => {
    setError(null)
    if (isReject && reason.trim() === '') {
      setError('Vui lòng nhập lý do từ chối.')
      return
    }
    try {
      await mutation.mutateAsync({ id: payout.id, reason: reason.trim() || undefined })
      // Refresh the admin queue and the seller-facing views so both reflect the
      // new status (and any balance change from an approval).
      queryClient.invalidateQueries({ queryKey: ['admin-payouts'] })
      queryClient.invalidateQueries({ queryKey: ['seller-payouts'] })
      queryClient.invalidateQueries({ queryKey: ['seller-wallet'] })
      queryClient.invalidateQueries({ queryKey: ['wallet-transactions'] })
      onClose()
    } catch (err) {
      setError((err as Error)?.message || 'Xử lý yêu cầu thất bại. Vui lòng thử lại.')
    }
  }

  return (
    <Dialog open onOpenChange={(next) => !next && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{isReject ? 'Từ chối yêu cầu rút tiền' : 'Duyệt yêu cầu rút tiền'}</DialogTitle>
          <DialogDescription>
            {payout.bankAccountHolder} · {payout.bankName} · {payout.bankAccountNumber} —{' '}
            <span className="font-semibold text-foreground">
              {formatMoneyMinor(payout.amountMinor, payout.currency)}
            </span>
            {isReject
              ? '. Số tiền sẽ được hoàn lại vào số dư khả dụng của người bán.'
              : '. Số tiền sẽ được trừ khỏi số dư khả dụng của người bán.'}
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="payout-reason">
              {isReject ? 'Lý do từ chối' : 'Ghi chú (tùy chọn)'}
            </Label>
            <textarea
              id="payout-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              rows={3}
              placeholder={isReject ? 'Nhập lý do từ chối yêu cầu' : 'Ghi chú cho lần duyệt này'}
              className="flex w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
            />
          </div>
          {error && <p className="text-sm text-destructive">{error}</p>}
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={onClose}>
              Hủy
            </Button>
            <Button
              variant={isReject ? 'destructive' : 'default'}
              onClick={handleSubmit}
              disabled={!canSubmit}
            >
              {mutation.isPending
                ? 'Đang xử lý...'
                : isReject
                  ? 'Xác nhận từ chối'
                  : 'Xác nhận duyệt'}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}

/**
 * Admin payout approval dashboard.
 *
 * Day 5 (Dev 5): list seller payout requests (defaulting to PENDING) and let the
 * admin approve or reject each pending request with a reason.
 */
export function AdminPayoutsPage() {
  const { t } = useTranslation()
  const { data: me, isLoading: isMeLoading } = useMeQuery()
  const roleName = me?.role?.name
  const isAdmin = roleName === 'admin'

  const [statusFilter, setStatusFilter] = useState<string | undefined>('PENDING')
  const [activeAction, setActiveAction] = useState<
    { action: PayoutAction; payout: PayoutRequest } | null
  >(null)

  const {
    data: payouts,
    isLoading,
    isError,
    error,
  } = useAdminPayoutsQuery(statusFilter)

  if (isMeLoading) {
    return <div className="text-center py-8 text-muted-foreground">Loading access control...</div>
  }

  if (!isAdmin) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[300px] border border-dashed rounded-lg p-8 text-center space-y-4">
        <h2 className="text-xl font-bold text-destructive">Access Denied</h2>
        <p className="text-muted-foreground max-w-md">
          Only administrators can review payout requests.
        </p>
      </div>
    )
  }

  const list = payouts ?? []

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <Banknote className="h-7 w-7 text-primary" />
          <div>
            <h1 className="text-3xl font-bold tracking-tight">{t('nav:payouts', 'Payouts')}</h1>
            <p className="text-muted-foreground mt-1">
              Duyệt yêu cầu rút tiền của người bán. Review, approve or reject seller payouts.
            </p>
          </div>
        </div>
        <Badge variant={payoutDataSource === 'api' ? 'default' : 'secondary'}>
          {payoutDataSource === 'api' ? 'Live API' : 'Mock data'}
        </Badge>
      </div>

      {/* Status filter */}
      <div className="flex flex-wrap gap-2">
        {STATUS_FILTERS.map((filter) => (
          <Button
            key={filter.label}
            size="sm"
            variant={statusFilter === filter.value ? 'default' : 'outline'}
            onClick={() => setStatusFilter(filter.value)}
          >
            {filter.label}
          </Button>
        ))}
      </div>

      <Card>
        <CardHeader>
          <CardTitle>
            {statusFilter === 'PENDING' ? 'Yêu cầu chờ duyệt' : 'Yêu cầu rút tiền'}
            {!isLoading && !isError && (
              <span className="ml-2 text-sm font-normal text-muted-foreground">
                ({list.length})
              </span>
            )}
          </CardTitle>
        </CardHeader>
        <CardContent>
          {isError ? (
            <div className="py-8 text-center">
              <p className="font-semibold text-destructive">Không tải được danh sách rút tiền.</p>
              <p className="mt-1 text-sm text-muted-foreground">
                {(error as Error)?.message || 'Vui lòng thử lại sau.'}
              </p>
            </div>
          ) : isLoading ? (
            <p className="py-8 text-center text-sm text-muted-foreground">
              {t('common:loading', 'Loading...')}
            </p>
          ) : list.length === 0 ? (
            <p className="py-8 text-center text-sm text-muted-foreground">
              Không có yêu cầu rút tiền nào.
            </p>
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Thời gian</TableHead>
                    <TableHead>Chủ tài khoản</TableHead>
                    <TableHead>Ngân hàng</TableHead>
                    <TableHead>Số tài khoản</TableHead>
                    <TableHead>Trạng thái</TableHead>
                    <TableHead className="text-right">Số tiền</TableHead>
                    <TableHead className="text-right">Thao tác</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {list.map((payout: PayoutRequest) => {
                    const status = PAYOUT_STATUS_META[payout.status] ?? {
                      label: payout.status,
                      variant: 'secondary' as const,
                    }
                    return (
                      <TableRow key={payout.id}>
                        <TableCell className="whitespace-nowrap text-sm text-muted-foreground">
                          {formatDate(payout.createdAt)}
                        </TableCell>
                        <TableCell className="font-medium">{payout.bankAccountHolder}</TableCell>
                        <TableCell>{payout.bankName}</TableCell>
                        <TableCell className="text-sm text-muted-foreground">
                          {payout.bankAccountNumber}
                        </TableCell>
                        <TableCell>
                          <Badge variant={status.variant}>{status.label}</Badge>
                          {payout.reason && (
                            <p className="mt-1 max-w-[200px] text-xs text-muted-foreground">
                              Lý do: {payout.reason}
                            </p>
                          )}
                        </TableCell>
                        <TableCell className="whitespace-nowrap text-right font-semibold">
                          {formatMoneyMinor(payout.amountMinor, payout.currency)}
                        </TableCell>
                        <TableCell className="text-right">
                          {payout.status === 'PENDING' ? (
                            <div className="flex justify-end gap-2">
                              <Button
                                size="sm"
                                variant="outline"
                                onClick={() => setActiveAction({ action: 'approve', payout })}
                              >
                                <Check className="mr-1 h-4 w-4" />
                                Duyệt
                              </Button>
                              <Button
                                size="sm"
                                variant="destructive"
                                onClick={() => setActiveAction({ action: 'reject', payout })}
                              >
                                <X className="mr-1 h-4 w-4" />
                                Từ chối
                              </Button>
                            </div>
                          ) : (
                            <span className="text-sm text-muted-foreground">—</span>
                          )}
                        </TableCell>
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>

      {activeAction && (
        <PayoutActionDialog
          action={activeAction.action}
          payout={activeAction.payout}
          onClose={() => setActiveAction(null)}
        />
      )}
    </div>
  )
}
