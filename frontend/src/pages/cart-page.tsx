import { useEffect, useState, useMemo } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRouter } from '@tanstack/react-router'
import { AlertCircle, Minus, Plus, ShoppingCart, Trash2, Building2, Tag, ChevronRight, ShoppingBag, Info, Percent } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { cartApi } from '@/shared/api/cart'
import { catalogApi } from '@/shared/api/catalog'
import type { Customer } from '@/shared/api/cart'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'
import {
  useMeQuery,
  getMyCustomerProfileOptions,
  listVouchersOptions,
  myVouchersOptions,
  useCheckoutOrderMutation,
  listOrdersQueryKey,
  myVouchersQueryKey,
} from '@/shared/api'
import { formatUSD } from '@/shared/utils/currency'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/shared/ui/dialog'
import { Badge } from '@/shared/ui/badge'
import { Link } from '@tanstack/react-router'

interface CartItem {
  product: any
  variant?: any
  quantity: number
  priceMinor: number
}

function formatMoney(value?: number | null) {
  if (value === null || value === undefined) return '-'
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(value)
}

export function CartPage() {
  const { t } = useTranslation(['cart', 'common'])
  const router = useRouter()
  const queryClient = useQueryClient()

  // Access Control / User Profile Info
  const { data: me, isLoading: isMeLoading } = useMeQuery()
  const role = me?.role?.name
  const isCustomer = role === 'customer'

  // ==========================================
  // STAFF CART BUILDER STATE & QUERIES
  // ==========================================
  const [customerSearch, setCustomerSearch] = useState('')
  const [debouncedCustomerSearch, setDebouncedCustomerSearch] = useState('')
  const [selectedCustomerId, setSelectedCustomerId] = useState('')
  const [selectedCustomerName, setSelectedCustomerName] = useState('')

  const [productSearch, setProductSearch] = useState('')
  const [debouncedProductSearch, setDebouncedProductSearch] = useState('')
  const [selectedVariantId, setSelectedVariantId] = useState('')

  const [error, setError] = useState('')

  const [checkoutNotes, setCheckoutNotes] = useState('')
  const [shippingFeeMinor, setShippingFee] = useState('')
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
    enabled: !isCustomer && !!me,
  })

  const productsQuery = useQuery({
    queryKey: ['products', debouncedProductSearch],
    queryFn: () => catalogApi.listProducts({ search: debouncedProductSearch || undefined }),
    enabled: !isCustomer && !!me,
  })

  const activeCartQuery = useQuery({
    queryKey: ['activeCart', selectedCustomerId],
    queryFn: () => cartApi.getOrCreateActiveCart(selectedCustomerId),
    enabled: !isCustomer && !!selectedCustomerId,
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

  const staffCheckoutMutation = useMutation({
    mutationFn: () => cartApi.checkoutCart(activeCartQuery.data!.id!, {
      notes: checkoutNotes || undefined,
      shippingFeeMinor: shippingFeeMinor ? Number(shippingFeeMinor) : undefined,
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

  const cartTotal = cart?.items?.reduce((sum, item) => sum + item.unitPriceMinor * item.quantity, 0) ?? 0
  const shipping = Number(shippingFeeMinor) || 0
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
    staffCheckoutMutation.mutate()
  }

  // ==========================================
  // CUSTOMER SHOPPING CART LOGIC
  // ==========================================
  const [buyerCart, setBuyerCart] = useState<CartItem[]>(() => {
    try {
      const saved = localStorage.getItem('buyer_cart')
      return saved ? JSON.parse(saved) : []
    } catch {
      return []
    }
  })

  useEffect(() => {
    if (isCustomer) {
      localStorage.setItem('buyer_cart', JSON.stringify(buyerCart))
    }
  }, [buyerCart, isCustomer])

  const { data: myCustomerProfile } = useQuery({
    ...getMyCustomerProfileOptions(),
    enabled: isCustomer && !!me,
  })

  const customerId = myCustomerProfile?.id ?? ''

  // Get active vouchers for customer
  const { data: allVouchersResponse } = useQuery({
    ...listVouchersOptions(),
    enabled: isCustomer,
  })
  const customerVouchers = useMemo(() => {
    if (!allVouchersResponse) return []
    return (allVouchersResponse as any[]).filter((v: any) => v.active)
  }, [allVouchersResponse])

  // Get collected vouchers for customer
  const { data: collectedVouchers } = useQuery({
    ...myVouchersOptions(customerId ? { query: { customerId } } : undefined),
    enabled: isCustomer && !!customerId,
  })
  const collectedVoucherList = useMemo(() => {
    if (!collectedVouchers) return []
    return (collectedVouchers as any[]).filter((cv: any) => cv.voucher?.active)
  }, [collectedVouchers])

  // State for customer checkout flow
  const [showCheckout, setShowCheckout] = useState(false)
  const [selectedVoucherId, setSelectedVoucherId] = useState<string>('')
  const [customerName, setCustomerName] = useState('')
  const [customerEmail, setCustomerEmail] = useState('')
  const [customerPhone, setCustomerPhone] = useState('')
  const [customerAddress, setCustomerAddress] = useState('')
  const [shippingNote, setShippingNote] = useState('')

  // Pre-populate customer checkout info when profile loads
  useEffect(() => {
    if (isCustomer && myCustomerProfile) {
      setCustomerName(`${myCustomerProfile.firstName ?? ''} ${myCustomerProfile.lastName ?? ''}`.trim())
      setCustomerEmail(myCustomerProfile.email ?? '')
      setCustomerPhone(myCustomerProfile.phone ?? '')
      setCustomerAddress(myCustomerProfile.address ?? '')
    }
  }, [isCustomer, myCustomerProfile])

  // Checkout Mutation for Customer
  const customerCheckoutMutation = useCheckoutOrderMutation({
    onSuccess: (data: any) => {
      queryClient.invalidateQueries({ queryKey: listOrdersQueryKey() })
      queryClient.invalidateQueries({ queryKey: myVouchersQueryKey() })
      setBuyerCart([])
      localStorage.setItem('buyer_cart', '[]')
      setShowCheckout(false)
      setSelectedVoucherId('')
      const result = data as { id?: string; data?: { id?: string } }
      const orderId = result.data?.id ?? result.id
      if (orderId) {
        router.navigate({ to: '/orders/payment', search: { orderId } })
      }
    },
    onError: (err: any) => {
      setError(err?.body?.message || err?.message || 'Checkout failed')
    },
  })

  // Customer calculation variables
  const buyerCartTotal = useMemo(() => {
    return buyerCart.reduce((sum, item) => sum + item.priceMinor * item.quantity, 0)
  }, [buyerCart])

  const selectedVoucher = useMemo(() => {
    if (!selectedVoucherId || !collectedVoucherList.length) return null
    return collectedVoucherList.find((cv: any) => cv.voucherId === selectedVoucherId)?.voucher ?? null
  }, [selectedVoucherId, collectedVoucherList])

  const buyerDiscountAmount = useMemo(() => {
    if (!selectedVoucher) return 0
    const v = selectedVoucher
    const minOrderVal = Number(v.minOrderValueMinor)
    if (buyerCartTotal < minOrderVal) return 0
    if (v.discountType === 'PERCENTAGE') {
      const disc = buyerCartTotal * (Number(v.discountValue) / 100)
      return v.maxDiscountAmountMinor ? Math.min(disc, Number(v.maxDiscountAmountMinor)) : disc
    }
    return Number(v.discountValue)
  }, [selectedVoucher, buyerCartTotal])

  const FREE_SHIPPING_THRESHOLD = 100 
  const FLAT_SHIPPING_FEE = 10
  const VAT_RATE = 0.1 

  const buyerShippingFeeMinor = buyerCartTotal > 0 && (buyerCartTotal - buyerDiscountAmount) < FREE_SHIPPING_THRESHOLD ? FLAT_SHIPPING_FEE : 0
  const buyerTaxAmountMinor = Math.max(0, (buyerCartTotal - buyerDiscountAmount) * VAT_RATE)
  const buyerFinalAmountMinor = Math.max(0, buyerCartTotal - buyerDiscountAmount + buyerShippingFeeMinor + buyerTaxAmountMinor)

  // Handlers for customer cart
  const updateBuyerQty = (productId: string, variantId: string | undefined, delta: number) => {
    setBuyerCart(prev => prev.map(item => {
      if (item.product.id !== productId || item.variant?.id !== variantId) return item
      const newQty = item.quantity + delta
      if (newQty <= 0) return null
      
      const v = item.variant
      const stock = v ? v.stockQuantity : (item.product.stockQuantity ?? 0)
      const reserved = v ? v.reservedQuantity : (item.product.reservedQuantity ?? 0)
      const avail = Math.max(0, stock - reserved)
      if (delta > 0 && item.quantity >= avail) {
        setError(`Chỉ còn ${avail} sản phẩm trong kho`)
        return item
      }
      setError('')
      return { ...item, quantity: newQty }
    }).filter(Boolean) as CartItem[])
  }

  const removeBuyerItem = (productId: string, variantId: string | undefined) => {
    setBuyerCart(prev => prev.filter(item => item.product.id !== productId || item.variant?.id !== variantId))
  }

  const handlePlaceOrder = () => {
    if (!customerName.trim() || !customerPhone.trim() || !customerAddress.trim()) {
      setError('Vui lòng điền đầy đủ tên, số điện thoại và địa chỉ giao hàng')
      return
    }
    customerCheckoutMutation.mutate({
      body: {
        customerId,
        voucherCode: selectedVoucher?.code ?? undefined,
        shippingName: customerName.trim(),
        shippingPhone: customerPhone.trim(),
        shippingAddress: customerAddress.trim(),
        shippingNote: shippingNote.trim() || undefined,
        items: buyerCart.map(item => ({
          productId: item.product.id,
          productVariantId: item.variant?.id ?? null,
          quantity: item.quantity,
        })) as any,
      }
    })
  }

  // Loaders
  if (isMeLoading) {
    return <div className="text-center py-10 text-muted-foreground">Loading access control...</div>
  }

  // ==========================================
  // CUSTOMER (BUYER) VIEW
  // ==========================================
  if (isCustomer) {
    // Group items by store
    const groupedCart: Record<string, { business?: any; items: CartItem[]; subtotal: number }> = {}
    buyerCart.forEach(item => {
      const bizId = item.product.business?.id || 'default'
      const bizName = item.product.business?.name || 'Cửa hàng hệ thống'
      if (!groupedCart[bizId]) {
        groupedCart[bizId] = {
          business: item.product.business || { id: 'default', name: 'Cửa hàng hệ thống' },
          items: [],
          subtotal: 0,
        }
      }
      groupedCart[bizId].items.push(item)
      groupedCart[bizId].subtotal += item.priceMinor * item.quantity
    })

    return (
      <div className="mx-auto max-w-6xl space-y-6 px-4 md:px-0">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight bg-gradient-to-r from-primary to-violet-600 bg-clip-text text-transparent">
            Giỏ hàng của tôi
          </h1>
          <p className="mt-1 text-muted-foreground text-sm">
            Xem và quản lý các sản phẩm trong giỏ hàng của bạn
          </p>
        </div>

        {buyerCart.length === 0 ? (
          <Card className="flex flex-col items-center justify-center p-12 text-center border-dashed">
            <div className="h-16 w-16 bg-muted/50 rounded-full flex items-center justify-center text-muted-foreground mb-4">
              <ShoppingCart className="h-8 w-8" />
            </div>
            <CardTitle className="text-xl font-bold mb-1">Giỏ hàng của bạn trống</CardTitle>
            <p className="text-muted-foreground text-sm max-w-sm mb-6">
              Chưa có sản phẩm nào trong giỏ hàng. Hãy khám phá và mua sắm ngay!
            </p>
            <Link to="/shop">
              <Button size="lg" className="shadow-md hover:shadow-lg transition-all cursor-pointer">
                Quay lại Cửa hàng
              </Button>
            </Link>
          </Card>
        ) : (
          <div className="grid gap-6 lg:grid-cols-[1fr_360px] items-start">
            {/* Left: Cart items list grouped by vendor */}
            <div className="space-y-4">
              {Object.entries(groupedCart).map(([bizId, group]) => (
                <Card key={bizId} className="shadow-sm overflow-hidden border border-border">
                  <div className="bg-muted/30 px-4 py-3 border-b border-border flex items-center gap-2">
                    <Building2 className="h-4.5 w-4.5 text-primary shrink-0" />
                    <span className="font-bold text-sm text-foreground">{group.business.name}</span>
                    <Badge variant="outline" className="ml-2 bg-background border-primary/20 text-primary text-xs px-2 py-0">
                      Cửa hàng
                    </Badge>
                  </div>
                  <CardContent className="p-0 divide-y divide-border">
                    {group.items.map((item, idx) => {
                      const variantLabel = item.variant
                        ? Object.values(item.variant.optionValues ?? {}).join(' / ')
                        : null
                      return (
                        <div key={idx} className="p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                          <div className="flex gap-3 min-w-0">
                            <div className="h-14 w-14 rounded-lg bg-gradient-to-br from-violet-500/10 to-primary/10 border border-border flex items-center justify-center shrink-0">
                              <ShoppingBag className="h-6 w-6 text-primary" />
                            </div>
                            <div className="min-w-0">
                              <h4 className="font-semibold text-sm text-foreground truncate">{item.product.name}</h4>
                              <p className="text-xs text-muted-foreground mt-0.5 font-mono">{item.product.sku}</p>
                              {variantLabel && (
                                <Badge variant="secondary" className="mt-1 text-[10px] font-medium px-2 py-0.5">
                                  {variantLabel}
                                </Badge>
                              )}
                            </div>
                          </div>

                          <div className="flex items-center justify-between sm:justify-end gap-6 shrink-0">
                            {/* Price info */}
                            <div className="text-right sm:w-24">
                              <div className="text-[10px] text-muted-foreground">Đơn giá</div>
                              <div className="font-semibold text-sm text-foreground">{formatUSD(item.priceMinor)}</div>
                            </div>

                            {/* Quantity buttons */}
                            <div className="flex items-center border border-input rounded-lg h-9 bg-background px-1 shrink-0">
                              <Button
                                size="icon"
                                variant="ghost"
                                className="h-7 w-7 rounded-md cursor-pointer hover:bg-muted"
                                onClick={() => updateBuyerQty(item.product.id, item.variant?.id, -1)}
                              >
                                <Minus className="h-3 w-3" />
                              </Button>
                              <span className="w-8 text-center text-sm font-semibold text-foreground">
                                {item.quantity}
                              </span>
                              <Button
                                size="icon"
                                variant="ghost"
                                className="h-7 w-7 rounded-md cursor-pointer hover:bg-muted"
                                onClick={() => updateBuyerQty(item.product.id, item.variant?.id, 1)}
                              >
                                <Plus className="h-3 w-3" />
                              </Button>
                            </div>

                            {/* Subtotal */}
                            <div className="text-right sm:w-28">
                              <div className="text-[10px] text-muted-foreground">Thành tiền</div>
                              <div className="font-bold text-sm text-primary">{formatUSD(item.priceMinor * item.quantity)}</div>
                            </div>

                            {/* Trash action */}
                            <Button
                              size="icon"
                              variant="ghost"
                              className="h-8 w-8 text-muted-foreground hover:text-destructive hover:bg-destructive/10 rounded-lg cursor-pointer"
                              onClick={() => removeBuyerItem(item.product.id, item.variant?.id)}
                            >
                              <Trash2 className="h-4.5 w-4.5" />
                            </Button>
                          </div>
                        </div>
                      )
                    })}
                  </CardContent>
                </Card>
              ))}
            </div>

            {/* Right: Summary sidebar */}
            <div className="space-y-4 lg:sticky lg:top-4">
              {/* Voucher selectors */}
              <Card className="shadow-sm border border-border">
                <CardHeader className="pb-3">
                  <CardTitle className="text-base flex items-center gap-2">
                    <Tag className="h-4 w-4 text-primary" />
                    Khuyến mãi & Vouchers
                  </CardTitle>
                </CardHeader>
                <CardContent className="space-y-3">
                  {collectedVoucherList.length === 0 ? (
                    <p className="text-xs text-muted-foreground text-center py-2">
                      Không tìm thấy voucher đã thu thập. Hãy thu thập vouchers từ trang Cửa hàng!
                    </p>
                  ) : (
                    <div className="space-y-2">
                      <Label htmlFor="voucherSelect" className="text-xs text-muted-foreground font-medium">Chọn voucher áp dụng</Label>
                      <select
                        id="voucherSelect"
                        className="w-full rounded-md border border-input bg-transparent px-3 py-1.5 text-xs shadow-sm focus:outline-none focus:ring-1 focus:ring-ring bg-popover text-foreground"
                        value={selectedVoucherId}
                        onChange={(e) => setSelectedVoucherId(e.target.value)}
                      >
                        <option value="">Không sử dụng voucher</option>
                        {collectedVoucherList.map((cv: any) => {
                          const v = cv.voucher
                          const isValid = buyerCartTotal >= Number(v.minOrderValueMinor)
                          const discValStr = v.discountType === 'PERCENTAGE' ? `${v.discountValue}%` : formatUSD(Number(v.discountValue))
                          return (
                            <option key={v.id} value={v.id} disabled={!isValid}>
                              {v.code} ({discValStr} OFF) {!isValid ? ` - Đơn tối thiểu: ${formatUSD(Number(v.minOrderValueMinor))}` : ''}
                            </option>
                          )
                        })}
                      </select>
                      {selectedVoucher && (
                        <div className="rounded-lg bg-emerald-500/10 border border-emerald-500/20 p-2.5 flex items-start gap-2 mt-2">
                          <Percent className="h-4 w-4 text-emerald-500 shrink-0 mt-0.5" />
                          <div className="text-xs">
                            <span className="font-bold text-emerald-600 dark:text-emerald-400">
                              Đã áp dụng: {selectedVoucher.code}
                            </span>
                            <p className="text-muted-foreground mt-0.5">
                              Giảm {selectedVoucher.discountType === 'PERCENTAGE' ? `${selectedVoucher.discountValue}%` : formatUSD(Number(selectedVoucher.discountValue))} (Tối đa: {selectedVoucher.maxDiscountAmountMinor ? formatUSD(Number(selectedVoucher.maxDiscountAmountMinor)) : 'Không giới hạn'})
                            </p>
                          </div>
                        </div>
                      )}
                    </div>
                  )}
                </CardContent>
              </Card>

              {/* Order total cards */}
              <Card className="shadow-sm border border-border">
                <CardHeader className="pb-3">
                  <CardTitle className="text-base">Tóm tắt đơn hàng</CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div className="space-y-2.5 text-sm">
                    <div className="flex justify-between">
                      <span className="text-muted-foreground">Tổng tiền hàng</span>
                      <span className="font-medium text-foreground">{formatUSD(buyerCartTotal)}</span>
                    </div>

                    {buyerDiscountAmount > 0 && (
                      <div className="flex justify-between text-emerald-600 dark:text-emerald-400 font-medium">
                        <span>Chiết khấu Voucher</span>
                        <span>-{formatUSD(buyerDiscountAmount)}</span>
                      </div>
                    )}

                    <div className="flex justify-between">
                      <span className="text-muted-foreground">Phí ship</span>
                      <span>
                        {buyerShippingFeeMinor > 0 ? (
                          formatUSD(buyerShippingFeeMinor)
                        ) : (
                          <span className="text-emerald-500 font-semibold">Miễn phí</span>
                        )}
                      </span>
                    </div>

                    <div className="flex justify-between">
                      <span className="text-muted-foreground">VAT (10%)</span>
                      <span className="font-medium text-foreground">{formatUSD(buyerTaxAmountMinor)}</span>
                    </div>

                    {buyerShippingFeeMinor > 0 && (
                      <div className="rounded-lg bg-amber-500/10 border border-amber-500/20 p-2 flex items-start gap-1.5 text-[11px] text-amber-600 dark:text-amber-400 mt-1">
                        <Info className="h-3.5 w-3.5 shrink-0 mt-0.5" />
                        <span>Mua thêm <strong>{formatUSD(FREE_SHIPPING_THRESHOLD - (buyerCartTotal - buyerDiscountAmount))}</strong> để được miễn phí vận chuyển!</span>
                      </div>
                    )}

                    <div className="border-t border-border pt-3 mt-2 flex justify-between text-base font-bold">
                      <span>Tổng cộng</span>
                      <span className="text-primary text-lg">
                        {formatUSD(buyerFinalAmountMinor)}
                      </span>
                    </div>
                  </div>

                  {error && (
                    <div className="flex items-start gap-2 rounded-lg bg-destructive/10 p-3 text-xs text-destructive">
                      <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
                      <span>{error}</span>
                    </div>
                  )}

                  <Button
                    className="w-full cursor-pointer shadow-md hover:shadow-lg transition-all"
                    size="lg"
                    onClick={() => setShowCheckout(true)}
                  >
                    Tiến hành thanh toán
                  </Button>
                </CardContent>
              </Card>
            </div>
          </div>
        )}

        {/* Customer checkout dialog */}
        <Dialog open={showCheckout} onOpenChange={setShowCheckout}>
          <DialogContent className="max-w-md max-h-[90vh] overflow-y-auto">
            <DialogHeader>
              <DialogTitle className="text-lg font-bold">Thông tin nhận hàng</DialogTitle>
              <DialogDescription>
                Vui lòng nhập chính xác thông tin nhận hàng bên dưới.
              </DialogDescription>
            </DialogHeader>

            <div className="space-y-4 py-2 text-sm">
              <div className="space-y-1.5">
                <Label htmlFor="shippingName">Tên người nhận</Label>
                <Input
                  id="shippingName"
                  value={customerName}
                  onChange={(e) => setCustomerName(e.target.value)}
                  placeholder="Họ và tên người nhận"
                />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="shippingPhone">Số điện thoại liên lạc</Label>
                <Input
                  id="shippingPhone"
                  value={customerPhone}
                  onChange={(e) => setCustomerPhone(e.target.value)}
                  placeholder="Nhập số điện thoại"
                />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="shippingAddress">Địa chỉ nhận hàng</Label>
                <Input
                  id="shippingAddress"
                  value={customerAddress}
                  onChange={(e) => setCustomerAddress(e.target.value)}
                  placeholder="Số nhà, tên đường, phường/xã, quận/huyện..."
                />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="shippingNote">Ghi chú giao hàng</Label>
                <textarea
                  id="shippingNote"
                  className="flex min-h-[60px] w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                  value={shippingNote}
                  onChange={(e) => setShippingNote(e.target.value)}
                  placeholder="Ghi chú thêm cho shipper (ví dụ: giao giờ hành chính, gọi trước khi giao...)"
                />
              </div>

              <div className="border-t border-border pt-3 mt-4 space-y-1">
                <div className="flex justify-between text-xs text-muted-foreground">
                  <span>Tạm tính</span>
                  <span>{formatUSD(buyerCartTotal)}</span>
                </div>
                {buyerDiscountAmount > 0 && (
                  <div className="flex justify-between text-xs text-emerald-600 dark:text-emerald-400">
                    <span>Voucher giảm giá</span>
                    <span>-{formatUSD(buyerDiscountAmount)}</span>
                  </div>
                )}
                <div className="flex justify-between text-xs text-muted-foreground">
                  <span>Phí ship</span>
                  <span>{buyerShippingFeeMinor > 0 ? formatUSD(buyerShippingFeeMinor) : 'Free'}</span>
                </div>
                <div className="flex justify-between text-xs text-muted-foreground">
                  <span>Thuế VAT (10%)</span>
                  <span>{formatUSD(buyerTaxAmountMinor)}</span>
                </div>
                <div className="flex justify-between font-bold text-sm text-foreground pt-1.5">
                  <span>Tổng tiền thanh toán</span>
                  <span className="text-primary">{formatUSD(buyerFinalAmountMinor)}</span>
                </div>
              </div>
            </div>

            <DialogFooter className="gap-2 sm:gap-0">
              <Button variant="outline" onClick={() => setShowCheckout(false)} className="cursor-pointer">
                Hủy bỏ
              </Button>
              <Button
                onClick={handlePlaceOrder}
                disabled={customerCheckoutMutation.isPending}
                className="cursor-pointer shadow-sm"
              >
                {customerCheckoutMutation.isPending ? 'Đang gửi đơn hàng...' : 'Xác nhận đặt hàng'}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>
    )
  }

  // ==========================================
  // STAFF (ADMIN / OPERATOR) CART BUILDER VIEW
  // ==========================================
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
                      const priceMinor = product.salePriceMinor ?? product.basePriceMinor
                      return (
                        <div key={product.id} className="flex items-center justify-between rounded-md border px-3 py-2">
                          <div className="min-w-0 flex-1">
                            <p className="text-sm font-medium truncate">{product.name}</p>
                            <p className="text-xs text-muted-foreground">
                              {product.sku} — {formatMoney(priceMinor)}
                            </p>
                            {product.variants && product.variants.length > 0 && (
                              <div className="mt-1 flex flex-wrap gap-1">
                                {product.variants.map((v) => (
                                  <button
                                    key={v.id}
                                    type="button"
                                    className="inline-flex items-center rounded-full border px-2 py-0.5 text-[10px] hover:bg-muted transition-colors"
                                    onClick={() => handleAddVariant(product.id!, v.id!)}
                                    title={`${Object.values(v.optionValues).join(' / ')} — ${formatMoney(v.priceMinor)}`}
                                  >
                                    {Object.values(v.optionValues).join(' / ')} {formatMoney(v.priceMinor)}
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
                      <TableHead className="text-right">{t('priceMinor')}</TableHead>
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
                          <TableCell className="text-right">{formatMoney(item.unitPriceMinor)}</TableCell>
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
                            {formatMoney(item.unitPriceMinor * item.quantity)}
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
                  <Label htmlFor="shipping" className="text-xs text-muted-foreground">{t('shippingFeeMinor')}</Label>
                  <Input
                    id="shipping"
                    type="number"
                    min="0"
                    step="0.01"
                    value={shippingFeeMinor}
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
                    <span className="text-muted-foreground">{t('shippingFeeMinor')}</span>
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
                disabled={!cart?.items?.length || staffCheckoutMutation.isPending}
                onClick={handleCheckout}
              >
                <ShoppingCart className="h-4 w-4 mr-2" />
                {staffCheckoutMutation.isPending ? t('processing') : t('checkout')}
              </Button>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  )
}
