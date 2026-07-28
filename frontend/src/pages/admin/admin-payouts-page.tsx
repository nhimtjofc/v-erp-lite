import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useMeQuery } from '@/shared/api'
import { batchApprove, batchReject, export_, listRiskEvents } from '@/shared/api/gen/sdk.gen'
import type { VendorRiskEvent } from '@/shared/api/gen/types.gen'
import {
  useAdminPayoutsQuery,
  useApprovePayoutMutation,
  useRejectPayoutMutation,
  type PayoutRequest,
} from '@/shared/api/payout'
import { MY_WALLET_QUERY_KEY } from '@/shared/api/wallet'
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
import { AlertTriangle, Banknote, Check, Download, ShieldAlert, X } from 'lucide-react'

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
      await queryClient.invalidateQueries({ queryKey: MY_WALLET_QUERY_KEY })
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
        {isReject && (
          <div className="space-y-2">
            <Label htmlFor="review-reason">Rejection reason</Label>
            <textarea
              id="review-reason"
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              rows={3}
              className="flex w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm"
            />
          </div>
        )}
        {error && <p className="text-sm text-destructive">{error}</p>}
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
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const queryClient = useQueryClient()
  const { data: riskEvents, isLoading: isRiskLoading, isError: isRiskError } = useQuery({
    queryKey: ['payout-risk-events', 'unresolved'],
    queryFn: async () => {
      const response = await listRiskEvents({ query: { resolved: false }, throwOnError: true })
      return response.data ?? []
    },
    enabled: me?.role?.name === 'admin',
  })

  const batchApproveMutation = useMutation({
    mutationFn: async (ids: string[]) => {
      const response = await batchApprove({ body: { ids }, throwOnError: true })
      return response.data
    },
    onSuccess: async () => {
      setSelectedIds(new Set())
      await queryClient.invalidateQueries({ queryKey: ['admin-payouts'] })
      await queryClient.invalidateQueries({ queryKey: ['seller-payouts'] })
      await queryClient.invalidateQueries({ queryKey: MY_WALLET_QUERY_KEY })
    },
  })

  const batchRejectMutation = useMutation({
    mutationFn: async (ids: string[]) => {
      const response = await batchReject({ body: { ids }, throwOnError: true })
      return response.data
    },
    onSuccess: async () => {
      setSelectedIds(new Set())
      await queryClient.invalidateQueries({ queryKey: ['admin-payouts'] })
      await queryClient.invalidateQueries({ queryKey: ['seller-payouts'] })
      await queryClient.invalidateQueries({ queryKey: MY_WALLET_QUERY_KEY })
    },
  })

  const exportMutation = useMutation({
    mutationFn: async () => {
      const response = await export_({ throwOnError: true })
      return response.data
    },
    onSuccess: (csvContent) => {
      if (!csvContent) return
      const blob = new Blob([csvContent as unknown as string], { type: 'text/csv;charset=utf-8;' })
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `payouts-export-${new Date().toISOString().slice(0, 10)}.csv`
      link.click()
      URL.revokeObjectURL(url)
    },
  })

  if (isMeLoading) return <div className="py-8 text-center text-muted-foreground">Loading access control…</div>
  if (me?.role?.name !== 'admin') return <div className="py-8 text-center text-destructive">Only administrators can review payout exceptions.</div>

  const list = payouts ?? []
  const totalAmount = list.reduce((sum, p) => sum + Number(p.amountMinor ?? 0), 0)
  const selectedArray = Array.from(selectedIds)
  const allSelected = list.length > 0 && selectedIds.size === list.length

  const toggleSelectAll = () => {
    if (allSelected) {
      setSelectedIds(new Set())
    } else {
      setSelectedIds(new Set(list.map((p) => p.id!)))
    }
  }

  const toggleSelect = (id: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <Banknote className="h-7 w-7 text-primary" />
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('nav:payouts', 'Payouts')}</h1>
          <p className="mt-1 text-muted-foreground">Exception queue — normal payouts are auto-approved and paid without admin involvement.</p>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Card>
          <CardContent className="pt-4">
            <p className="text-xs text-muted-foreground font-medium">Flagged payouts</p>
            <p className="text-2xl font-bold">{list.length}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4">
            <p className="text-xs text-muted-foreground font-medium">Open risk events</p>
            <p className="text-2xl font-bold">{riskEvents?.length ?? 0}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4">
            <p className="text-xs text-muted-foreground font-medium">Total flagged amount</p>
            <p className="text-2xl font-bold">{formatMoneyMinor(totalAmount, list[0]?.currency ?? 'VND')}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-4">
            <p className="text-xs text-muted-foreground font-medium">Selected</p>
            <p className="text-2xl font-bold">{selectedIds.size} of {list.length}</p>
          </CardContent>
        </Card>
      </div>

      {selectedIds.size > 0 && (
        <div className="flex items-center gap-3 rounded-lg border border-primary/30 bg-primary/5 px-4 py-2.5">
          <span className="text-sm font-medium">{selectedIds.size} payout{selectedIds.size !== 1 ? 's' : ''} selected</span>
          <div className="ml-auto flex gap-2">
            <Button
              size="sm"
              onClick={() => batchApproveMutation.mutate(selectedArray)}
              disabled={batchApproveMutation.isPending}
            >
              <Check className="mr-1 h-4 w-4" />
              {batchApproveMutation.isPending ? 'Approving…' : `Approve ${selectedIds.size}`}
            </Button>
            <Button
              size="sm"
              variant="destructive"
              onClick={() => batchRejectMutation.mutate(selectedArray)}
              disabled={batchRejectMutation.isPending}
            >
              <X className="mr-1 h-4 w-4" />
              {batchRejectMutation.isPending ? 'Rejecting…' : `Reject ${selectedIds.size}`}
            </Button>
          </div>
        </div>
      )}

      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle className="flex items-center gap-2"><AlertTriangle className="h-5 w-5 text-destructive" /> Flagged payouts ({list.length})</CardTitle>
          <Button
            variant="outline"
            size="sm"
            onClick={() => exportMutation.mutate()}
            disabled={exportMutation.isPending || list.length === 0}
          >
            <Download className="mr-1.5 h-4 w-4" />
            {exportMutation.isPending ? 'Exporting…' : 'Export CSV'}
          </Button>
        </CardHeader>
        <CardContent>
          {isError ? <p className="text-destructive">{error instanceof Error ? error.message : 'Unable to load payouts.'}</p>
            : isLoading ? <p className="text-muted-foreground">Loading…</p>
              : list.length === 0 ? <p className="py-8 text-center text-muted-foreground">No payouts require manual review.</p>
                : (
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead className="w-10">
                          <input
                            type="checkbox"
                            checked={allSelected}
                            onChange={toggleSelectAll}
                            className="h-4 w-4 rounded border-border accent-primary"
                          />
                        </TableHead>
                        <TableHead>Created</TableHead>
                        <TableHead>Bank destination</TableHead>
                        <TableHead>Why it was flagged</TableHead>
                        <TableHead className="text-right">Amount</TableHead>
                        <TableHead className="text-right">Actions</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {list.map((payout) => (
                        <TableRow key={payout.id}>
                          <TableCell>
                            <input
                              type="checkbox"
                              checked={selectedIds.has(payout.id!)}
                              onChange={() => toggleSelect(payout.id!)}
                              className="h-4 w-4 rounded border-border accent-primary"
                            />
                          </TableCell>
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

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <ShieldAlert className="h-5 w-5 text-amber-600 dark:text-amber-400" />
            Vendor payout risk events ({riskEvents?.length ?? 0})
          </CardTitle>
        </CardHeader>
        <CardContent>
          {isRiskError ? (
            <p className="text-sm text-destructive">Unable to load payout risk events.</p>
          ) : isRiskLoading ? (
            <p className="text-sm text-muted-foreground">Loading risk events…</p>
          ) : !riskEvents?.length ? (
            <p className="py-6 text-center text-sm text-muted-foreground">No unresolved disputes, chargebacks, or payout failures.</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Occurred</TableHead>
                  <TableHead>Event</TableHead>
                  <TableHead>Owner</TableHead>
                  <TableHead>Details</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {riskEvents.map((event: VendorRiskEvent) => (
                  <TableRow key={event.id}>
                    <TableCell className="whitespace-nowrap">
                      {event.occurredAt ? new Date(event.occurredAt).toLocaleString() : '—'}
                    </TableCell>
                    <TableCell><Badge variant="destructive">{event.eventType ?? 'RISK'}</Badge></TableCell>
                    <TableCell><code className="text-xs">{event.ownerId?.slice(0, 8) ?? '—'}</code></TableCell>
                    <TableCell>{event.details ?? '—'}</TableCell>
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
