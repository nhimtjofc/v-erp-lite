import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import { useMeQuery } from '@/shared/api'
import {
  useAdminPayoutsQuery,
  useApprovePayoutMutation,
  useRejectPayoutMutation,
  type PayoutRequest,
} from '@/shared/api/payout'
import { formatMoneyMinor } from '@/shared/utils/currency'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/ui/dialog'
import { Label } from '@/shared/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'
import { AlertTriangle, Banknote, Check, X } from 'lucide-react'

type PayoutAction = 'approve' | 'reject'

function ReviewDialog({
  payout,
  action,
  onClose,
}: {
  payout: PayoutRequest
  action: PayoutAction
  onClose: () => void
}) {
  const queryClient = useQueryClient()
  const approve = useApprovePayoutMutation()
  const reject = useRejectPayoutMutation()
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')
  const isReject = action === 'reject'
  const mutation = isReject ? reject : approve

  const submit = async () => {
    if (isReject && !reason.trim()) {
      setError('A rejection reason is required.')
      return
    }
    try {
      await mutation.mutateAsync({ id: payout.id!, reason: reason.trim() || undefined })
      await queryClient.invalidateQueries({ queryKey: ['admin-payouts'] })
      await queryClient.invalidateQueries({ queryKey: ['seller-payouts'] })
      await queryClient.invalidateQueries({ queryKey: ['seller-wallet'] })
      onClose()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to review payout.')
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{isReject ? 'Reject flagged payout' : 'Approve flagged payout'}</DialogTitle>
          <DialogDescription>
            {formatMoneyMinor(Number(payout.amountMinor ?? 0), payout.currency ?? 'VND')} to {payout.bankAccountHolder}.
            Approval calls the bank transfer adapter immediately.
          </DialogDescription>
        </DialogHeader>
        <div className="rounded-md border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
          <strong>Flag reason:</strong> {payout.flagReason || 'No reason recorded'}
        </div>
        <div className="space-y-2">
          <Label htmlFor="review-reason">{isReject ? 'Rejection reason' : 'Review note (optional)'}</Label>
          <textarea
            id="review-reason"
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            rows={3}
            className="flex w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm"
          />
          {error && <p className="text-sm text-destructive">{error}</p>}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>Cancel</Button>
          <Button variant={isReject ? 'destructive' : 'default'} onClick={submit} disabled={mutation.isPending}>
            {mutation.isPending ? 'Processing…' : isReject ? 'Reject payout' : 'Approve and pay'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

export function AdminPayoutsPage() {
  const { t } = useTranslation()
  const { data: me, isLoading: isMeLoading } = useMeQuery()
  const { data: payouts, isLoading, isError, error } = useAdminPayoutsQuery()
  const [activeReview, setActiveReview] = useState<{ payout: PayoutRequest; action: PayoutAction } | null>(null)

  if (isMeLoading) return <div className="py-8 text-center text-muted-foreground">Loading access control…</div>
  if (me?.role?.name !== 'admin') return <div className="py-8 text-center text-destructive">Only administrators can review payout exceptions.</div>

  const list = payouts ?? []
  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <Banknote className="h-7 w-7 text-primary" />
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('nav:payouts', 'Payouts')}</h1>
          <p className="mt-1 text-muted-foreground">Exception queue — normal payouts are auto-approved and paid without admin involvement.</p>
        </div>
      </div>
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><AlertTriangle className="h-5 w-5 text-destructive" /> Flagged payouts ({list.length})</CardTitle>
        </CardHeader>
        <CardContent>
          {isError ? <p className="text-destructive">{error instanceof Error ? error.message : 'Unable to load payouts.'}</p>
            : isLoading ? <p className="text-muted-foreground">Loading…</p>
              : list.length === 0 ? <p className="py-8 text-center text-muted-foreground">No payouts require manual review.</p>
                : (
                  <Table>
                    <TableHeader><TableRow><TableHead>Created</TableHead><TableHead>Bank destination</TableHead><TableHead>Why it was flagged</TableHead><TableHead className="text-right">Amount</TableHead><TableHead className="text-right">Actions</TableHead></TableRow></TableHeader>
                    <TableBody>
                      {list.map((payout) => (
                        <TableRow key={payout.id}>
                          <TableCell>{payout.createdAt ? new Date(payout.createdAt).toLocaleString() : '—'}</TableCell>
                          <TableCell>
                            <p className="font-medium">{payout.bankAccountHolder || 'Bank details missing'}</p>
                            <p className="text-xs text-muted-foreground">{payout.bankName} · {payout.bankAccountNumber}</p>
                          </TableCell>
                          <TableCell>
                            <Badge variant="destructive" className="mb-1">FLAGGED</Badge>
                            <p className="max-w-md font-medium text-destructive">{payout.flagReason}</p>
                          </TableCell>
                          <TableCell className="text-right font-semibold">{formatMoneyMinor(Number(payout.amountMinor ?? 0), payout.currency ?? 'VND')}</TableCell>
                          <TableCell>
                            <div className="flex justify-end gap-2">
                              <Button size="sm" variant="outline" onClick={() => setActiveReview({ payout, action: 'approve' })}><Check className="mr-1 h-4 w-4" /> Approve</Button>
                              <Button size="sm" variant="destructive" onClick={() => setActiveReview({ payout, action: 'reject' })}><X className="mr-1 h-4 w-4" /> Reject</Button>
                            </div>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
        </CardContent>
      </Card>
      {activeReview && <ReviewDialog {...activeReview} onClose={() => setActiveReview(null)} />}
    </div>
  )
}
