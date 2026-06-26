import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRouter } from '@tanstack/react-router'
import { AlertCircle, Minus, Plus, ShoppingCart, Trash2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { cartApi } from '@/shared/api/cart'
import { catalogApi } from '@/shared/api/catalog'
import type { Customer } from '@/shared/api/cart'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'

function formatMoney(value?: number | null) {
  if (value === null || value === undefined) return '-'
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(value)
}

export function CartPage() {
  const { t } = useTranslation(['cart', 'common'])
  const router = useRouter()
  const queryClient = useQueryClient()

  const [customerSearch, setCustomerSearch] = useState('')
  const [debouncedCustomerSearch, setDebouncedCustomerSearch] = useState('')
  const [selectedCustomerId, setSelectedCustomerId] = useState('')
  const [selectedCustomerName, setSelectedCustomerName] = useState('')

  const [productSearch, setProductSearch] = useState('')
  const [debouncedProductSearch, setDebouncedProductSearch] = useState('')
  const [selectedVariantId, setSelectedVariantId] = useState('')

  const [error, setError] = useState('')

  const [checkoutNotes, setCheckoutNotes] = useState('')
  const [shippingFee, setShippingFee] = useState('')
  const [discountPercent, setDiscountPercent] = useState('')
  const [vatPercent, setVatPercent] = useState('')

  const resetCheckoutForm = () => {
    setCheckoutNotes('')
    setShippingFee('')
    setDiscountPercent('')
    setVatPercent('')
    setError('')
  }

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedCustomerSearch(customerSearch), 300)
    return () => clearTimeout(timer)
  }, [customerSearch])

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedProductSearch(productSearch), 300)
    return () => clearTimeout(timer)
  }, [productSearch])

  const customersQuery = useQuery({
    queryKey: ['customers', debouncedCustomerSearch],
    queryFn: () => cartApi.listCustomers(debouncedCustomerSearch || undefined),
  })

  const productsQuery = useQuery({
    queryKey: ['products', debouncedProductSearch],
    queryFn: () => catalogApi.listProducts({ search: debouncedProductSearch || undefined }),
  })

  const activeCartQuery = useQuery({
    queryKey: ['activeCart', selectedCustomerId],
    queryFn: () => cartApi.getOrCreateActiveCart(selectedCustomerId),
    enabled: !!selectedCustomerId,
  })

  const addItemMutation = useMutation({
    mutationFn: ({ productId, variantId, quantity }: { productId: string; variantId?: string; quantity?: number }) =>
      cartApi.addCartItem(activeCartQuery.data!.id!, productId, quantity, variantId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['activeCart', selectedCustomerId] })
      setError('')
    },
    onError: (err) => setError(err instanceof Error ? err.message : t('addProductError')),
  })

  const updateItemMutation = useMutation({
    mutationFn: ({ itemId, quantity }: { itemId: string; quantity: number }) =>
      cartApi.updateCartItem(activeCartQuery.data!.id!, itemId, quantity),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['activeCart', selectedCustomerId] })
      setError('')
    },
    onError: (err) => setError(err instanceof Error ? err.message : t('updateItemError')),
  })

  const removeItemMutation = useMutation({
    mutationFn: (itemId: string) => cartApi.removeCartItem(activeCartQuery.data!.id!, itemId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['activeCart', selectedCustomerId] })
      setError('')
    },
    onError: (err) => setError(err instanceof Error ? err.message : t('removeItemError')),
  })

  const checkoutMutation = useMutation({
    mutationFn: () => cartApi.checkoutCart(activeCartQuery.data!.id!, {
      notes: checkoutNotes || undefined,
      shippingFee: shippingFee ? Number(shippingFee) : undefined,
      discountPercent: discountPercent ? Number(discountPercent) : undefined,
      vatPercent: vatPercent ? Number(vatPercent) : undefined,
    }),
    onSuccess: (order) => {
      queryClient.invalidateQueries({ queryKey: ['activeCart', selectedCustomerId] })
      queryClient.invalidateQueries({ queryKey: ['orders'] })
      resetCheckoutForm()
      router.navigate({ to: '/orders/$id', params: { id: order.id! } })
    },
    onError: (err) => setError(err instanceof Error ? err.message : t('checkoutError')),
  })

  const cart = activeCartQuery.data
  const customers = customersQuery.data ?? []
  const products = productsQuery.data ?? []

  const cartTotal = cart?.items?.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0) ?? 0
  const shipping = Number(shippingFee) || 0
  const discountPct = Math.min(100, Math.max(0, Number(discountPercent) || 0))
  const vatPct = Math.min(100, Math.max(0, Number(vatPercent) || 0))
  const computedDiscount = cartTotal * (discountPct / 100)
  const afterDiscount = cartTotal - computedDiscount
  const computedVat = afterDiscount * (vatPct / 100)
  const finalTotal = afterDiscount + shipping + computedVat

  const handleSelectCustomer = (customer: Customer) => {
    setSelectedCustomerId(customer.id!)
    setSelectedCustomerName(`${customer.firstName ?? ''} ${customer.lastName ?? ''}`.trim() || customer.email || '')
  }

  const handleAddProduct = (productId: string) => {
    addItemMutation.mutate({ productId, variantId: selectedVariantId || undefined })
    setSelectedVariantId('')
    setProductSearch('')
  }

  const handleAddVariant = (productId: string, variantId: string) => {
    addItemMutation.mutate({ productId, variantId })
    setSelectedVariantId('')
    setProductSearch('')
  }

  const handleCheckout = () => {
    if (!cart?.items?.length) return
    checkoutMutation.mutate()
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <div className="flex items-center gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('shoppingCart')}</h1>
          <p className="mt-1 text-muted-foreground">{t('cartDescription')}</p>
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">{t('selectCustomer')}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <Input
                placeholder={t('searchCustomer')}
                value={customerSearch}
                onChange={(e) => setCustomerSearch(e.target.value)}
              />
              {selectedCustomerName && (
                <div className="flex items-center justify-between rounded-md border bg-muted/20 px-3 py-2">
                  <span className="font-medium">{selectedCustomerName}</span>
                  <Button variant="ghost" size="sm" onClick={() => { setSelectedCustomerId(''); setSelectedCustomerName('') }}>
                    {t('common:change')}
                  </Button>
                </div>
              )}
              {!selectedCustomerName && (
                <div className="max-h-[200px] space-y-1 overflow-y-auto">
                  {customersQuery.isLoading ? (
                    <p className="text-sm text-muted-foreground">{t('common:loading')}</p>
                  ) : customers.length === 0 ? (
                    <p className="text-sm text-muted-foreground">{t('noCustomers')}</p>
                  ) : (
                    customers.map((customer) => (
                      <button
                        key={customer.id}
                        type="button"
                        className="w-full rounded-md px-3 py-2 text-left text-sm hover:bg-muted transition-colors"
                        onClick={() => handleSelectCustomer(customer)}
                      >
                        <span className="font-medium">
                          {customer.firstName} {customer.lastName}
                        </span>
                        {customer.email && (
                          <span className="ml-2 text-xs text-muted-foreground">{customer.email}</span>
                        )}
                      </button>
                    ))
                  )}
                </div>
              )}
            </CardContent>
          </Card>

          {selectedCustomerId && (
            <Card>
              <CardHeader>
                <CardTitle className="text-base">{t('addProducts')}</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <Input
                  placeholder={t('searchProducts')}
                  value={productSearch}
                  onChange={(e) => setProductSearch(e.target.value)}
                />
                <div className="max-h-[300px] space-y-1 overflow-y-auto">
                  {productsQuery.isLoading ? (
                    <p className="text-sm text-muted-foreground">{t('common:loading')}</p>
                  ) : products.length === 0 ? (
                    <p className="text-sm text-muted-foreground">{t('noProducts')}</p>
                  ) : (
                    products.filter((p) => p.active).map((product) => {
                      const price = product.salePrice ?? product.basePrice
                      return (
                        <div key={product.id} className="flex items-center justify-between rounded-md border px-3 py-2">
                          <div className="min-w-0 flex-1">
                            <p className="text-sm font-medium truncate">{product.name}</p>
                            <p className="text-xs text-muted-foreground">
                              {product.sku} — {formatMoney(price)}
                            </p>
                            {product.variants && product.variants.length > 0 && (
                              <div className="mt-1 flex flex-wrap gap-1">
                                {product.variants.map((v) => (
                                  <button
                                    key={v.id}
                                    type="button"
                                    className="inline-flex items-center rounded-full border px-2 py-0.5 text-[10px] hover:bg-muted transition-colors"
                                    onClick={() => handleAddVariant(product.id!, v.id!)}
                                    title={`${Object.values(v.optionValues).join(' / ')} — ${formatMoney(v.price)}`}
                                  >
                                    {Object.values(v.optionValues).join(' / ')} {formatMoney(v.price)}
                                  </button>
                                ))}
                              </div>
                            )}
                          </div>
                          <Button
                            size="sm"
                            variant="outline"
                            className="ml-2 shrink-0"
                            onClick={() => handleAddProduct(product.id!)}
                          >
                            <Plus className="h-3 w-3" />
                          </Button>
                        </div>
                      )
                    })
                  )}
                </div>
              </CardContent>
            </Card>
          )}

          {cart && cart.items.length > 0 && (
            <Card>
              <CardHeader>
                <CardTitle className="text-base">{t('cartItems')}</CardTitle>
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
                      <TableHead></TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {cart.items.map((item) => {
                      const variantLabel = item.variant
                        ? Object.values(item.variant.optionValues ?? {}).join(' / ')
                        : null
                      return (
                        <TableRow key={item.id}>
                          <TableCell>
                            <p className="font-medium">{item.product.name}</p>
                            <p className="text-xs text-muted-foreground">{item.product.sku}</p>
                          </TableCell>
                          <TableCell>{variantLabel || '-'}</TableCell>
                          <TableCell className="text-right">{formatMoney(item.unitPrice)}</TableCell>
                          <TableCell className="text-right">
                            <div className="inline-flex items-center gap-1">
                              <Button
                                size="icon"
                                variant="ghost"
                                className="h-7 w-7"
                                onClick={() => updateItemMutation.mutate({ itemId: item.id!, quantity: item.quantity - 1 })}
                              >
                                <Minus className="h-3 w-3" />
                              </Button>
                              <span className="w-8 text-center text-sm font-medium">{item.quantity}</span>
                              <Button
                                size="icon"
                                variant="ghost"
                                className="h-7 w-7"
                                onClick={() => updateItemMutation.mutate({ itemId: item.id!, quantity: item.quantity + 1 })}
                              >
                                <Plus className="h-3 w-3" />
                              </Button>
                            </div>
                          </TableCell>
                          <TableCell className="text-right font-medium">
                            {formatMoney(item.unitPrice * item.quantity)}
                          </TableCell>
                          <TableCell>
                            <Button
                              size="icon"
                              variant="ghost"
                              className="h-7 w-7 text-destructive"
                              onClick={() => removeItemMutation.mutate(item.id!)}
                            >
                              <Trash2 className="h-3 w-3" />
                            </Button>
                          </TableCell>
                        </TableRow>
                      )
                    })}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          )}
        </div>

        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">{t('orderSummary')}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-2">
                <div className="flex justify-between text-sm">
                  <span className="text-muted-foreground">{t('subtotal')}</span>
                  <span className="font-medium">{formatMoney(cartTotal)}</span>
                </div>

                <div className="space-y-1">
                  <Label htmlFor="shipping" className="text-xs text-muted-foreground">{t('shippingFee')}</Label>
                  <Input
                    id="shipping"
                    type="number"
                    min="0"
                    step="0.01"
                    value={shippingFee}
                    onChange={(e) => setShippingFee(e.target.value)}
                    placeholder="0.00"
                  />
                </div>

                <div className="space-y-1">
                  <Label htmlFor="discount" className="text-xs text-muted-foreground">{t('discount')} (%)</Label>
                  <Input
                    id="discount"
                    type="number"
                    min="0"
                    max="100"
                    step="0.1"
                    value={discountPercent}
                    onChange={(e) => {
                      const val = e.target.value
                      if (val === '' || (Number(val) >= 0 && Number(val) <= 100)) setDiscountPercent(val)
                    }}
                    placeholder="0"
                  />
                </div>

                <div className="space-y-1">
                  <Label htmlFor="vat" className="text-xs text-muted-foreground">{t('vat')} (%)</Label>
                  <Input
                    id="vat"
                    type="number"
                    min="0"
                    max="100"
                    step="0.1"
                    value={vatPercent}
                    onChange={(e) => {
                      const val = e.target.value
                      if (val === '' || (Number(val) >= 0 && Number(val) <= 100)) setVatPercent(val)
                    }}
                    placeholder="0"
                  />
                </div>

                <div className="border-t pt-2 space-y-1">
                  <div className="flex justify-between text-sm">
                    <span className="text-muted-foreground">{t('shippingFee')}</span>
                    <span className="font-medium">{formatMoney(shipping)}</span>
                  </div>
                  <div className="flex justify-between text-sm">
                    <span className="text-muted-foreground">{t('discount')}</span>
                    <span className="font-medium text-success">-{formatMoney(computedDiscount)}</span>
                  </div>
                  <div className="flex justify-between text-sm">
                    <span className="text-muted-foreground">{t('vat')}</span>
                    <span className="font-medium">{formatMoney(computedVat)}</span>
                  </div>
                </div>

                <div className="space-y-1">
                  <Label htmlFor="notes" className="text-xs text-muted-foreground">{t('notes')}</Label>
                  <textarea
                    id="notes"
                    className="flex min-h-[60px] w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                    value={checkoutNotes}
                    onChange={(e) => setCheckoutNotes(e.target.value)}
                    placeholder={t('notesPlaceholder')}
                  />
                </div>

                <div className="border-t pt-2">
                  <div className="flex justify-between text-base font-bold">
                    <span>{t('total')}</span>
                    <span>{formatMoney(Math.max(0, finalTotal))}</span>
                  </div>
                </div>

                {error && (
                  <div className="flex items-start gap-2 rounded-md bg-destructive/10 p-3 text-sm text-destructive">
                    <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
                    <span>{error}</span>
                  </div>
                )}
              </div>

              <Button
                className="w-full"
                size="lg"
                disabled={!cart?.items?.length || checkoutMutation.isPending}
                onClick={handleCheckout}
              >
                <ShoppingCart className="h-4 w-4 mr-2" />
                {checkoutMutation.isPending ? t('processing') : t('checkout')}
              </Button>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  )
}
