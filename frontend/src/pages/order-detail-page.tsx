import { useState } from 'react'
import { useParams, Link, useNavigate } from '@tanstack/react-router'
import {
  useGet4Query, // order detail
  useUpdateStatusMutation,
  useMeQuery,
} from '@/shared/api'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/shared/ui/card'
import { Badge } from '@/shared/ui/badge'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/shared/ui/dialog'
import { Label } from '@/shared/ui/label'
import { formatUSD } from '@/shared/utils/currency'
import {
  AlertCircle, ArrowLeft, Calendar, CheckCircle2, Package, XCircle,
  User, Truck, Tag, Receipt, Clock, ChevronRight, Printer, RefreshCw,
  Trash2,
} from 'lucide-react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

// ─── Types ────────────────────────────────────────────────────────────────────

interface OrderHistoryEntry {
  id: string
  oldStatus?: string
  newStatus: string
  note?: string
  createdAt: string
  createdBy?: string
}

// ─── Status helpers ───────────────────────────────────────────────────────────

const STATUS_CONFIG: Record<string, { label: string; badgeClass: string; dotClass: string; icon: React.ElementType }> = {
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

// ─── Order History via fetch (not in SDK yet — new endpoint) ─────────────────

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

// ─── Main Component ───────────────────────────────────────────────────────────

export function OrderDetailPage() {
  const { id } = useParams({ from: '/_authenticated/orders/$id' })
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data: me } = useMeQuery()
  const isAdmin = me?.role?.name === 'admin'

  // Dialog state
  const [showStatusDialog, setShowStatusDialog] = useState(false)
  const [pendingStatus, setPendingStatus] = useState('')
  const [statusNote, setStatusNote] = useState('')
  const [statusError, setStatusError] = useState<string | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const {
    data: order,
    isLoading,
    isFetching: isOrderFetching,
    error: orderError,
    refetch: refetchOrder,
  } = useGet4Query({
    path: { id },
  })

  const historyQuery = useQuery({
    queryKey: ['order-history', id],
    queryFn: () => fetchOrderHistory(id),
    enabled: Boolean(order),
  })

  const history = historyQuery.data ?? []
  const isRefreshing = isOrderFetching || historyQuery.isFetching

  const refreshOrderPage = async () => {
    await Promise.all([
      refetchOrder(),
      historyQuery.refetch(),
    ])
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
  const isShipped = status === 'SHIPPED'
  const isFinal = status === 'COMPLETED' || status === 'CANCELLED'
  const canDelete = isAdmin && isFinal

  const statusCfg = getStatusConfig(status)

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

      {/* Header */}
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

          {/* Print button */}
          <Button variant="outline" size="sm" onClick={() => window.print()}>
            <Printer className="h-4 w-4 mr-1.5" /> Print
          </Button>

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

          {/* Status action buttons */}
          {!isFinal && (
            <>
              {isPending && (
                <>
                  <Button onClick={() => openStatusDialog('CONFIRMED')} variant="secondary" className="font-semibold">
                    <CheckCircle2 className="h-4 w-4 mr-1.5" /> Confirm
                  </Button>
                  <Button onClick={() => openStatusDialog('CANCELLED')} variant="destructive" className="font-semibold">
                    <XCircle className="h-4 w-4 mr-1.5" /> Cancel
                  </Button>
                </>
              )}
              {isConfirmed && (
                <Button onClick={() => openStatusDialog('SHIPPED')} className="font-semibold">
                  <Truck className="h-4 w-4 mr-1.5" /> Ship Order
                </Button>
              )}
              {isShipped && (
                <Button onClick={() => openStatusDialog('COMPLETED')} variant="default" className="bg-success text-success-foreground hover:bg-success/90 font-semibold">
                  <CheckCircle2 className="h-4 w-4 mr-1.5" /> Complete
                </Button>
              )}
            </>
          )}
        </div>
      </div>

      {deleteError && (
        <div className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm font-medium text-destructive">
          {deleteError}
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">

        {/* LEFT: Items + Delivery + Timeline */}
        <div className="md:col-span-2 space-y-6">

          {/* Order Items */}
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
                  const variantValues = item.productVariant
                    ? Object.entries(item.productVariant.optionValues ?? {})
                        .map(([_, v]) => v)
                        .join(' / ')
                    : null

                  return (
                    <div key={index} className="py-4 flex items-center justify-between">
                      <div className="space-y-0.5">
                        <div className="flex items-center gap-1.5">
                          <Package className="h-3.5 w-3.5 text-muted-foreground" />
                          <h4 className="font-semibold text-sm">{item.product?.name}</h4>
                        </div>
                        {variantValues && (
                          <p className="text-xs text-muted-foreground font-medium">{variantValues}</p>
                        )}
                        <p className="text-xs text-muted-foreground">SKU: {item.productVariant?.sku ?? item.product?.sku}</p>
                      </div>

                      <div className="text-right">
                        <div className="text-sm font-semibold">
                          {formatUSD(Number(item.unitPrice) * (item.quantity ?? 0))}
                        </div>
                        <div className="text-xs text-muted-foreground">
                          {formatUSD(Number(item.unitPrice))} × {item.quantity ?? 0}
                        </div>
                      </div>
                    </div>
                  )
                })}
              </div>
            </CardContent>
          </Card>

          {/* Delivery Information */}
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

          {/* Order Tracking Timeline */}
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
                  {/* vertical line */}
                  <div className="absolute left-[11px] top-3 bottom-3 w-px bg-border" />
                  <ul className="space-y-0">
                    {history.map((entry, i) => {
                      const cfg = getStatusConfig(entry.newStatus)
                      const Icon = cfg.icon
                      const isLast = i === history.length - 1
                      return (
                        <li key={entry.id} className="flex gap-4 relative">
                          {/* Dot */}
                          <div className={`relative z-10 mt-1 flex h-6 w-6 shrink-0 items-center justify-center rounded-full border-2 ${
                            isLast ? cfg.dotClass : 'border-border bg-background text-muted-foreground'
                          }`}>
                            <Icon className="h-3 w-3" />
                          </div>

                          {/* Content */}
                          <div className={`pb-6 flex-1 ${isLast ? '' : ''}`}>
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

        {/* RIGHT: Customer + Totals */}
        <div className="space-y-6">

          {/* Customer Profile */}
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

          {/* Order Totals */}
          <Card className="shadow-sm">
            <CardHeader>
              <CardTitle className="text-lg">Order Totals</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-2 text-sm">
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Subtotal</span>
                  <span className="font-medium">{formatUSD(Number(order.subtotalAmount))}</span>
                </div>

                {Number(order.discountAmount) > 0 && (
                  <div className="flex justify-between text-success">
                    <span className="flex items-center gap-1">
                      <Tag className="h-3.5 w-3.5" />
                      Discount {order.voucher?.code && `(${order.voucher.code})`}
                    </span>
                    <span>-{formatUSD(Number(order.discountAmount))}</span>
                  </div>
                )}

                <div className="flex justify-between">
                  <span className="text-muted-foreground">Shipping Fee</span>
                  <span>
                    {Number(order.shippingFee) > 0
                      ? `${formatUSD(Number(order.shippingFee))}`
                      : <span className="text-success font-medium">Free</span>}
                  </span>
                </div>

                <div className="flex justify-between">
                  <span className="text-muted-foreground">VAT (10%)</span>
                  <span>{formatUSD(Number(order.taxAmount))}</span>
                </div>

                <div className="border-t border-border pt-3 mt-2 flex justify-between text-base font-bold">
                  <span>Grand Total</span>
                  <span className="text-primary text-lg">
                    {formatUSD(Number(order.finalAmount))}
                  </span>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>

      {/* Status Update Dialog */}
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
    </div>
  )
}
