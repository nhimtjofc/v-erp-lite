import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRouter } from '@tanstack/react-router'
import { AlertCircle, ArrowLeft, Save } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { cartApi } from '@/shared/api/cart'
import type { Order } from '@/shared/api/cart'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'

function formatMoney(value?: number | null) {
  if (value === null || value === undefined) return '-'
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(value)
}

function formatDate(value?: string | null) {
  if (!value) return '-'
  return new Date(value).toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

const statusVariants: Record<string, 'default' | 'secondary' | 'destructive' | 'outline'> = {
  PENDING: 'default',
  CONFIRMED: 'secondary',
  SHIPPED: 'outline',
  COMPLETED: 'default',
  CANCELLED: 'destructive',
}

const validTransitions: Record<string, string[]> = {
  PENDING: ['CONFIRMED', 'CANCELLED'],
  CONFIRMED: ['SHIPPED', 'CANCELLED'],
  SHIPPED: ['COMPLETED', 'CANCELLED'],
  COMPLETED: [],
  CANCELLED: [],
}

interface OrderDetailPageProps {
  orderId: string
}

export function OrderDetailPage({ orderId }: OrderDetailPageProps) {
  const { t } = useTranslation(['orders', 'common'])
  const router = useRouter()
  const queryClient = useQueryClient()

  const [error, setError] = useState('')
  const [notes, setNotes] = useState<string | null>(null)
  const [shippingFee, setShippingFee] = useState<string | null>(null)
  const [discountPercent, setDiscountPercent] = useState<string | null>(null)
  const [vatPercent, setVatPercent] = useState<string | null>(null)

  const orderQuery = useQuery({
    queryKey: ['order', orderId],
    queryFn: () => cartApi.getOrder(orderId),
  })

  const updateStatusMutation = useMutation({
    mutationFn: (status: string) => cartApi.updateOrderStatus(orderId, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['order', orderId] })
      queryClient.invalidateQueries({ queryKey: ['orders'] })
      setError('')
    },
    onError: (err) => setError(err instanceof Error ? err.message : t('updateStatusError')),
  })

  const updateOrderMutation = useMutation({
    mutationFn: () => cartApi.updateOrder(orderId, {
      notes: notes ?? undefined,
      shippingFee: shippingFee !== null ? Number(shippingFee) : undefined,
      discountPercent: discountPercent !== null ? Number(discountPercent) : undefined,
      vatPercent: vatPercent !== null ? Number(vatPercent) : undefined,
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['order', orderId] })
      queryClient.invalidateQueries({ queryKey: ['orders'] })
      setError('')
    },
    onError: (err) => setError(err instanceof Error ? err.message : t('updateOrderError')),
  })

  const order = orderQuery.data
  if (orderQuery.isLoading) {
    return <p className="py-8 text-center text-sm text-muted-foreground">{t('common:loading')}</p>
  }
  if (!order) {
    return <p className="py-8 text-center text-sm text-destructive">{t('notFound')}</p>
  }

  const transitions = validTransitions[order.status] ?? []
  const currentNotes = notes ?? order.notes ?? ''
  const currentShipping = shippingFee ?? order.shippingFee?.toString() ?? '0'
  const currentDiscount = discountPercent ?? order.discountPercent?.toString() ?? '0'
  const currentVat = vatPercent ?? order.vatPercent?.toString() ?? '0'

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <Button variant="ghost" className="-ml-2 mb-2" onClick={() => router.navigate({ to: '/orders' })}>
            <ArrowLeft className="h-4 w-4 mr-1" />
            {t('ordersBack')}
          </Button>
          <h1 className="text-3xl font-bold tracking-tight">{t('orderDetail')}</h1>
          <p className="mt-1 text-muted-foreground">
            <code className="rounded bg-muted px-1.5 py-0.5 text-xs font-mono">{order.id}</code>
          </p>
        </div>
        <Badge variant={statusVariants[order.status] ?? 'secondary'} className="text-base px-4 py-1">
          {order.status}
        </Badge>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2 space-y-6">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">{t('orderItems')}</CardTitle>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('common:product')}</TableHead>
                    <TableHead>{t('variant')}</TableHead>
                    <TableHead className="text-right">{t('price')}</TableHead>
                    <TableHead className="text-right">{t('quantity')}</TableHead>
                    <TableHead className="text-right">{t('subtotal')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {order.items?.map((item) => {
                    const variantLabel = item.variant
                      ? Object.values(item.variant.optionValues ?? {}).join(' / ')
                      : null
                    return (
                      <TableRow key={item.id}>
                        <TableCell>
                          <p className="font-medium">{item.product?.name}</p>
                          <p className="text-xs text-muted-foreground">{item.product?.sku}</p>
                        </TableCell>
                        <TableCell>{variantLabel || '-'}</TableCell>
                        <TableCell className="text-right">{formatMoney(item.unitPrice)}</TableCell>
                        <TableCell className="text-right">{item.quantity}</TableCell>
                        <TableCell className="text-right font-medium">{formatMoney(item.subtotal)}</TableCell>
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">{t('updateOrder')}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="notes">{t('notes')}</Label>
                <textarea
                  id="notes"
                  className="flex min-h-[80px] w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                  value={currentNotes}
                  onChange={(e) => setNotes(e.target.value)}
                />
              </div>
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="shipping">{t('shippingFee')}</Label>
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
                  <Label htmlFor="discount">{t('discount')} (%)</Label>
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
                  <Label htmlFor="vat">{t('vat')} (%)</Label>
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
                {updateOrderMutation.isPending ? t('common:saving') : t('saveChanges')}
              </Button>
            </CardContent>
          </Card>
        </div>

        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">{t('customerInfo')}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2 text-sm">
              <p>
                <span className="text-muted-foreground">{t('name')}:</span>{' '}
                <span className="font-medium">
                  {order.customer?.firstName} {order.customer?.lastName}
                </span>
              </p>
              {order.customer?.email && (
                <p>
                  <span className="text-muted-foreground">{t('email')}:</span>{' '}
                  <span className="font-medium">{order.customer.email}</span>
                </p>
              )}
              {order.customer?.phone && (
                <p>
                  <span className="text-muted-foreground">{t('phone')}:</span>{' '}
                  <span className="font-medium">{order.customer.phone}</span>
                </p>
              )}
              {order.customer?.address && (
                <p>
                  <span className="text-muted-foreground">{t('address')}:</span>{' '}
                  <span className="font-medium">{order.customer.address}</span>
                </p>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">{t('paymentSummary')}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-muted-foreground">{t('subtotal')}</span>
                <span className="font-medium">{formatMoney(order.totalAmount)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">{t('shippingFee')}</span>
                <span className="font-medium">{formatMoney(order.shippingFee)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">{t('discount')}</span>
                <span className="font-medium text-success">-{formatMoney(order.discountAmount)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">{t('vat')}</span>
                <span className="font-medium">{formatMoney(order.vatAmount)}</span>
              </div>
              <div className="flex justify-between border-t pt-2 text-base font-bold">
                <span>{t('total')}</span>
                <span>{formatMoney(order.finalAmount)}</span>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">{t('updateStatus')}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2">
              {transitions.length === 0 ? (
                <p className="text-sm text-muted-foreground">{t('noStatusChanges')}</p>
              ) : (
                transitions.map((status) => (
                  <Button
                    key={status}
                    variant="outline"
                    className="w-full"
                    onClick={() => updateStatusMutation.mutate(status)}
                    disabled={updateStatusMutation.isPending}
                  >
                    {status === 'CANCELLED' ? t('cancelOrder') : t('markAs', { status })}
                  </Button>
                ))
              )}
              <p className="text-xs text-muted-foreground mt-2">{t('statusHint')}</p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">{t('timeline')}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3 text-sm">
              <div className="flex justify-between">
                <span className="text-muted-foreground">{t('created')}</span>
                <span>{formatDate(order.createdAt)}</span>
              </div>
              {order.updatedAt && order.updatedAt !== order.createdAt && (
                <div className="flex justify-between">
                  <span className="text-muted-foreground">{t('lastUpdated')}</span>
                  <span>{formatDate(order.updatedAt)}</span>
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  )
}
