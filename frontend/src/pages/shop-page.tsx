import { useState, useMemo, useEffect } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { catalogApi } from '@/shared/api/catalog'
import {
  useListProductsQuery,
  useCheckoutOrderMutation,
  useMeQuery,
  useGetMyCustomerProfileQuery,
  useListVouchersQuery,
  useMyVouchersQuery,
  useCollectMutation,
  myVouchersQueryKey,
  listCustomersOptions,
  listVouchersOptions,
  myVouchersOptions,
  getMyCustomerProfileOptions
} from '@/shared/api'
import { formatUSD } from '@/shared/utils/currency'
import { Button } from '@/shared/ui/button'
import { Card, CardContent } from '@/shared/ui/card'
import { Badge } from '@/shared/ui/badge'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/shared/ui/dialog'
import {
  ShoppingCart, Package, Tag, Plus, Minus, X, Search, Flame,
  ShoppingBag, ChevronRight, AlertCircle, Gift, Check,
} from 'lucide-react'

const FLAT_SHIPPING_FEE = 10
const FREE_SHIPPING_THRESHOLD = 100
const VAT_RATE = 0.10

interface CartItem {
  product: any
  variant?: any
  quantity: number
  priceMinor: number
}

function isOnSale(product: any): boolean {
  if (!product.salePriceMinor || !product.saleStartsAt || !product.saleEndsAt) return false
  const now = Date.now()
  return now > new Date(product.saleStartsAt).getTime() && now < new Date(product.saleEndsAt).getTime()
}

function getAvailableStock(product: any): number {
  if (product.variants && product.variants.length > 0) {
    return product.variants.reduce((sum: number, v: any) => {
      const stock = v.stockQuantity ?? 0
      const reserved = v.reservedQuantity ?? 0
      return sum + Math.max(0, stock - reserved)
    }, 0)
  }
  const stock = (product as any).stockQuantity ?? 0
  const reserved = (product as any).reservedQuantity ?? 0
  return Math.max(0, stock - reserved)
}

export function ShopPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  // Queries
  const { data: products } = useListProductsQuery()
  
  // Resolve current user info
  const { data: me } = useMeQuery()
  const isCustomerRole = me?.role?.name === 'customer'

  // Fetch list of customers if admin/operator
  const { data: customers } = useQuery({
    ...listCustomersOptions(),
    enabled: !isCustomerRole,
  })

  // Fetch logged in customer's profile if customer
  const { data: myCustomerProfile } = useQuery({
    ...getMyCustomerProfileOptions(),
    enabled: isCustomerRole,
  })

  const [selectedCustomerId, setSelectedCustomerId] = useState<string>('')

  // Resolve customerId
  const customerId = useMemo(() => {
    if (isCustomerRole) {
      return myCustomerProfile?.id ?? ''
    }
    return selectedCustomerId || (customers && customers.length > 0 ? customers[0].id! : '')
  }, [isCustomerRole, myCustomerProfile, selectedCustomerId, customers])

  const { data: categories } = useQuery({
    queryKey: ['categories'],
    queryFn: () => catalogApi.listCategories(),
  })

  // Get active vouchers
  const { data: allVouchersResponse } = useQuery(listVouchersOptions())
  const vouchers = useMemo(() => {
    if (!allVouchersResponse) return []
    return (allVouchersResponse as any[]).filter((v: any) => v.active)
  }, [allVouchersResponse])

  // Get collected vouchers
  const { data: collectedVouchers } = useQuery({
    ...myVouchersOptions(customerId ? { query: { customerId } } : undefined),
    enabled: !!customerId,
  })

  const collectedIds = useMemo(() => {
    if (!collectedVouchers) return new Set<string>()
    return new Set((collectedVouchers as any[]).map((cv: any) => cv.voucherId))
  }, [collectedVouchers])

  // Collect voucher
  const collectMutation = useCollectMutation({
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: myVouchersQueryKey() })
    },
  })

  const [search, setSearch] = useState('')
  const [activeCategory, setActiveCategory] = useState('')
  const [cart, setCart] = useState<CartItem[]>([])
  const [showCart, setShowCart] = useState(false)
  const [showCheckout, setShowCheckout] = useState(false)
  const [showPromoDrawer, setShowPromoDrawer] = useState(false)
  const [error, setError] = useState('')
  const [selectedVoucherId, setSelectedVoucherId] = useState<string>('')
  
  const [selectedProductForVariant, setSelectedProductForVariant] = useState<any>(null)
  const [selectedVariantIdForProduct, setSelectedVariantIdForProduct] = useState<string>('')

  useEffect(() => {
    if (selectedProductForVariant?.variants?.length > 0) {
      const firstInStock = selectedProductForVariant.variants.find((v: any) => {
        const stock = v.stockQuantity ?? 0
        const reserved = v.reservedQuantity ?? 0
        return Math.max(0, stock - reserved) > 0
      })
      setSelectedVariantIdForProduct(firstInStock?.id || selectedProductForVariant.variants[0].id || '')
    } else {
      setSelectedVariantIdForProduct('')
    }
  }, [selectedProductForVariant])

  const activeProducts = useMemo(() => {
    if (!products) return []
    return products.filter((p: any) => p.active !== false)
  }, [products])

  const filteredProducts = useMemo(() => {
    let list = activeProducts
    if (search) {
      const q = search.toLowerCase()
      list = list.filter((p: any) => (p.name ?? '').toLowerCase().includes(q) || (p.sku ?? '').toLowerCase().includes(q))
    }
    if (activeCategory) {
      list = list.filter((p: any) => p.category?.id === activeCategory)
    }
    return list
  }, [activeProducts, search, activeCategory])

  const saleProducts = useMemo(() => activeProducts.filter((p: any) => isOnSale(p)), [activeProducts])

  const cartTotal = useMemo(() => cart.reduce((s, i) => s + i.priceMinor * i.quantity, 0), [cart])

  const collectedVoucherList = useMemo(() => {
    if (!collectedVouchers) return []
    return (collectedVouchers as any[]).filter((cv: any) => cv.voucher?.active)
  }, [collectedVouchers])

  const selectedVoucher = useMemo(() => {
    if (!selectedVoucherId || !collectedVoucherList.length) return null
    return collectedVoucherList.find((cv: any) => cv.voucherId === selectedVoucherId)?.voucher ?? null
  }, [selectedVoucherId, collectedVoucherList])

  const discountAmount = useMemo(() => {
    if (!selectedVoucher) return 0
    const v = selectedVoucher
    
    // Check validity based on minOrderValueMinor
    const minOrderVal = Number(v.minOrderValueMinor)
    if (cartTotal < minOrderVal) return 0

    if (v.discountType === 'PERCENTAGE') {
      const disc = cartTotal * (Number(v.discountValue) / 100)
      return v.maxDiscountAmountMinor ? Math.min(disc, Number(v.maxDiscountAmountMinor)) : disc
    }
    return Number(v.discountValue)
  }, [selectedVoucher, cartTotal])

  const shippingFeeMinor = cartTotal > 0 && (cartTotal - discountAmount) < FREE_SHIPPING_THRESHOLD ? FLAT_SHIPPING_FEE : 0
  const taxAmountMinor = Math.max(0, (cartTotal - discountAmount) * VAT_RATE)
  const finalAmountMinor = Math.max(0, cartTotal - discountAmount + shippingFeeMinor + taxAmountMinor)

  function addToCart(product: any, variant?: any) {
    const hasVariants = (product.variants?.length ?? 0) > 0
    if (hasVariants && !variant) {
      setSelectedProductForVariant(product)
      return
    }
    const sale = isOnSale(product)
    const priceMinor = variant ? Number(variant.priceMinor) : (sale ? Number(product.salePriceMinor) : Number(product.basePriceMinor))
    const avail = variant ? (variant.stockQuantity - variant.reservedQuantity) : getAvailableStock(product)
    const existing = cart.find(i => i.product.id === product.id && i.variant?.id === variant?.id)
    const currentQty = existing ? existing.quantity : 0
    if (currentQty >= avail) {
      setError(`Only ${avail} available`)
      return
    }
    setError('')
    if (existing) {
      setCart(cart.map(i => (i.product.id === product.id && i.variant?.id === variant?.id) ? { ...i, quantity: i.quantity + 1 } : i))
    } else {
      setCart([...cart, { product, variant, quantity: 1, priceMinor }])
    }
    setShowCart(true)
  }

  function updateQty(productId: string, variantId: string | undefined, delta: number) {
    setCart(cart.map(i => {
      if (i.product.id !== productId || i.variant?.id !== variantId) return i
      const newQty = i.quantity + delta
      if (newQty <= 0) return null
      const avail = i.variant ? (i.variant.stockQuantity - i.variant.reservedQuantity) : getAvailableStock(i.product)
      if (delta > 0 && i.quantity >= avail) {
        setError(`Only ${avail} available`)
        return i
      }
      setError('')
      return { ...i, quantity: newQty }
    }).filter(Boolean) as CartItem[])
  }

  const checkoutMutation = useCheckoutOrderMutation({
    onSuccess: (data: any) => {
      queryClient.invalidateQueries({ queryKey: ['list4'] })
      queryClient.invalidateQueries({ queryKey: myVouchersQueryKey() })
      setCart([])
      setShowCart(false)
      setShowCheckout(false)
      setSelectedVoucherId('')
      const order = data?.data
      if (order?.id) {
        navigate({ to: '/orders/$id', params: { id: order.id } })
      }
    },
    onError: (err: any) => {
      setError(err?.body?.message || err?.message || 'Checkout failed')
    },
  })

  const [customerName, setCustomerName] = useState('')
  const [customerEmail, setCustomerEmail] = useState('')
  const [customerPhone, setCustomerPhone] = useState('')
  const [customerAddress, setCustomerAddress] = useState('')
  const [shippingNote, setShippingNote] = useState('')

  // Pre-populate shipping fields when customer profile or selected customer changes
  useEffect(() => {
    if (isCustomerRole && myCustomerProfile) {
      setCustomerName(`${myCustomerProfile.firstName ?? ''} ${myCustomerProfile.lastName ?? ''}`.trim())
      setCustomerEmail(myCustomerProfile.email ?? '')
      setCustomerPhone(myCustomerProfile.phone ?? '')
      setCustomerAddress(myCustomerProfile.address ?? '')
    } else if (!isCustomerRole && selectedCustomerId && customers) {
      const found = customers.find(c => c.id === selectedCustomerId)
      if (found) {
        setCustomerName(`${found.firstName ?? ''} ${found.lastName ?? ''}`.trim())
        setCustomerEmail(found.email ?? '')
        setCustomerPhone(found.phone ?? '')
        setCustomerAddress(found.address ?? '')
      }
    }
  }, [isCustomerRole, myCustomerProfile, selectedCustomerId, customers])

  async function handleCheckout() {
    if (!customerName.trim() || !customerPhone.trim() || !customerAddress.trim()) {
      setError('Please fill in name, phone, and address')
      return
    }

    checkoutMutation.mutate({
      body: {
        customerId,
        voucherCode: selectedVoucher?.code ?? undefined,
        shippingName: customerName.trim(),
        shippingPhone: customerPhone.trim(),
        shippingAddress: customerAddress.trim(),
        shippingNote: shippingNote.trim() || undefined,
        items: cart.map(i => ({
          productId: i.product.id!,
          productVariantId: i.variant?.id ?? null,
          quantity: i.quantity,
        })) as any,
      },
    })
  }

  return (
    <div className="space-y-6 relative pb-16">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-black tracking-tight">Shop</h1>
          <p className="text-sm text-muted-foreground mt-1">Browse products and place orders</p>
        </div>
        <div className="flex items-center gap-3">
          {vouchers.length > 0 && (
            <Button
              variant="outline"
              onClick={() => setShowPromoDrawer(true)}
              className="relative bg-gradient-to-r from-pink-500/10 to-rose-500/10 hover:from-pink-500/20 hover:to-rose-500/20 border-rose-200 text-rose-600 dark:text-rose-400 dark:border-rose-950 font-bold"
            >
              <Gift className="h-4 w-4 mr-2" />
              Collect Vouchers
              {vouchers.filter(v => !collectedIds.has(v.id)).length > 0 && (
                <span className="absolute -top-1 -right-1 bg-rose-500 text-white text-[10px] w-4.5 h-4.5 flex items-center justify-center rounded-full font-bold shadow">
                  {vouchers.filter(v => !collectedIds.has(v.id)).length}
                </span>
              )}
            </Button>
          )}
          <Button variant="outline" className="relative" onClick={() => setShowCart(true)}>
            <ShoppingCart className="h-4 w-4 mr-2" />
            Cart
            {cart.length > 0 && (
              <span className="ml-1 bg-primary text-primary-foreground text-xs font-bold px-1.5 py-0.5 rounded-full">
                {cart.reduce((s, i) => s + i.quantity, 0)}
              </span>
            )}
          </Button>
        </div>
      </div>

      {error && (
        <div className="text-sm font-semibold text-destructive bg-destructive/10 p-3 rounded-lg border border-destructive/20 flex items-start gap-2">
          <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
          <div>{error}</div>
        </div>
      )}

      {/* Sale Products */}
      {saleProducts.length > 0 && (
        <div>
          <div className="flex items-center gap-2 mb-3">
            <Flame className="h-5 w-5 text-red-500 animate-pulse" />
            <h2 className="font-bold text-lg">Hot Sale</h2>
          </div>
          <div className="grid gap-4 grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5">
            {saleProducts.slice(0, 10).map(product => {
              const avail = getAvailableStock(product)
              const hasVariants = (product.variants?.length ?? 0) > 0
              const discPct = product.salePriceMinor ? Math.round(((Number(product.basePriceMinor) - Number(product.salePriceMinor)) / Number(product.basePriceMinor)) * 100) : 0
              return (
                <Card key={product.id} className={`overflow-hidden transition-all hover:shadow-lg ${avail <= 0 ? 'opacity-60' : ''}`}>
                  <div className="relative aspect-square bg-muted flex items-center justify-center">
                    {product.imageUrl ? (
                      <img src={product.imageUrl} alt={product.name} className="w-full h-full object-cover" />
                    ) : (
                      <Package className="h-10 w-10 text-muted-foreground/30" />
                    )}
                    <div className="absolute top-2 left-2">
                      <Badge variant="destructive" className="text-[10px]">-{discPct}%</Badge>
                    </div>
                  </div>
                  <CardContent className="p-3">
                    <p className="font-semibold text-sm truncate">{product.name}</p>
                    <div className="flex items-center gap-1 mt-1">
                      <span className="font-bold text-sm text-red-500">{formatUSD(Number(product.salePriceMinor))}</span>
                      <span className="text-[10px] line-through text-muted-foreground">{formatUSD(Number(product.basePriceMinor))}</span>
                    </div>
                    <Button size="sm" className="w-full mt-2 h-8 text-xs cursor-pointer" disabled={avail <= 0} onClick={() => addToCart(product)}>
                      <ShoppingCart className="h-3 w-3 mr-1" />
                      {avail <= 0 ? 'Sold Out' : hasVariants ? 'Select Variant' : 'Add to Cart'}
                    </Button>
                  </CardContent>
                </Card>
              )
            })}
          </div>
        </div>
      )}

      {/* Voucher Banner Grid Shelf */}
      {vouchers.length > 0 && (
        <div className="bg-gradient-to-r from-muted/50 to-muted/20 border rounded-2xl p-6">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-2">
              <Gift className="h-5 w-5 text-primary" />
              <h2 className="font-black text-xl">Exclusive Store Vouchers</h2>
            </div>
            <span className="text-xs text-muted-foreground">Collect & save at checkout</span>
          </div>
          <div className="grid gap-4 grid-cols-1 sm:grid-cols-2 lg:grid-cols-3">
            {vouchers.slice(0, 6).map((voucher: any) => {
              const isCollected = collectedIds.has(voucher.id)
              return (
                <div
                  key={voucher.id}
                  className="relative flex items-stretch h-28 rounded-xl overflow-hidden shadow-sm hover:shadow-md transition-all border border-border bg-card group"
                >
                  {/* Perforated coupon visual */}
                  <div className="flex flex-col items-center justify-center w-24 bg-gradient-to-br from-indigo-500 via-purple-500 to-pink-500 text-white p-2 text-center shrink-0 select-none relative">
                    {/* Circle punch holes on perforations */}
                    <div className="absolute top-0 bottom-0 -right-1.5 flex flex-col justify-between py-1.5 z-10 w-3">
                      {[...Array(5)].map((_, idx) => (
                        <div key={idx} className="w-2.5 h-2.5 rounded-full bg-background" />
                      ))}
                    </div>
                    <span className="text-2xl font-black tracking-tight leading-none">
                      {voucher.discountType === 'PERCENTAGE' ? `${Number(voucher.discountValue)}%` : `$${Number(voucher.discountValue)}`}
                    </span>
                    <span className="text-[9px] font-black uppercase tracking-wider mt-1 opacity-90">OFF</span>
                  </div>

                  {/* Coupon Details */}
                  <div className="flex-1 p-3 flex flex-col justify-between min-w-0 bg-muted/10">
                    <div className="min-w-0">
                      <div className="flex items-center gap-1.5">
                        <span className="font-mono font-black text-[10px] bg-primary/10 text-primary px-1.5 py-0.5 rounded uppercase tracking-wide">
                          {voucher.code}
                        </span>
                        {voucher.maxDiscountAmountMinor && (
                          <span className="text-[9px] text-muted-foreground font-medium">
                            Max ${Number(voucher.maxDiscountAmountMinor)}
                          </span>
                        )}
                      </div>
                      <h3 className="font-bold text-xs text-foreground mt-1.5 truncate group-hover:text-primary transition-colors">
                        {voucher.name}
                      </h3>
                      <p className="text-[10px] text-muted-foreground mt-0.5">
                        {Number(voucher.minOrderValueMinor) > 0 ? `Min. Spend: ${formatUSD(Number(voucher.minOrderValueMinor))}` : 'No Min. Spend'}
                      </p>
                    </div>

                    <div className="flex items-center justify-between mt-1 gap-2">
                      <span className="text-[9px] text-muted-foreground truncate">
                        {voucher.endTime ? `Exp: ${new Date(voucher.endTime).toLocaleDateString()}` : 'No Expiry'}
                      </span>
                      <Button
                        size="sm"
                        variant={isCollected ? 'ghost' : 'secondary'}
                        className={`h-7 px-2.5 text-[10px] font-bold rounded-md cursor-pointer transition-all ${
                          isCollected
                            ? 'text-success hover:bg-success/10 bg-success/5 border border-success/15'
                            : 'bg-primary text-primary-foreground hover:bg-primary/95 shadow-sm'
                        }`}
                        disabled={isCollected || collectMutation.isPending}
                        onClick={() => collectMutation.mutate({ path: { id: voucher.id! }, query: { customerId } })}
                      >
                        {isCollected ? (
                          <span className="flex items-center gap-0.5"><Check className="h-3 w-3 stroke-[3]" />Collected</span>
                        ) : (
                          <span className="flex items-center gap-0.5"><Gift className="h-3 w-3" />Collect</span>
                        )}
                      </Button>
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        </div>
      )}

      {/* All Products */}
      <div>
        <div className="flex items-center gap-3 mb-3 flex-wrap">
          <div className="relative flex-1 max-w-sm">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <Input
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Search products..."
              className="pl-8 h-9"
            />
          </div>
          <select
            className="h-9 rounded-lg border border-input bg-transparent px-3 text-sm"
            value={activeCategory}
            onChange={e => setActiveCategory(e.target.value)}
          >
            <option value="">All Categories</option>
            {categories?.map((cat: any) => (
              <option key={cat.id} value={cat.id}>{cat.name}</option>
            ))}
          </select>
        </div>

        <div className="grid gap-4 grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5">
          {filteredProducts.map(product => {
            const avail = getAvailableStock(product)
            const hasVariants = (product.variants?.length ?? 0) > 0
            const sale = isOnSale(product)
            return (
              <Card key={product.id} className={`overflow-hidden transition-all hover:shadow-lg ${avail <= 0 ? 'opacity-60' : ''}`}>
                <div className="relative aspect-square bg-muted flex items-center justify-center">
                  {product.imageUrl ? (
                    <img src={product.imageUrl} alt={product.name} className="w-full h-full object-cover" />
                  ) : (
                    <Package className="h-10 w-10 text-muted-foreground/30" />
                  )}
                  {sale && (
                    <div className="absolute top-2 left-2">
                      <Badge variant="destructive" className="text-[10px]">SALE</Badge>
                    </div>
                  )}
                </div>
                <CardContent className="p-3">
                  <p className="font-semibold text-sm truncate">{product.name}</p>
                  <div className="flex items-center gap-1 mt-1">
                    <span className={`font-bold text-sm ${sale ? 'text-red-500' : ''}`}>
                      {formatUSD(sale ? Number(product.salePriceMinor) : Number(product.basePriceMinor))}
                    </span>
                    {sale && <span className="text-[10px] line-through text-muted-foreground">{formatUSD(Number(product.basePriceMinor))}</span>}
                  </div>
                  <div className="flex items-center justify-between mt-2">
                    <span className={`text-[10px] ${avail <= 3 ? 'text-amber-500' : 'text-muted-foreground'}`}>
                      {avail <= 0 ? 'Sold Out' : `${avail} left`}
                    </span>
                    <Button size="sm" className="h-7 text-xs px-2 cursor-pointer" disabled={avail <= 0} onClick={() => addToCart(product)}>
                      {hasVariants ? 'Choose' : <Plus className="h-3 w-3" />}
                    </Button>
                  </div>
                </CardContent>
              </Card>
            )
          })}
        </div>

        {filteredProducts.length === 0 && (
          <div className="text-center py-16 text-muted-foreground">
            <Package className="h-12 w-12 mx-auto mb-3 opacity-30" />
            <p className="font-semibold">No products found</p>
          </div>
        )}
      </div>

      {/* Cart Drawer */}
      {showCart && (
        <>
          <div className="fixed inset-0 z-40 bg-black/40" onClick={() => setShowCart(false)} />
          <div className="fixed top-0 right-0 h-full z-50 w-full sm:w-96 bg-background border-l border-border shadow-2xl flex flex-col">
            <div className="flex items-center justify-between px-4 py-3 border-b">
              <h2 className="font-bold">Cart ({cart.reduce((s, i) => s + i.quantity, 0)})</h2>
              <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => setShowCart(false)}>
                <X className="h-4 w-4" />
              </Button>
            </div>
            <div className="flex-1 overflow-y-auto p-4 space-y-3">
              {cart.length === 0 ? (
                <div className="text-center py-12 text-muted-foreground">
                  <ShoppingBag className="h-10 w-10 mx-auto mb-2 opacity-30" />
                  <p className="text-sm">Cart is empty</p>
                </div>
              ) : (
                cart.map(item => {
                  const key = item.product.id + (item.variant ? `-${item.variant.id}` : '')
                  const variantLabel = item.variant?.optionValues 
                    ? ` (${Object.values(item.variant.optionValues).join(' / ')})` 
                    : ''
                  return (
                    <div key={key} className="flex gap-3 bg-muted/30 rounded-xl p-3">
                      <div className="h-12 w-12 rounded-lg bg-muted flex items-center justify-center shrink-0 overflow-hidden">
                        {item.product.imageUrl ? (
                          <img src={item.product.imageUrl} alt="" className="w-full h-full object-cover" />
                        ) : (
                          <Package className="h-5 w-5 text-muted-foreground/50" />
                        )}
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-semibold truncate">{item.product.name}{variantLabel}</p>
                        <p className="text-xs text-muted-foreground">{formatUSD(item.priceMinor)} each</p>
                        <div className="flex items-center justify-between mt-1">
                          <div className="flex items-center gap-1 bg-background border rounded-lg">
                            <button onClick={() => updateQty(item.product.id!, item.variant?.id, -1)} className="h-6 w-6 flex items-center justify-center hover:bg-muted rounded-l-lg"><Minus className="h-3 w-3" /></button>
                            <span className="w-6 text-center text-xs font-bold">{item.quantity}</span>
                            <button onClick={() => updateQty(item.product.id!, item.variant?.id, 1)} className="h-6 w-6 flex items-center justify-center hover:bg-muted rounded-r-lg"><Plus className="h-3 w-3" /></button>
                          </div>
                          <span className="font-bold text-sm">{formatUSD(item.priceMinor * item.quantity)}</span>
                        </div>
                      </div>
                    </div>
                  )
                })
              )}
            </div>
            {cart.length > 0 && (
              <div className="border-t p-4 space-y-3">
                <div className="flex justify-between font-bold text-base">
                  <span>Total</span>
                  <span className="text-primary">{formatUSD(cartTotal)}</span>
                </div>
                <Button className="w-full cursor-pointer" onClick={() => { setShowCheckout(true); setShowCart(false) }}>
                  Checkout <ChevronRight className="h-4 w-4 ml-1" />
                </Button>
              </div>
            )}
          </div>
        </>
      )}

      {/* Floating Promotion FAB */}
      {vouchers.length > 0 && (
        <button
          onClick={() => setShowPromoDrawer(true)}
          className="fixed bottom-6 right-6 z-40 bg-gradient-to-r from-pink-500 to-rose-500 text-white p-4 rounded-full shadow-2xl hover:scale-110 active:scale-95 transition-all hover:rotate-6 cursor-pointer flex items-center justify-center border border-white/20 focus:outline-none"
          title="Promo Vouchers"
        >
          <Gift className="h-6 w-6 text-white" />
          {vouchers.filter(v => !collectedIds.has(v.id)).length > 0 && (
            <span className="absolute -top-1 -right-1 bg-red-600 border border-white text-white text-[10px] font-black w-5 h-5 flex items-center justify-center rounded-full">
              {vouchers.filter(v => !collectedIds.has(v.id)).length}
            </span>
          )}
        </button>
      )}

      {/* Sliding Promotion Drawer */}
      {showPromoDrawer && (
        <>
          <div className="fixed inset-0 z-40 bg-black/40 backdrop-blur-xs" onClick={() => setShowPromoDrawer(false)} />
          <div className="fixed top-0 right-0 h-full z-50 w-full sm:w-[400px] bg-background border-l border-border shadow-2xl flex flex-col">
            <div className="flex items-center justify-between px-4 py-4 border-b">
              <div>
                <h2 className="font-bold text-lg flex items-center gap-2">
                  <Gift className="h-5 w-5 text-rose-500 animate-pulse" /> Promotion Vouchers
                </h2>
                <p className="text-xs text-muted-foreground mt-0.5">Collect vouchers to save on your orders</p>
              </div>
              <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => setShowPromoDrawer(false)}>
                <X className="h-4 w-4" />
              </Button>
            </div>
            <div className="flex-1 overflow-y-auto p-4 space-y-4">
              {vouchers.length === 0 ? (
                <div className="text-center py-12 text-muted-foreground">
                  <Gift className="h-10 w-10 mx-auto mb-2 opacity-30" />
                  <p className="text-sm">No vouchers available</p>
                </div>
              ) : (
                vouchers.map((voucher: any) => {
                  const isCollected = collectedIds.has(voucher.id)
                  return (
                    <div
                      key={voucher.id}
                      className="relative flex items-stretch h-28 rounded-xl overflow-hidden shadow-sm hover:shadow-md transition-all border border-border bg-card group"
                    >
                      {/* Ticket Left Section */}
                      <div className="flex flex-col items-center justify-center w-24 bg-gradient-to-br from-pink-500 via-rose-500 to-orange-500 text-white p-2 text-center shrink-0 select-none relative">
                        {/* Perforated holes */}
                        <div className="absolute top-0 bottom-0 -right-1.5 flex flex-col justify-between py-1.5 z-10 w-3">
                          {[...Array(5)].map((_, idx) => (
                            <div key={idx} className="w-2.5 h-2.5 rounded-full bg-background" />
                          ))}
                        </div>
                        <span className="text-xl font-black tracking-tight leading-none">
                          {voucher.discountType === 'PERCENTAGE' ? `${Number(voucher.discountValue)}%` : `$${Number(voucher.discountValue)}`}
                        </span>
                        <span className="text-[9px] font-black uppercase tracking-wider mt-1 opacity-90">OFF</span>
                      </div>

                      {/* Ticket Right Section */}
                      <div className="flex-1 p-3 flex flex-col justify-between min-w-0 bg-muted/10">
                        <div className="min-w-0">
                          <div className="flex items-center gap-1.5">
                            <span className="font-mono font-black text-[10px] bg-primary/10 text-primary px-1.5 py-0.5 rounded uppercase tracking-wide">
                              {voucher.code}
                            </span>
                            {voucher.maxDiscountAmountMinor && (
                              <span className="text-[9px] text-muted-foreground font-medium">
                                Max ${Number(voucher.maxDiscountAmountMinor)}
                              </span>
                            )}
                          </div>
                          <h3 className="font-bold text-xs text-foreground mt-1.5 truncate group-hover:text-primary transition-colors">
                            {voucher.name}
                          </h3>
                          <p className="text-[10px] text-muted-foreground mt-0.5">
                            {Number(voucher.minOrderValueMinor) > 0 ? `Min. Spend: ${formatUSD(Number(voucher.minOrderValueMinor))}` : 'No Min. Spend'}
                          </p>
                        </div>

                        <div className="flex items-center justify-between mt-1 gap-2">
                          <span className="text-[9px] text-muted-foreground truncate">
                            {voucher.endTime ? `Exp: ${new Date(voucher.endTime).toLocaleDateString()}` : 'No Expiry'}
                          </span>
                          <Button
                            size="sm"
                            variant={isCollected ? 'ghost' : 'secondary'}
                            className={`h-7 px-2.5 text-[10px] font-bold rounded-md cursor-pointer transition-all ${
                              isCollected
                                ? 'text-success hover:bg-success/10 bg-success/5 border border-success/15'
                                : 'bg-primary text-primary-foreground hover:bg-primary/95 shadow-sm'
                            }`}
                            disabled={isCollected || collectMutation.isPending}
                            onClick={() => collectMutation.mutate({ path: { id: voucher.id! }, query: { customerId } })}
                          >
                            {isCollected ? (
                              <span className="flex items-center gap-0.5"><Check className="h-3 w-3 stroke-[3]" />Collected</span>
                            ) : (
                              <span className="flex items-center gap-0.5"><Gift className="h-3 w-3" />Collect</span>
                            )}
                          </Button>
                        </div>
                      </div>
                    </div>
                  )
                })
              )}
            </div>
          </div>
        </>
      )}

      {/* Checkout Dialog */}
      <Dialog open={showCheckout} onOpenChange={setShowCheckout}>
        <DialogContent className="max-w-md max-h-[90vh] flex flex-col gap-0">
          <DialogHeader className="pb-4">
            <DialogTitle>Checkout</DialogTitle>
            <DialogDescription>Enter your shipping details and apply a voucher</DialogDescription>
          </DialogHeader>
          <div className="space-y-3 flex-1 overflow-y-auto pr-1">
            {/* Customer selector for Admin/Operator */}
            {!isCustomerRole && customers && (
              <div className="space-y-1">
                <Label className="text-sm font-semibold">Select Customer Context *</Label>
                <select
                  className="w-full rounded-lg border border-input bg-transparent px-3 py-2 text-sm"
                  value={selectedCustomerId}
                  onChange={e => {
                    const cid = e.target.value
                    setSelectedCustomerId(cid)
                  }}
                >
                  <option value="">-- Choose Customer --</option>
                  {customers.map(c => (
                    <option key={c.id} value={c.id}>
                      {c.firstName} {c.lastName} ({c.email})
                    </option>
                  ))}
                </select>
              </div>
            )}

            {isCustomerRole && myCustomerProfile && (
              <div className="bg-muted/30 p-2.5 rounded-lg border border-border/50 text-xs">
                <span className="text-muted-foreground">Shopping as:</span> <strong className="text-foreground">{myCustomerProfile.firstName} {myCustomerProfile.lastName} ({myCustomerProfile.email})</strong>
              </div>
            )}

            <div>
              <Label>Full Name *</Label>
              <Input value={customerName} onChange={e => setCustomerName(e.target.value)} placeholder="John Doe" />
            </div>
            <div>
              <Label>Email</Label>
              <Input type="email" value={customerEmail} onChange={e => setCustomerEmail(e.target.value)} placeholder="john@example.com" />
            </div>
            <div>
              <Label>Phone *</Label>
              <Input value={customerPhone} onChange={e => setCustomerPhone(e.target.value)} placeholder="+1 555-0000" />
            </div>
            <div>
              <Label>Shipping Address *</Label>
              <textarea
                className="flex min-h-[60px] w-full rounded-lg border border-input bg-transparent px-3 py-2 text-sm"
                value={customerAddress}
                onChange={e => setCustomerAddress(e.target.value)}
                placeholder="123 Main St, City"
              />
            </div>
            <div>
              <Label>Note</Label>
              <textarea
                className="flex min-h-[50px] w-full rounded-lg border border-input bg-transparent px-3 py-2 text-sm"
                value={shippingNote}
                onChange={e => setShippingNote(e.target.value)}
                placeholder="Optional delivery note"
              />
            </div>

            {/* Premium Voucher interactive list */}
            {collectedVoucherList.length > 0 ? (
              <div className="border rounded-xl p-3 space-y-2.5 bg-muted/10">
                <Label className="flex items-center gap-2 text-sm font-semibold text-foreground">
                  <Tag className="h-4 w-4 text-primary" /> Apply Voucher ({collectedVoucherList.length})
                </Label>
                <div className="space-y-2 max-h-48 overflow-y-auto pr-1">
                  {collectedVoucherList.map((cv: any) => {
                    const v = cv.voucher
                    const isValid = cartTotal >= Number(v.minOrderValueMinor)
                    const isSelected = selectedVoucherId === cv.voucherId
                    
                    // Calculate individual voucher discount for preview
                    let previewDiscount = 0
                    if (isValid) {
                      if (v.discountType === 'PERCENTAGE') {
                        previewDiscount = cartTotal * (Number(v.discountValue) / 100)
                        if (v.maxDiscountAmountMinor) {
                          previewDiscount = Math.min(previewDiscount, Number(v.maxDiscountAmountMinor))
                        }
                      } else {
                        previewDiscount = Number(v.discountValue)
                      }
                      previewDiscount = Math.min(previewDiscount, cartTotal)
                    }

                    return (
                      <div
                        key={cv.voucherId}
                        onClick={() => {
                          if (isValid) {
                            setSelectedVoucherId(isSelected ? '' : cv.voucherId)
                          }
                        }}
                        className={`flex items-center justify-between p-3 rounded-lg border transition-all cursor-pointer ${
                          !isValid
                            ? 'opacity-50 cursor-not-allowed border-border bg-muted/20'
                            : isSelected
                            ? 'border-primary bg-primary/5 ring-1 ring-primary'
                            : 'border-border bg-card hover:border-primary/45 hover:bg-muted/10'
                        }`}
                      >
                        <div className="min-w-0 flex-1">
                          <div className="flex items-center gap-2">
                            <span className="font-mono font-black text-xs text-primary uppercase">
                              {v.code}
                            </span>
                            {!isValid && (
                              <Badge variant="destructive" className="text-[9px] px-1 py-0 h-4">
                                Min. spend: {formatUSD(Number(v.minOrderValueMinor))}
                              </Badge>
                            )}
                          </div>
                          <p className="text-xs text-muted-foreground mt-0.5 truncate">{v.name}</p>
                        </div>
                        <div className="text-right shrink-0 pl-2">
                          <span className="font-bold text-xs text-primary block">
                            {v.discountType === 'PERCENTAGE' ? `${v.discountValue}% OFF` : `${formatUSD(Number(v.discountValue))} OFF`}
                          </span>
                          {isValid && isSelected && (
                            <span className="text-[10px] text-success font-semibold">
                              Saved -{formatUSD(previewDiscount)}
                            </span>
                          )}
                        </div>
                      </div>
                    )
                  })}
                </div>
                {selectedVoucher && (
                  <div className="flex items-center justify-between text-xs font-medium text-success pt-1.5 border-t border-dashed">
                    <span>Applied: {selectedVoucher.code}</span>
                    <span>-{formatUSD(discountAmount)}</span>
                  </div>
                )}
              </div>
            ) : (
              <div className="border border-dashed rounded-xl p-4 text-center text-xs text-muted-foreground bg-muted/5">
                <Gift className="h-6 w-6 mx-auto mb-1.5 opacity-40 text-muted-foreground" />
                <p>You have no collected vouchers.</p>
                <p className="mt-0.5 text-[10px]">Collect them on the shop page to get discounts!</p>
              </div>
            )}

            {/* Price breakdown */}
            <div className="border-t pt-3 space-y-1.5 text-sm">
              <div className="flex justify-between">
                <span>Subtotal</span>
                <span>{formatUSD(cartTotal)}</span>
              </div>
              {discountAmount > 0 && (
                <div className="flex justify-between text-success">
                  <span>Discount</span>
                  <span>-{formatUSD(discountAmount)}</span>
                </div>
              )}
              <div className="flex justify-between">
                <span>Shipping</span>
                <span>{shippingFeeMinor > 0 ? formatUSD(shippingFeeMinor) : 'Free'}</span>
              </div>
              <div className="flex justify-between">
                <span>VAT (10%)</span>
                <span>{formatUSD(taxAmountMinor)}</span>
              </div>
              <div className="flex justify-between font-bold text-base border-t pt-1.5">
                <span>Total</span>
                <span className="text-primary">{formatUSD(finalAmountMinor)}</span>
              </div>
            </div>
          </div>
          <DialogFooter className="pt-4">
            <Button variant="outline" className="cursor-pointer" onClick={() => setShowCheckout(false)}>Cancel</Button>
            <Button onClick={handleCheckout} className="cursor-pointer" disabled={checkoutMutation.isPending || !customerId}>
              {checkoutMutation.isPending ? 'Processing...' : `Place Order - ${formatUSD(finalAmountMinor)}`}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Variant Selection Dialog */}
      <Dialog open={!!selectedProductForVariant} onOpenChange={(open) => { if (!open) setSelectedProductForVariant(null) }}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Select Option</DialogTitle>
            <DialogDescription>Choose a variant for {selectedProductForVariant?.name}</DialogDescription>
          </DialogHeader>
          <div className="space-y-3 my-4 max-h-64 overflow-y-auto pr-1">
            {selectedProductForVariant?.variants?.map((v: any) => {
              const stock = v.stockQuantity ?? 0
              const reserved = v.reservedQuantity ?? 0
              const avail = Math.max(0, stock - reserved)
              const isSelected = selectedVariantIdForProduct === v.id
              const optionString = Object.entries(v.optionValues || {})
                .map(([key, value]) => `${key}: ${value}`)
                .join(', ')
              return (
                <div
                  key={v.id}
                  onClick={() => avail > 0 && setSelectedVariantIdForProduct(v.id)}
                  className={`flex items-center justify-between p-3 rounded-lg border transition-all ${
                    avail <= 0
                      ? 'opacity-50 cursor-not-allowed border-border bg-muted/20'
                      : isSelected
                      ? 'border-primary bg-primary/5 ring-1 ring-primary cursor-pointer'
                      : 'border-border bg-card hover:border-primary/45 hover:bg-muted/10 cursor-pointer'
                  }`}
                >
                  <div>
                    <p className="font-semibold text-sm">{optionString || v.sku}</p>
                    <p className="text-xs text-muted-foreground">{avail <= 0 ? 'Sold Out' : `${avail} left`}</p>
                  </div>
                  <div className="text-right">
                    <span className="font-bold text-sm text-primary">{formatUSD(Number(v.priceMinor))}</span>
                  </div>
                </div>
              )
            })}
          </div>
          <DialogFooter>
            <Button variant="outline" className="cursor-pointer" onClick={() => setSelectedProductForVariant(null)}>Cancel</Button>
            <Button
              className="cursor-pointer"
              disabled={!selectedVariantIdForProduct}
              onClick={() => {
                if (selectedProductForVariant && selectedVariantIdForProduct) {
                  const targetVariant = selectedProductForVariant.variants.find((v: any) => v.id === selectedVariantIdForProduct)
                  if (targetVariant) {
                    addToCart(selectedProductForVariant, targetVariant)
                    setSelectedProductForVariant(null)
                    setSelectedVariantIdForProduct('')
                  }
                }
              }}
            >
              Add to Cart
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
