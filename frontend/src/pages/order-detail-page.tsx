import { useState } from 'react'
import { useParams, Link, useNavigate } from '@tanstack/react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  useGetOrderQuery,
  useConfirmReceiptMutation,
  useUpdateStatusMutation,
  useOwnerDecisionMutation,
  listOrdersQueryKey,
  useMeQuery,
    useEcommerceOrdersQuery,
    useReconcileEcommerceOrderMutation,
} from '@/shared/api'
import {
  useOrderEscrowQuery,
  useOrderPaymentActivityQuery,
  useOrderRefundsQuery,
  type OrderPaymentAttempt,
} from '@/shared/api/order-details'
import { cartApi } from '@/shared/api/cart'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/shared/ui/card'
import { Badge } from '@/shared/ui/badge'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { formatMoneyMinor, formatUSD } from '@/shared/utils/currency'
import {
  AlertCircle, ArrowLeft, Calendar, CheckCircle2, Package, XCircle,
  User, Truck, Tag, Receipt, Clock, ChevronRight, Printer, RefreshCw,
  Trash2, Save, PlugZap, CreditCard, Shield, RotateCcw, KeyRound,
} from 'lucide-react'

interface OrderHistoryEntry {
  id: string
  oldStatus?: string
  newStatus: string
  note?: string
  createdAt: string
  createdBy?: string
}

const STATUS_CONFIG: Record<string, { label: string; badgeClass: string; dotClass: string; icon: React.ElementType }> = {
  PENDING_PAYMENT: {
    label: 'Pending payment', badgeClass: 'border-amber-500/30 bg-amber-500/15 text-amber-700 dark:text-amber-300',
    dotClass: 'border-amber-400 bg-amber-500 text-white', icon: CreditCard,
  },
  PAID_ESCROW: {
    label: 'Awaiting owner decision', badgeClass: 'border-violet-500/30 bg-violet-500/15 text-violet-700 dark:text-violet-300',
    dotClass: 'border-violet-400 bg-violet-500 text-white', icon: Clock,
  },
  OWNER_ACCEPTED: {
    label: 'Owner accepted', badgeClass: 'border-sky-500/30 bg-sky-500/15 text-sky-700 dark:text-sky-300',
    dotClass: 'border-sky-400 bg-sky-500 text-white', icon: CheckCircle2,
  },
  FULFILLING: {
    label: 'Fulfilling', badgeClass: 'border-orange-500/30 bg-orange-500/15 text-orange-700 dark:text-orange-300',
    dotClass: 'border-orange-400 bg-orange-500 text-white', icon: Package,
  },
  DELIVERED: {
    label: 'Delivered', badgeClass: 'border-emerald-500/30 bg-emerald-500/15 text-emerald-700 dark:text-emerald-300',
    dotClass: 'border-emerald-400 bg-emerald-500 text-white', icon: Truck,
  },
  OWNER_DECLINED: {
    label: 'Owner declined', badgeClass: 'border-red-500/30 bg-red-500/15 text-red-700 dark:text-red-300',
    dotClass: 'border-red-400 bg-red-500 text-white', icon: XCircle,
  },
  REFUNDED: {
    label: 'Refunded', badgeClass: 'border-slate-500/30 bg-slate-500/15 text-slate-700 dark:text-slate-300',
    dotClass: 'border-slate-400 bg-slate-500 text-white', icon: RefreshCw,
  },
  PENDING: {
    label: 'Pending',
    badgeClass: 'border-amber-500/30 bg-amber-500/15 text-amber-700 dark:text-amber-300',
    dotClass: 'border-amber-400 bg-amber-500 text-white',
    icon: Clock,
  },
  CONFIRMED: {
    label: 'Confirmed',
    badgeClass: 'border-sky-500/30 bg-sky-500/15 text-sky-700 dark:text-sky-300',
    dotClass: 'border-sky-400 bg-sky-500 text-white',
    icon: CheckCircle2,
  },
  SHIPPING: {
    label: 'Shipping',
    badgeClass: 'border-violet-500/30 bg-violet-500/15 text-violet-700 dark:text-violet-300',
    dotClass: 'border-violet-400 bg-violet-500 text-white',
    icon: Package,
  },
  SHIPPED: {
    label: 'Shipped',
    badgeClass: 'border-orange-500/30 bg-orange-500/15 text-orange-700 dark:text-orange-300',
    dotClass: 'border-orange-400 bg-orange-500 text-white',
    icon: Truck,
  },
  COMPLETED: {
    label: 'Completed',
    badgeClass: 'border-emerald-500/30 bg-emerald-500/15 text-emerald-700 dark:text-emerald-300',
    dotClass: 'border-emerald-400 bg-emerald-500 text-white',
    icon: CheckCircle2,
  },
  CANCELLED: {
    label: 'Cancelled',
    badgeClass: 'border-red-500/30 bg-red-500/15 text-red-700 dark:text-red-300',
    dotClass: 'border-red-400 bg-red-500 text-white',
    icon: XCircle,
  },
}

function getStatusConfig(status: string) {
  return STATUS_CONFIG[status?.toUpperCase()] ?? {
    label: status,
    badgeClass: 'border-border bg-muted text-muted-foreground',
    dotClass: 'border-border bg-muted text-muted-foreground',
    icon: Clock,
  }
}

async function fetchOrderHistory(orderId: string): Promise<OrderHistoryEntry[]> {
  const token = localStorage.getItem('access_token')
  const baseUrl = import.meta.env.VITE_API_BASE_URL || ''
  const res = await fetch(`${baseUrl}/api/orders/${orderId}/history`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!res.ok) return []
  return res.json()
}

async function deleteOrder(orderId: string): Promise<void> {
  const token = localStorage.getItem('access_token')
  const baseUrl = import.meta.env.VITE_API_BASE_URL || ''
  const res = await fetch(`${baseUrl}/api/orders/${orderId}`, {
    method: 'DELETE',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!res.ok) {
    const message = await res.text()
    throw new Error(message || 'Unable to delete order')
  }
}

function paymentBadgeClass(status?: string): string {
  switch (status?.toUpperCase()) {
    case 'SUCCEEDED':
    case 'SUCCESS':
      return 'border-success/30 bg-success/10 text-success'
    case 'FAILED':
    case 'CANCELLED':
      return 'border-destructive/30 bg-destructive/10 text-destructive'
    case 'PROCESSING':
      return 'border-sky-500/30 bg-sky-500/10 text-sky-700 dark:text-sky-300'
    default:
      return 'border-amber-500/30 bg-amber-500/10 text-amber-700 dark:text-amber-300'
  }
}

function PaymentActivityCard({
  attempts,
  isLoading,
  isError,
  orderId,
  payable,
}: {
  attempts: OrderPaymentAttempt[]
  isLoading: boolean
  isError: boolean
  orderId: string
  payable: boolean
}) {
  return (
    <Card className="shadow-sm">
      <CardHeader className="flex flex-row items-center gap-3">
        <CreditCard className="h-5 w-5 text-primary" />
        <div>
          <CardTitle className="text-lg">Payment Activity</CardTitle>
          <CardDescription>PaymentIntent lifecycle and gateway attempts from the backend</CardDescription>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {isLoading ? (
          <p className="text-sm text-muted-foreground">Loading payment activity...</p>
        ) : isError ? (
          <div className="rounded-md border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
            Payment activity could not be loaded for this order.
          </div>
        ) : attempts.length === 0 ? (
          <div className="space-y-3 rounded-lg border border-dashed p-4 text-sm">
            <p className="text-muted-foreground">No PaymentIntent has been created yet.</p>
            {payable && (
              <Link to="/orders/payment" search={{ orderId }}>
                <Button size="sm"><CreditCard className="mr-1.5 h-4 w-4" /> Open payment checkout</Button>
              </Link>
            )}
          </div>
        ) : (
          attempts.map(({ intent, transactions }, index) => (
            <div key={intent.id} className="space-y-3 rounded-lg border p-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <div className="flex items-center gap-2">
                    <Badge variant="outline" className={paymentBadgeClass(intent.status)}>
                      {intent.status ?? 'UNKNOWN'}
                    </Badge>
                    {index === 0 && <Badge variant="secondary">Latest</Badge>}
                  </div>
                  <p className="mt-2 text-xs text-muted-foreground">
                    Created {intent.createdAt ? new Date(intent.createdAt).toLocaleString() : '—'}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    Expires {intent.expiresAt ? new Date(intent.expiresAt).toLocaleString() : '—'}
                  </p>
                </div>
                <span className="font-semibold">
                  {formatMoneyMinor(Number(intent.amountMinor ?? 0), intent.currency ?? 'USD')}
                </span>
              </div>

              <div className="flex min-w-0 items-start gap-2 rounded-md bg-muted/50 p-2 text-xs">
                <KeyRound className="mt-0.5 h-3.5 w-3.5 shrink-0 text-muted-foreground" />
                <div className="min-w-0">
                  <span className="text-muted-foreground">Idempotency key</span>
                  <code className="block truncate font-mono" title={intent.idempotencyKey}>{intent.idempotencyKey ?? '—'}</code>
                </div>
              </div>

              {transactions.length === 0 ? (
                <p className="text-xs text-muted-foreground">No gateway transaction recorded yet.</p>
              ) : (
                <div className="space-y-2">
                  {transactions.map((transaction) => (
                    <div key={transaction.id} className="flex flex-wrap items-center justify-between gap-2 rounded-md border bg-background px-3 py-2 text-xs">
                      <div className="min-w-0">
                        <div className="flex items-center gap-2">
                          <span className="font-semibold">{transaction.gateway ?? 'Gateway'}</span>
                          <Badge variant="outline" className={paymentBadgeClass(transaction.status)}>
                            {transaction.status ?? 'UNKNOWN'}
                          </Badge>
                        </div>
                        <code className="mt-1 block max-w-56 truncate font-mono text-muted-foreground" title={transaction.gatewayTransactionId}>
                          {transaction.gatewayTransactionId ?? '—'}
                        </code>
                      </div>
                      <span className="text-muted-foreground">
                        {transaction.createdAt ? new Date(transaction.createdAt).toLocaleString() : '—'}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          ))
        )}
      </CardContent>
    </Card>
  )
}

export function OrderDetailPage() {
  const { id } = useParams({ from: '/_authenticated/orders/$id' })
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data: me } = useMeQuery()
  const isAdmin = me?.role?.name === 'admin'
  const isCustomer = me?.role?.name === 'customer' || me?.role?.name === 'user'
  const isOwner = me?.role?.name === 'businessOwner'
  const canAccessPayment = isAdmin || isOwner || me?.role?.name === 'operator' || isCustomer
  const isStaff = me?.role?.name === 'admin' || me?.role?.name === 'businessOwner' || me?.role?.name === 'operator'

  const [showStatusDialog, setShowStatusDialog] = useState(false)
  const [pendingStatus, setPendingStatus] = useState('')
  const [statusNote, setStatusNote] = useState('')
  const [statusError, setStatusError] = useState<string | null>(null)
  const [showDeclineDialog, setShowDeclineDialog] = useState(false)
  const [declineReason, setDeclineReason] = useState('')
  const [decisionError, setDecisionError] = useState<string | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const [error, setError] = useState('')
  const [cancellingItem, setCancellingItem] = useState<any | null>(null)
  const [cancelItemReason, setCancelItemReason] = useState('')
  const [cancelItemError, setCancelItemError] = useState<string | null>(null)
  const [notes, setNotes] = useState<string | null>(null)
  const [shippingFeeMinor, setShippingFee] = useState<string | null>(null)
  const [discountPercent, setDiscountPercent] = useState<string | null>(null)
  const [vatPercent, setVatPercent] = useState<string | null>(null)

  const {
    data: order,
    isLoading,
    isFetching: isOrderFetching,
    error: orderError,
    refetch: refetchOrder,
  } = useGetOrderQuery({
    path: { id },
  })

  const tenantScope = me?.tenantId
  const isWooEnabled = Boolean(me?.role?.name && !isCustomer)
  const {
    data: wooOrders,
    refetch: refetchWooOrders,
    isFetching: isWooOrdersFetching,
  } = useEcommerceOrdersQuery(
    tenantScope,
    order?.orderNumber ? { search: order.orderNumber } : undefined,
    { enabled: isWooEnabled }
  )
  const wooOrderInfo = wooOrders?.find((o) => o.erpOrderId === order?.id)

  const reconcileMutation = useReconcileEcommerceOrderMutation()

  const historyQuery = useQuery({
    queryKey: ['order-history', id],
    queryFn: () => fetchOrderHistory(id),
    enabled: Boolean(order),
  })

  const history = historyQuery.data ?? []
  const escrowQuery = useOrderEscrowQuery(id)
  const refundQuery = useOrderRefundsQuery(id)
  const paymentActivityQuery = useOrderPaymentActivityQuery(id, canAccessPayment)
  const escrowRecords = escrowQuery.data
  const refundRecords = refundQuery.data
  const isRefreshing = isOrderFetching
    || historyQuery.isFetching
    || isWooOrdersFetching
    || escrowQuery.isFetching
    || refundQuery.isFetching
    || paymentActivityQuery.isFetching

  const refreshOrderPage = async () => {
    const promises: Promise<any>[] = [
      refetchOrder(),
      historyQuery.refetch(),
      escrowQuery.refetch(),
      refundQuery.refetch(),
      paymentActivityQuery.refetch(),
    ]
    if (isWooEnabled) promises.push(refetchWooOrders())
    await Promise.all(promises)
  }

  const updateStatusMutation = useUpdateStatusMutation({
    onSuccess: async () => {
      await refreshOrderPage()
      queryClient.invalidateQueries({
        predicate: (query) => {
          const key = query.queryKey[0]
          return typeof key === 'object' && key !== null && '_id' in key && key._id === 'list4'
        },
      })
      setShowStatusDialog(false)
      setStatusNote('')
      setStatusError(null)
    },
    onError: (err) => {
      setStatusError(err instanceof Error ? err.message : 'Unable to update order status.')
    },
  })

  const ownerDecisionMutation = useOwnerDecisionMutation({
    onSuccess: async () => {
      await refreshOrderPage()
      await queryClient.invalidateQueries({
        queryKey: listOrdersQueryKey({ query: { status: 'PAID_ESCROW' } }),
      })
      setShowDeclineDialog(false)
      setDeclineReason('')
      setDecisionError(null)
    },
    onError: (err) => setDecisionError(err instanceof Error ? err.message : 'Unable to submit owner decision.'),
  })

  const confirmReceiptMutation = useConfirmReceiptMutation({
    onSuccess: async () => {
      await refreshOrderPage()
      await queryClient.invalidateQueries({ queryKey: listOrdersQueryKey() })
      setStatusError(null)
    },
    onError: (err) => {
      setStatusError(err instanceof Error ? err.message : 'Unable to confirm receipt.')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: deleteOrder,
    onSuccess: async () => {
      queryClient.invalidateQueries({
        predicate: (query) => {
          const key = query.queryKey[0]
          return typeof key === 'object' && key !== null && '_id' in key && key._id === 'list4'
        },
      })
      await navigate({ to: '/orders' })
    },
    onError: (err) => {
      setDeleteError(err instanceof Error ? err.message : 'Unable to delete order.')
    },
  })

  const updateOrderMutation = useMutation({
    mutationFn: () => cartApi.updateOrder(id, {
      notes: notes ?? undefined,
      shippingFeeMinor: shippingFeeMinor !== null ? Number(shippingFeeMinor) : undefined,
      discountPercent: discountPercent !== null ? Number(discountPercent) : undefined,
      vatPercent: vatPercent !== null ? Number(vatPercent) : undefined,
    }),
    onSuccess: async () => {
      await refreshOrderPage()
      queryClient.invalidateQueries({ queryKey: ['order-history', id] })
      setError('')
    },
    onError: (err) => setError(err instanceof Error ? err.message : 'Unable to update order.'),
  })

  const cancelItemMutation = useMutation({
    mutationFn: async (body: { itemId: string; reason?: string }) => {
      const token = localStorage.getItem('access_token')
      const baseUrl = import.meta.env.VITE_API_BASE_URL || ''
      const res = await fetch(`${baseUrl}/api/orders/${id}/items/${body.itemId}/cancel`, {
        method: 'POST',
        headers: {
          'Authorization': token ? `Bearer ${token}` : '',
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ reason: body.reason || undefined }),
      })
      if (!res.ok) {
        const message = await res.text()
        throw new Error(message || 'Unable to cancel item')
      }
      return res.json()
    },
    onSuccess: async () => {
      await refreshOrderPage()
      queryClient.invalidateQueries({ queryKey: listOrdersQueryKey() })
      queryClient.invalidateQueries({ queryKey: ['order-history', id] })
      setCancellingItem(null)
      setCancelItemReason('')
      setCancelItemError(null)
    },
    onError: (err) => {
      setCancelItemError(err instanceof Error ? err.message : 'Unable to cancel item.')
    },
  })

  const openStatusDialog = (newStatus: string) => {
    setPendingStatus(newStatus)
    setStatusNote('')
    setStatusError(null)
    setShowStatusDialog(true)
  }

  const handleConfirmStatus = () => {
    setStatusError(null)
    updateStatusMutation.mutate({
      path: { id },
      body: { status: pendingStatus, note: statusNote || undefined } as any,
    })
  }

  const submitOwnerDecision = (decision: 'ACCEPT' | 'DECLINE') => {
    if (decision === 'DECLINE' && !declineReason.trim()) {
      setDecisionError('A decline reason is required.')
      return
    }
    setDecisionError(null)
    ownerDecisionMutation.mutate({
      path: { id },
      body: { decision, reason: decision === 'DECLINE' ? declineReason.trim() : undefined },
    })
  }

  if (isLoading) {
    return <div className="text-center py-10 text-muted-foreground">Loading order details...</div>
  }

  if (orderError || !order) {
    return (
      <div className="text-center py-10 space-y-4">
        <AlertCircle className="h-10 w-10 text-destructive mx-auto" />
        <h2 className="text-xl font-bold">Order Not Found</h2>
        <p className="text-muted-foreground">We couldn't retrieve the details for this order.</p>
        <Link to="/orders">
          <Button variant="outline" className="mt-2">
            <ArrowLeft className="h-4 w-4 mr-2" /> Back to Orders
          </Button>
        </Link>
      </div>
    )
  }

  const status = order.status?.toUpperCase() ?? 'PENDING'
  const isPending = status === 'PENDING'
  const isConfirmed = status === 'CONFIRMED'
  const isShipping = status === 'SHIPPING'
  const isShipped = status === 'SHIPPED'
  const isPendingPayment = status === 'PENDING' || status === 'PENDING_PAYMENT'
  const isFinal = ['COMPLETED', 'CANCELLED', 'OWNER_DECLINED', 'REFUNDED'].includes(status)
  const canDelete = isAdmin && isFinal
  const cancellableStatuses = ['PENDING_PAYMENT', 'PENDING', 'CONFIRMED', 'SHIPPING']
  const canCancelItem = isCustomer && cancellableStatuses.includes(status)

  const statusCfg = getStatusConfig(status)
  const currentNotes = notes ?? order.notes ?? ''
  const currentShipping = shippingFeeMinor ?? order.shippingFeeMinor?.toString() ?? '0'
  const currentDiscount = discountPercent ?? order.discountPercent?.toString() ?? '0'
  const currentVat = vatPercent ?? order.vatPercent?.toString() ?? '0'

  const handleDeleteOrder = () => {
    setDeleteError(null)
    if (!canDelete) return
    if (!window.confirm('Delete this completed/cancelled order?')) return
    deleteMutation.mutate(id)
  }

  return (
    <div className="space-y-6 max-w-5xl mx-auto">
      <div className="flex items-center space-x-2">
        <Link to="/orders" className="text-muted-foreground hover:text-foreground flex items-center gap-1 text-sm font-medium">
          <ArrowLeft className="h-4 w-4" /> Back to Orders
        </Link>
      </div>

      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-border pb-6">
        <div>
          <div className="flex items-center gap-2.5">
            <h1 className="text-3xl font-bold tracking-tight">{order.orderNumber}</h1>
            <Badge variant="outline" className={`text-sm px-2.5 py-0.5 ${statusCfg.badgeClass}`}>
              {statusCfg.label}
            </Badge>
          </div>
          <p className="text-muted-foreground mt-1.5 flex items-center gap-1.5 text-sm">
            <Calendar className="h-4 w-4 text-muted-foreground" />
            Placed on {new Date(order.orderDate!).toLocaleDateString()} {new Date(order.orderDate!).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={refreshOrderPage} disabled={isRefreshing || updateStatusMutation.isPending}>
            <RefreshCw className={`h-4 w-4 mr-1.5 ${isRefreshing ? 'animate-spin' : ''}`} /> Refresh
          </Button>

          <Button variant="outline" size="sm" onClick={() => window.print()}>
            <Printer className="h-4 w-4 mr-1.5" /> Print
          </Button>

          {canAccessPayment && isPendingPayment && (
            <Link to="/orders/payment" search={{ orderId: id }}>
              <Button size="sm">
                <CreditCard className="h-4 w-4 mr-1.5" /> Payment
              </Button>
            </Link>
          )}

          {canDelete && (
            <Button
              variant="destructive"
              size="sm"
              onClick={handleDeleteOrder}
              disabled={deleteMutation.isPending}
            >
              <Trash2 className="h-4 w-4 mr-1.5" />
              {deleteMutation.isPending ? 'Deleting...' : 'Delete'}
            </Button>
          )}

          {isOwner && status === 'PAID_ESCROW' && (
            <>
              <Button onClick={() => submitOwnerDecision('ACCEPT')} disabled={ownerDecisionMutation.isPending} className="font-semibold">
                <CheckCircle2 className="h-4 w-4 mr-1.5" /> Accept
              </Button>
              <Button onClick={() => setShowDeclineDialog(true)} disabled={ownerDecisionMutation.isPending} variant="destructive" className="font-semibold">
                <XCircle className="h-4 w-4 mr-1.5" /> Decline
              </Button>
            </>
          )}
          {isStaff && isPending && (
            <>
              <Button onClick={() => openStatusDialog('CONFIRMED')} variant="secondary" className="font-semibold">
                <CheckCircle2 className="h-4 w-4 mr-1.5" /> Confirm
              </Button>
              <Button onClick={() => openStatusDialog('CANCELLED')} variant="destructive" className="font-semibold">
                <XCircle className="h-4 w-4 mr-1.5" /> Cancel
              </Button>
            </>
          )}
          {isStaff && isConfirmed && (
            <Button onClick={() => openStatusDialog('SHIPPING')} className="font-semibold">
              <Package className="h-4 w-4 mr-1.5" /> Start Shipping
            </Button>
          )}
          {isStaff && isShipping && (
            <>
              <Button onClick={() => openStatusDialog('SHIPPED')} variant="secondary" className="font-semibold">
                <Truck className="h-4 w-4 mr-1.5" /> Mark Shipped
              </Button>
              <Button onClick={() => openStatusDialog('CANCELLED')} variant="destructive" className="font-semibold">
                <XCircle className="h-4 w-4 mr-1.5" /> Cancel
              </Button>
            </>
          )}
          {isStaff && isShipped && (
            <Button onClick={() => openStatusDialog('COMPLETED')} className="bg-success text-success-foreground hover:bg-success/90 font-semibold">
              <CheckCircle2 className="h-4 w-4 mr-1.5" /> Complete
            </Button>
          )}
          {isStaff && status === 'OWNER_ACCEPTED' && <Button onClick={() => openStatusDialog('FULFILLING')}><Package className="h-4 w-4 mr-1.5" /> Start fulfillment</Button>}
          {isStaff && status === 'FULFILLING' && <Button onClick={() => openStatusDialog('DELIVERED')}><Truck className="h-4 w-4 mr-1.5" /> Mark delivered</Button>}
          {isStaff && status === 'DELIVERED' && <Button onClick={() => openStatusDialog('COMPLETED')} className="bg-success text-success-foreground hover:bg-success/90"><CheckCircle2 className="h-4 w-4 mr-1.5" /> Complete</Button>}
          {isCustomer && status === 'DELIVERED' && (
            <Button
              onClick={() => confirmReceiptMutation.mutate({ path: { id } })}
              disabled={confirmReceiptMutation.isPending}
              className="bg-success text-success-foreground hover:bg-success/90"
            >
              <CheckCircle2 className="h-4 w-4 mr-1.5" />
              {confirmReceiptMutation.isPending ? 'Confirming...' : 'Confirm received'}
            </Button>
          )}
        </div>
      </div>

      {deleteError && (
        <div className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm font-medium text-destructive">
          {deleteError}
        </div>
      )}
      {decisionError && <div className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">{decisionError}</div>}
      {status === 'PAID_ESCROW' && order.ownerDecisionDeadline && (
        <div className="rounded-md border border-violet-500/30 bg-violet-500/10 px-3 py-2 text-sm">
          Owner decision deadline: <strong>{new Date(order.ownerDecisionDeadline).toLocaleString()}</strong>. Funds remain in escrow after acceptance.
        </div>
      )}

      {canAccessPayment && (
        <PaymentActivityCard
          attempts={paymentActivityQuery.data}
          isLoading={paymentActivityQuery.isLoading}
          isError={paymentActivityQuery.isError}
          orderId={id}
          payable={isPendingPayment}
        />
      )}

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">

        <div className="md:col-span-2 space-y-6">

          <Card className="shadow-sm">
            <CardHeader className="flex flex-row items-center gap-3">
              <Receipt className="h-5 w-5 text-primary" />
              <div>
                <CardTitle className="text-lg">Order Items</CardTitle>
                <CardDescription>Items in this sales order</CardDescription>
              </div>
            </CardHeader>
            <CardContent className="p-0">
              <div className="divide-y divide-border px-6">
                {order.items?.map((item, index) => {
                  const isItemCancelled = !!(item as any).cancelledAt
                  const variantValues = item.productVariant
                    ? Object.entries(item.productVariant.optionValues ?? {})
                        .map(([_, v]) => v)
                        .join(' / ')
                    : null

                  return (
                    <div key={index} className={`py-4 flex items-center justify-between gap-4 ${isItemCancelled ? 'opacity-50' : ''}`}>
                      <div className="space-y-0.5 min-w-0 flex-1">
                        <div className="flex items-center gap-1.5">
                          <Package className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
                          <h4 className={`font-semibold text-sm ${isItemCancelled ? 'line-through text-muted-foreground' : ''}`}>
                            {item.product?.name}
                          </h4>
                          {isItemCancelled && (
                            <Badge variant="destructive" className="text-[10px] px-1.5 py-0">Cancelled</Badge>
                          )}
                        </div>
                        {variantValues && (
                          <p className="text-xs text-muted-foreground font-medium">{variantValues}</p>
                        )}
                        <p className="text-xs text-muted-foreground">SKU: {item.productVariant?.sku ?? item.product?.sku}</p>
                        {isItemCancelled && (item as any).cancellationReason && (
                          <p className="text-xs text-destructive italic mt-0.5">Reason: {(item as any).cancellationReason}</p>
                        )}
                      </div>

                      <div className="flex items-center gap-3 shrink-0">
                        {!isItemCancelled && canCancelItem && (
                          <Button
                            size="sm"
                            variant="destructive"
                            className="h-8 text-xs"
                            onClick={() => {
                              setCancellingItem(item)
                              setCancelItemReason('')
                              setCancelItemError(null)
                            }}
                          >
                            <XCircle className="h-3.5 w-3.5 mr-1" /> Cancel
                          </Button>
                        )}
                        <div className="text-right">
                          <div className="text-sm font-semibold">
                            {formatUSD(Number(item.unitPriceMinor) * (item.quantity ?? 0))}
                          </div>
                          <div className="text-xs text-muted-foreground">
                            {formatUSD(Number(item.unitPriceMinor))} × {item.quantity ?? 0}
                          </div>
                        </div>
                      </div>
                    </div>
                  )
                })}
              </div>
            </CardContent>
          </Card>

          <Card className="shadow-sm">
            <CardHeader className="flex flex-row items-center gap-3">
              <Truck className="h-5 w-5 text-primary" />
              <div>
                <CardTitle className="text-lg">Delivery Information</CardTitle>
                <CardDescription>Recipient and courier coordinates</CardDescription>
              </div>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
                <div>
                  <span className="text-muted-foreground block text-xs">Recipient Name</span>
                  <span className="font-medium text-sm">{order.shippingName}</span>
                </div>
                <div>
                  <span className="text-muted-foreground block text-xs">Phone Number</span>
                  <span className="font-medium text-sm">{order.shippingPhone}</span>
                </div>
                <div className="sm:col-span-2">
                  <span className="text-muted-foreground block text-xs">Shipping Address</span>
                  <span className="font-medium text-sm block whitespace-pre-wrap">{order.shippingAddress}</span>
                </div>
                {order.shippingNote && (
                  <div className="sm:col-span-2">
                    <span className="text-muted-foreground block text-xs">Courier Notes</span>
                    <span className="text-muted-foreground italic text-sm">{order.shippingNote}</span>
                  </div>
                )}
              </div>
            </CardContent>
          </Card>

          {isStaff && (
            <Card className="shadow-sm">
              <CardHeader>
                <CardTitle className="text-lg">Update Order</CardTitle>
                <CardDescription>Edit order notes, shipping fee, discount, and VAT</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="notes">Notes</Label>
                  <textarea
                    id="notes"
                    className="flex min-h-[80px] w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                    value={currentNotes}
                    onChange={(e) => setNotes(e.target.value)}
                  />
                </div>
                <div className="grid gap-4 sm:grid-cols-2">
                  <div className="space-y-2">
                    <Label htmlFor="shipping">Shipping Fee ($)</Label>
                    <Input
                      id="shipping"
                      type="number"
                      min="0"
                      step="0.01"
                      value={currentShipping}
                      onChange={(e) => setShippingFee(e.target.value)}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="discount">Discount (%)</Label>
                    <Input
                      id="discount"
                      type="number"
                      min="0"
                      max="100"
                      step="0.1"
                      value={currentDiscount}
                      onChange={(e) => {
                        const val = e.target.value
                        if (val === '' || (Number(val) >= 0 && Number(val) <= 100)) setDiscountPercent(val)
                      }}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="vat">VAT (%)</Label>
                    <Input
                      id="vat"
                      type="number"
                      min="0"
                      max="100"
                      step="0.1"
                      value={currentVat}
                      onChange={(e) => {
                        const val = e.target.value
                        if (val === '' || (Number(val) >= 0 && Number(val) <= 100)) setVatPercent(val)
                      }}
                    />
                  </div>
                </div>
                {error && (
                  <div className="flex items-start gap-2 rounded-md bg-destructive/10 p-3 text-sm text-destructive">
                    <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
                    <span>{error}</span>
                  </div>
                )}
                <Button onClick={() => updateOrderMutation.mutate()} disabled={updateOrderMutation.isPending}>
                  <Save className="h-4 w-4 mr-1" />
                  {updateOrderMutation.isPending ? 'Saving...' : 'Save Changes'}
                </Button>
              </CardContent>
            </Card>
          )}

          <Card className="shadow-sm">
            <CardHeader className="flex flex-row items-center gap-3">
              <Clock className="h-5 w-5 text-primary" />
              <div>
                <CardTitle className="text-lg">Order Timeline</CardTitle>
                <CardDescription>Full lifecycle history of this order</CardDescription>
              </div>
            </CardHeader>
            <CardContent>
              {history.length === 0 ? (
                <p className="text-sm text-muted-foreground text-center py-4">No history available.</p>
              ) : (
                <div className="relative">
                  <div className="absolute left-[11px] top-3 bottom-3 w-px bg-border" />
                  <ul className="space-y-0">
                    {history.map((entry, i) => {
                      const cfg = getStatusConfig(entry.newStatus)
                      const Icon = cfg.icon
                      const isNewest = i === 0
                      return (
                        <li key={entry.id} className="flex gap-4 relative">
                          <div className={`relative z-10 mt-1 flex h-6 w-6 shrink-0 items-center justify-center rounded-full border-2 ${
                            isNewest ? cfg.dotClass : 'border-border bg-background text-muted-foreground'
                          }`}>
                            <Icon className="h-3 w-3" />
                          </div>

                          <div className="pb-6 flex-1">
                            <div className="flex min-w-0 flex-col gap-1 sm:flex-row sm:items-start sm:justify-between sm:gap-3">
                              <div className="min-w-0">
                                <div className="flex flex-wrap items-center gap-2">
                                  {entry.oldStatus && (
                                    <>
                                      <Badge variant="outline" className={`text-xs py-0 ${getStatusConfig(entry.oldStatus).badgeClass}`}>
                                        {entry.oldStatus}
                                      </Badge>
                                      <ChevronRight className="h-3 w-3 text-muted-foreground" />
                                    </>
                                  )}
                                  <Badge variant="outline" className={`text-xs py-0 ${cfg.badgeClass}`}>
                                    {entry.newStatus}
                                  </Badge>
                                </div>
                                {entry.note && (
                                  <p className="text-xs text-muted-foreground mt-1 italic">"{entry.note}"</p>
                                )}
                              </div>
                              <time className="text-xs text-muted-foreground shrink-0 sm:text-right">
                                {new Date(entry.createdAt).toLocaleDateString()} {new Date(entry.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                              </time>
                            </div>
                          </div>
                        </li>
                      )
                    })}
                  </ul>
                </div>
              )}
            </CardContent>
          </Card>
        </div>

        <div className="space-y-6">

          {wooOrderInfo && (
            <Card className="shadow-sm border-primary/20 bg-primary/5">
              <CardHeader className="flex flex-row items-center gap-3 pb-3">
                <PlugZap className="h-5 w-5 text-primary" />
                <div>
                  <CardTitle className="text-lg">Ecommerce Sync</CardTitle>
                  <CardDescription>Order integration details</CardDescription>
                </div>
              </CardHeader>
              <CardContent className="text-sm space-y-3">
                <div>
                  <span className="text-muted-foreground block text-xs">Ecommerce Order ID</span>
                  <span className="font-semibold text-foreground">#{wooOrderInfo.wooOrderId}</span>
                </div>
                <div>
                  <span className="text-muted-foreground block text-xs">Sync Status</span>
                  <div className="mt-1 flex items-center gap-1.5">
                    {wooOrderInfo.syncStatus === 'SYNCED' && (
                      <Badge className="border-transparent bg-success text-success-foreground">
                        Synced
                      </Badge>
                    )}
                    {wooOrderInfo.syncStatus === 'FAILED' && (
                      <Badge variant="destructive">
                        Failed
                      </Badge>
                    )}
                    {wooOrderInfo.syncStatus === 'PROCESSING' && (
                      <Badge variant="outline" className="animate-pulse">
                        Syncing...
                      </Badge>
                    )}
                  </div>
                </div>
                {wooOrderInfo.lastErrorMessage && (
                  <div>
                    <span className="text-muted-foreground block text-xs">Error Log</span>
                    <span className="text-destructive text-xs block bg-destructive/10 border border-destructive/20 rounded p-2 mt-1 font-mono break-all whitespace-pre-wrap">
                      {wooOrderInfo.lastErrorMessage}
                    </span>
                  </div>
                )}
                <div>
                  <span className="text-muted-foreground block text-xs">Last Attempt</span>
                  <span className="font-medium text-xs">
                    {wooOrderInfo.lastAttemptedAt ? new Date(wooOrderInfo.lastAttemptedAt).toLocaleString() : 'N/A'}
                  </span>
                </div>
                {wooOrderInfo.syncStatus !== 'SYNCED' && (
                  <Button
                    onClick={() => {
                      reconcileMutation.mutate(
                        { externalId: wooOrderInfo.wooOrderId! },
                        {
                          onSuccess: () => {
                            refreshOrderPage()
                          },
                        }
                      )
                    }}
                    disabled={reconcileMutation.isPending}
                    size="sm"
                    className="w-full mt-2 font-semibold gap-1.5"
                  >
                    <RefreshCw className={`h-3.5 w-3.5 ${reconcileMutation.isPending ? 'animate-spin' : ''}`} />
                    {reconcileMutation.isPending ? 'Retrying...' : 'Retry Sync / Reconcile'}
                  </Button>
                )}
              </CardContent>
            </Card>
          )}

          <Card className="shadow-sm">
            <CardHeader className="flex flex-row items-center gap-3">
              <User className="h-5 w-5 text-primary" />
              <div>
                <CardTitle className="text-lg">Customer Profile</CardTitle>
                <CardDescription>Ordering buyer client</CardDescription>
              </div>
            </CardHeader>
            <CardContent className="text-sm space-y-3">
              <div>
                <span className="text-muted-foreground block text-xs">Buyer Name</span>
                <Link to="/customers/$id" params={{ id: order.customer?.id! }} className="text-primary hover:underline font-semibold">
                  {order.customer?.firstName} {order.customer?.lastName}
                </Link>
              </div>
              <div>
                <span className="text-muted-foreground block text-xs">Email</span>
                <span className="font-medium">{order.customer?.email || 'N/A'}</span>
              </div>
              <div>
                <span className="text-muted-foreground block text-xs">Phone</span>
                <span className="font-medium">{order.customer?.phone || 'N/A'}</span>
              </div>
            </CardContent>
          </Card>

          <Card className="shadow-sm">
            <CardHeader>
              <CardTitle className="text-lg">Order Totals</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-2 text-sm">
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Subtotal</span>
                  <span className="font-medium">{formatUSD(Number(order.subtotalAmountMinor))}</span>
                </div>

                {Number(order.discountAmountMinor) > 0 && (
                  <div className="flex justify-between text-success">
                    <span className="flex items-center gap-1">
                      <Tag className="h-3.5 w-3.5" />
                      Discount {order.voucher?.code && `(${order.voucher.code})`}
                    </span>
                    <span>-{formatUSD(Number(order.discountAmountMinor))}</span>
                  </div>
                )}

                <div className="flex justify-between">
                  <span className="text-muted-foreground">Shipping Fee</span>
                  <span>
                    {Number(order.shippingFeeMinor) > 0
                      ? `${formatUSD(Number(order.shippingFeeMinor))}`
                      : <span className="text-success font-medium">Free</span>}
                  </span>
                </div>

                <div className="flex justify-between">
                  <span className="text-muted-foreground">VAT (10%)</span>
                  <span>{formatUSD(Number(order.taxAmountMinor))}</span>
                </div>

                <div className="border-t border-border pt-3 mt-2 flex justify-between text-base font-bold">
                  <span>Grand Total</span>
                  <span className="text-primary text-lg">
                    {formatUSD(Number(order.finalAmountMinor))}
                  </span>
                </div>
              </div>
            </CardContent>
          </Card>

          {escrowRecords && escrowRecords.length > 0 && (
            <Card className="shadow-sm">
              <CardHeader className="flex flex-row items-center gap-3">
                <Shield className="h-5 w-5 text-primary" />
                <div>
                  <CardTitle className="text-lg">Escrow Records</CardTitle>
                  <CardDescription>Funds held and released for this order</CardDescription>
                </div>
              </CardHeader>
              <CardContent className="space-y-3">
                {escrowRecords.map((record) => (
                  <div key={record.id} className="flex items-center justify-between p-3 rounded-lg border border-border bg-muted/30">
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <Badge variant={
                          record.status === 'RELEASED' ? 'default' :
                          record.status === 'REFUNDED' ? 'destructive' : 'secondary'
                        }>
                          {record.status}
                        </Badge>
                      </div>
                      <p className="text-xs text-muted-foreground">
                        Release due: {record.releaseDueAt ? new Date(record.releaseDueAt).toLocaleDateString() : '—'}
                      </p>
                    </div>
                    <span className="font-semibold">
                      {formatMoneyMinor(Number(record.amountMinor ?? 0), record.currency ?? order.currency ?? 'USD')}
                    </span>
                  </div>
                ))}
              </CardContent>
            </Card>
          )}

          {refundRecords && refundRecords.length > 0 && (
            <Card className="shadow-sm">
              <CardHeader className="flex flex-row items-center gap-3">
                <RotateCcw className="h-5 w-5 text-primary" />
                <div>
                  <CardTitle className="text-lg">Refund Details</CardTitle>
                  <CardDescription>Payment refunds for this order</CardDescription>
                </div>
              </CardHeader>
              <CardContent className="space-y-3">
                {refundRecords.map((refund) => (
                  <div key={refund.id} className="flex items-center justify-between p-3 rounded-lg border border-border bg-muted/30">
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <Badge variant={refund.status === 'COMPLETED' ? 'default' : 'secondary'}>
                          {refund.status}
                        </Badge>
                        <span className="text-xs text-muted-foreground">{refund.gateway}</span>
                      </div>
                      {refund.reason && (
                        <p className="text-xs text-muted-foreground italic">"{refund.reason}"</p>
                      )}
                      <p className="text-xs text-muted-foreground font-mono">Refund ID: {refund.gatewayRefundId}</p>
                    </div>
                    <span className="font-semibold text-destructive">
                      -{formatMoneyMinor(Number(refund.amountMinor ?? 0), refund.currency ?? order.currency ?? 'USD')}
                    </span>
                  </div>
                ))}
              </CardContent>
            </Card>
          )}
        </div>
      </div>

      <Dialog open={showStatusDialog} onOpenChange={setShowStatusDialog}>
        <DialogContent className="max-w-sm">
          <DialogHeader>
            <DialogTitle>Update Order Status</DialogTitle>
            <DialogDescription>
              Change status to <strong>{pendingStatus}</strong>. Add an optional note for the record.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-3 py-2">
            <div className="space-y-1.5">
              <Label htmlFor="statusNote">Note (optional)</Label>
              <textarea
                id="statusNote"
                className="flex min-h-[80px] w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                value={statusNote}
                onChange={(e) => setStatusNote(e.target.value)}
                placeholder={`e.g. "Package picked up by GHN" or "Customer requested cancel"`}
              />
            </div>
            {statusError && (
              <p className="text-sm text-destructive">{statusError}</p>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowStatusDialog(false)}>
              Cancel
            </Button>
            <Button
              onClick={handleConfirmStatus}
              disabled={updateStatusMutation.isPending}
              variant={pendingStatus === 'CANCELLED' ? 'destructive' : 'default'}
            >
              {updateStatusMutation.isPending ? 'Updating...' : `Set to ${pendingStatus}`}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={showDeclineDialog} onOpenChange={setShowDeclineDialog}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Decline order</DialogTitle>
            <DialogDescription>The buyer will be notified and refunded to the original payment method.</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="decline-reason">Reason</Label>
            <textarea id="decline-reason" rows={4} value={declineReason} onChange={(event) => setDeclineReason(event.target.value)} className="flex w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm" />
            {decisionError && <p className="text-sm text-destructive">{decisionError}</p>}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowDeclineDialog(false)}>Cancel</Button>
            <Button variant="destructive" onClick={() => submitOwnerDecision('DECLINE')} disabled={ownerDecisionMutation.isPending || !declineReason.trim()}>
              {ownerDecisionMutation.isPending ? 'Declining…' : 'Decline and refund'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={!!cancellingItem} onOpenChange={(open) => { if (!open) setCancellingItem(null) }}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Cancel this item?</DialogTitle>
            <DialogDescription>
              This item will be removed from the order and the total will be recalculated.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="rounded-lg border border-border bg-muted/30 p-4 space-y-2">
              <div className="flex items-center gap-2">
                <Package className="h-4 w-4 text-muted-foreground" />
                <span className="font-semibold text-sm">{cancellingItem?.product?.name}</span>
              </div>
              {cancellingItem?.productVariant?.sku && (
                <p className="text-xs text-muted-foreground ml-6">SKU: {cancellingItem.productVariant.sku}</p>
              )}
              <div className="flex justify-between text-sm ml-6">
                <span className="text-muted-foreground">{formatUSD(Number(cancellingItem?.unitPriceMinor))} × {cancellingItem?.quantity ?? 0}</span>
                <span className="font-semibold">{formatUSD(Number(cancellingItem?.unitPriceMinor) * (cancellingItem?.quantity ?? 0))}</span>
              </div>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="cancel-item-reason">Reason (optional)</Label>
              <select
                id="cancel-item-reason"
                className="flex w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                value={cancelItemReason}
                onChange={(e) => setCancelItemReason(e.target.value)}
              >
                <option value="">Select a reason...</option>
                <option value="Wrong size/color">Wrong size/color</option>
                <option value="Changed my mind">Changed my mind</option>
                <option value="Found cheaper elsewhere">Found cheaper elsewhere</option>
                <option value="Ordered by mistake">Ordered by mistake</option>
                <option value="Other">Other...</option>
              </select>
              {cancelItemReason === 'Other' && (
                <textarea
                  className="flex min-h-[60px] w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                  placeholder="Please describe..."
                  onChange={(e) => setCancelItemReason(e.target.value)}
                />
              )}
            </div>
            {cancelItemError && (
              <p className="text-sm text-destructive">{cancelItemError}</p>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCancellingItem(null)} disabled={cancelItemMutation.isPending}>
              Keep
            </Button>
            <Button
              variant="destructive"
              onClick={() => {
                cancelItemMutation.mutate({
                  itemId: cancellingItem?.id,
                  reason: cancelItemReason,
                })
              }}
              disabled={cancelItemMutation.isPending || !cancellingItem?.id}
            >
              {cancelItemMutation.isPending ? 'Cancelling...' : 'Yes, Cancel'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
