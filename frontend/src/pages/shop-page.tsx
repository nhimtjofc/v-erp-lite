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
  listOrdersQueryKey,
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
  ShoppingBag, ChevronRight, AlertCircle, Gift, Check, Sparkles,
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
  const isCustomerRole = me?.role?.name === 'customer' || me?.role?.name === 'user'

  // Fetch list of customers if admin/operator
  const { data: customers } = useQuery({
    ...listCustomersOptions(),
    enabled: !!me && !isCustomerRole,
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
  const [cart, setCart] = useState<CartItem[]>(() => {
    try {
      const saved = localStorage.getItem('buyer_cart')
      return saved ? JSON.parse(saved) : []
    } catch {
      return []
    }
  })
  const [showCart, setShowCart] = useState(false)
  const [showCheckout, setShowCheckout] = useState(false)
  const [error, setError] = useState('')
  const [selectedVoucherId, setSelectedVoucherId] = useState<string>('')
  const [paymentMethod, setPaymentMethod] = useState<'COD' | 'BANK_TRANSFER'>('COD')

  useEffect(() => {
    localStorage.setItem('buyer_cart', JSON.stringify(cart))
  }, [cart])
  
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
      queryClient.invalidateQueries({ queryKey: listOrdersQueryKey() })
      queryClient.invalidateQueries({ queryKey: myVouchersQueryKey() })
      setCart([])
      setShowCart(false)
      setShowCheckout(false)
      setSelectedVoucherId('')
      const result = data as { id?: string; data?: { id?: string } }
      const orderId = result.data?.id ?? result.id
      if (orderId) {
        if (isCustomerRole && paymentMethod === 'BANK_TRANSFER') {
          navigate({ to: '/orders/payment', search: { orderId } })
        } else if (isCustomerRole) {
          navigate({ to: '/orders' })
        } else {
          navigate({ to: '/orders/$id', params: { id: orderId } })
        }
      }
    },
    onError: (err: any) => {
      let msg = err?.body?.message || err?.message || 'Checkout failed'
      if (typeof err?.body === 'string') msg = err.body
      setError(msg)
    },
  })

  const [customerName, setCustomerName] = useState('')
  const [customerEmail, setCustomerEmail] = useState('')
  const [customerPhone, setCustomerPhone] = useState('')
  const [customerAddress, setCustomerAddress] = useState('')
  const [shippingNote, setShippingNote] = useState('')

  // Pre-populate checkout from the customer profile, with the signed-in account
  // as a fallback for profiles created before account details were synchronized.
  useEffect(() => {
    if (isCustomerRole) {
      const firstName = myCustomerProfile?.firstName?.trim() || me?.firstName?.trim() || ''
      const lastName = myCustomerProfile?.lastName?.trim() || me?.lastName?.trim() || ''
      setCustomerName(`${firstName} ${lastName}`.trim())
      setCustomerEmail(myCustomerProfile?.email?.trim() || me?.email?.trim() || '')
      setCustomerPhone(myCustomerProfile?.phone?.trim() || me?.phone?.trim() || '')
      setCustomerAddress(myCustomerProfile?.address ?? '')
    } else if (!isCustomerRole && selectedCustomerId && customers) {
      const found = customers.find(c => c.id === selectedCustomerId)
      if (found) {
        setCustomerName(`${found.firstName ?? ''} ${found.lastName ?? ''}`.trim())
        setCustomerEmail(found.email ?? '')
        setCustomerPhone(found.phone ?? '')
        setCustomerAddress(found.address ?? '')
      }
    }
  }, [isCustomerRole, me, myCustomerProfile, selectedCustomerId, customers])

  async function handleCheckout() {
    if (!customerName.trim() || !customerPhone.trim() || !customerAddress.trim()) {
      setError('Please fill in name, phone, and address')
      return
    }

    checkoutMutation.mutate({
      body: {
        customerId,
        paymentMethod,
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
      <div className="overflow-hidden rounded-[2rem] border border-orange-200/70 bg-white/75 shadow-xl shadow-orange-100/70 backdrop-blur-xl dark:border-white/10 dark:bg-white/[0.04] dark:shadow-none">
        <div className="grid gap-6 p-5 md:grid-cols-[1.35fr_0.65fr] md:p-8">
          <div className="flex flex-col justify-center">
            <Badge className="w-fit border-transparent bg-orange-100 text-orange-700 hover:bg-orange-100 dark:bg-orange-500/15 dark:text-orange-200">
              <Sparkles className="mr-1 h-3.5 w-3.5" />
              Storefront for customers
            </Badge>
            <h1 className="mt-4 max-w-3xl text-4xl font-black tracking-tight text-stone-950 dark:text-white md:text-5xl">
              Browse fresh deals from every seller.
            </h1>
            <p className="mt-3 max-w-2xl text-sm leading-6 text-stone-600 dark:text-slate-300 md:text-base">
              A customer-first shopping space with vouchers, live stock, seller grouping, and checkout in one flow.
            </p>
          </div>

          <div className="relative min-h-48 overflow-hidden rounded-[1.5rem] bg-gradient-to-br from-orange-500 via-rose-500 to-fuchsia-600 p-5 text-white shadow-lg shadow-orange-500/25">
            <div className="absolute -right-10 -top-10 h-36 w-36 rounded-full bg-white/20 blur-2xl" />
            <div className="absolute -bottom-12 left-6 h-32 w-32 rounded-full bg-yellow-300/30 blur-2xl" />
            <div className="relative flex h-full flex-col justify-between">
              <div>
                <p className="text-xs font-black uppercase tracking-[0.25em] text-white/75">Today</p>
                <p className="mt-2 text-3xl font-black">{activeProducts.length}</p>
                <p className="text-sm font-semibold text-white/80">active products</p>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div className="rounded-2xl bg-white/15 p-3 backdrop-blur">
                  <p className="text-2xl font-black">{saleProducts.length}</p>
                  <p className="text-xs font-semibold text-white/75">hot deals</p>
                </div>
                <div className="rounded-2xl bg-white/15 p-3 backdrop-blur">
                  <p className="text-2xl font-black">{vouchers.length}</p>
                  <p className="text-xs font-semibold text-white/75">vouchers</p>
                </div>
              </div>
            </div>
          </div>
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
              const storeName = product.business?.name || 'V-ERP Store'

              return (
                <Card key={product.id} className={`group relative bg-card border rounded-2xl overflow-hidden transition-all duration-300 hover:shadow-xl hover:-translate-y-1 ${avail <= 0 ? 'opacity-60' : 'hover:border-red-500/30'}`}>
                  <div className="relative aspect-square bg-muted/30 flex items-center justify-center overflow-hidden">
                    {product.imageUrl ? (
                      <img src={product.imageUrl} alt={product.name} className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105" />
                    ) : (
                      <Package className="h-10 w-10 text-muted-foreground/30" />
                    )}
                    <div className="absolute top-2 left-2 flex flex-col gap-1">
                      <Badge variant="destructive" className="text-[9px] font-bold bg-red-500 hover:bg-red-600 shadow shadow-red-500/30">-{discPct}%</Badge>
                    </div>
                    {product.business && (
                      <div className="absolute bottom-2 left-2 right-2">
                        <div className="inline-flex items-center gap-1 bg-background/90 backdrop-blur-xs text-foreground px-1.5 py-0.5 rounded-md border border-border/80 text-[8px] font-semibold max-w-full truncate shadow-xs">
                          <ShoppingBag className="h-2 w-2 text-red-500" />
                          <span className="truncate">{storeName}</span>
                        </div>
                      </div>
                    )}
                  </div>
                  <CardContent className="p-3">
                    <p className="font-semibold text-xs text-foreground leading-snug line-clamp-2 min-h-[2rem]">{product.name}</p>
                    
                    <div className="flex items-center gap-1.5 mt-1.5">
                      <span className="font-bold text-sm text-red-500">{formatUSD(Number(product.salePriceMinor))}</span>
                      <span className="text-[10px] line-through text-muted-foreground">{formatUSD(Number(product.basePriceMinor))}</span>
                    </div>

                    <Button size="sm" className="w-full mt-3.5 h-8 text-[10px] font-bold cursor-pointer transition-all hover:bg-red-600 shadow-xs" disabled={avail <= 0} onClick={() => addToCart(product)}>
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

      {/* All Products */}
      <div>
        <div className="mb-4 flex flex-col gap-3 rounded-3xl border border-orange-200/70 bg-white/70 p-4 shadow-sm shadow-orange-100/60 backdrop-blur-xl dark:border-white/10 dark:bg-white/[0.03] dark:shadow-none md:flex-row md:items-center md:justify-between">
          <div>
            <h2 className="text-2xl font-black tracking-tight">Explore products</h2>
            <p className="text-sm text-stone-500 dark:text-slate-400">{filteredProducts.length} items available</p>
          </div>
          <div className="flex flex-1 flex-wrap items-center gap-3 md:justify-end">
          <div className="relative flex-1 max-w-sm">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <Input
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Search products..."
              className="h-10 rounded-full border-orange-200/80 bg-white/80 pl-8 dark:border-white/10 dark:bg-white/5"
            />
          </div>
          <select
            className="h-10 rounded-full border border-orange-200/80 bg-white/80 px-4 text-sm font-semibold dark:border-white/10 dark:bg-slate-950"
            value={activeCategory}
            onChange={e => setActiveCategory(e.target.value)}
          >
            <option value="">All Categories</option>
            {categories?.map((cat: any) => (
              <option key={cat.id} value={cat.id}>{cat.name}</option>
            ))}
          </select>
          </div>
        </div>

        <div className="grid gap-4 grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5">
          {filteredProducts.map(product => {
            const avail = getAvailableStock(product)
            const hasVariants = (product.variants?.length ?? 0) > 0
            const sale = isOnSale(product)
            const storeName = product.business?.name || 'V-ERP Store'

            return (
              <Card key={product.id} className={`group relative bg-card border rounded-2xl overflow-hidden transition-all duration-300 hover:shadow-xl hover:-translate-y-1 ${avail <= 0 ? 'opacity-60' : 'hover:border-primary/30'}`}>
                <div className="relative aspect-square bg-muted/30 flex items-center justify-center overflow-hidden">
                  {product.imageUrl ? (
                    <img src={product.imageUrl} alt={product.name} className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105" />
                  ) : (
                    <Package className="h-10 w-10 text-muted-foreground/30" />
                  )}
                  {sale && (
                    <div className="absolute top-2 left-2">
                      <Badge variant="destructive" className="text-[9px] font-bold bg-red-500 hover:bg-red-600 shadow shadow-red-500/30">SALE</Badge>
                    </div>
                  )}
                  {product.business && (
                    <div className="absolute bottom-2 left-2 right-2">
                      <div className="inline-flex items-center gap-1 bg-background/90 backdrop-blur-xs text-foreground px-1.5 py-0.5 rounded-md border border-border/80 text-[8px] font-semibold max-w-full truncate shadow-xs">
                        <ShoppingBag className="h-2 w-2 text-primary" />
                        <span className="truncate">{storeName}</span>
                      </div>
                    </div>
                  )}
                </div>
                <CardContent className="p-3">
                  <p className="font-semibold text-xs text-foreground leading-snug line-clamp-2 min-h-[2rem]">{product.name}</p>
                  
                  <div className="flex items-center gap-1.5 mt-1.5">
                    <span className={`font-bold text-sm ${sale ? 'text-red-500' : 'text-foreground'}`}>
                      {formatUSD(sale ? Number(product.salePriceMinor) : Number(product.basePriceMinor))}
                    </span>
                    {sale && <span className="text-[10px] line-through text-muted-foreground">{formatUSD(Number(product.basePriceMinor))}</span>}
                  </div>
                  
                  <div className="flex items-center justify-between mt-2.5 pt-2 border-t border-border/40">
                    <span className={`text-[9px] font-semibold px-1 py-0.5 rounded-sm ${avail <= 3 ? 'text-amber-600 bg-amber-500/10' : 'text-muted-foreground bg-muted/40'}`}>
                      {avail <= 0 ? 'Sold Out' : `${avail} left`}
                    </span>
                    <Button size="sm" className="h-6.5 text-[10px] font-bold px-2 cursor-pointer shadow-xs transition-all hover:shadow" disabled={avail <= 0} onClick={() => addToCart(product)}>
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
                (() => {
                  const grouped = {} as Record<string, { business?: any; items: CartItem[]; subtotal: number }>;
                  cart.forEach(item => {
                    const business = item.product?.business;
                    const bizId = business?.id || 'system';
                    if (!grouped[bizId]) {
                      grouped[bizId] = { business, items: [], subtotal: 0 };
                    }
                    grouped[bizId].items.push(item);
                    grouped[bizId].subtotal += item.priceMinor * item.quantity;
                  });

                  return Object.values(grouped).map((group, gIdx) => {
                    const storeName = group.business?.name || 'V-ERP System Store';
                    const storeLogo = group.business?.logoUrl;

                    return (
                      <div key={gIdx} className="border border-border/60 rounded-xl bg-card overflow-hidden shadow-xs mb-3">
                        {/* Store Header */}
                        <div className="flex items-center gap-2 px-3 py-2 bg-muted/20 border-b border-border/40">
                          {storeLogo ? (
                            <img src={storeLogo} alt={storeName} className="h-5 w-5 rounded object-cover" />
                          ) : (
                            <ShoppingBag className="h-4 w-4 text-primary" />
                          )}
                          <span className="font-semibold text-xs text-foreground truncate">{storeName}</span>
                          {group.business ? (
                            <Badge variant="outline" className="text-[9px] h-4 px-1 bg-primary/5 text-primary border-primary/20">Seller</Badge>
                          ) : (
                            <Badge variant="outline" className="text-[9px] h-4 px-1 bg-muted text-muted-foreground">Official</Badge>
                          )}
                        </div>

                        {/* Store Items */}
                        <div className="divide-y divide-border/40 p-2.5 space-y-2.5">
                          {group.items.map((item, originalIndex) => {
                            const variantLabel = item.variant?.optionValues 
                              ? ` (${Object.values(item.variant.optionValues).join(' / ')})` 
                              : '';
                            return (
                              <div key={originalIndex} className="flex gap-3 pt-2.5 first:pt-0">
                                <div className="h-11 w-11 rounded-lg bg-muted flex items-center justify-center shrink-0 overflow-hidden border border-border">
                                  {item.product.imageUrl ? (
                                    <img src={item.product.imageUrl} alt="" className="w-full h-full object-cover" />
                                  ) : (
                                    <Package className="h-5 w-5 text-muted-foreground/50" />
                                  )}
                                </div>
                                <div className="flex-1 min-w-0">
                                  <div className="flex items-start justify-between gap-1">
                                    <p className="text-xs font-semibold truncate flex-1">{item.product.name}{variantLabel}</p>
                                    <button 
                                      onClick={() => updateQty(item.product.id!, item.variant?.id, -item.quantity)} 
                                      className="text-muted-foreground hover:text-destructive p-0.5 cursor-pointer shrink-0"
                                    >
                                      <X className="h-3 w-3" />
                                    </button>
                                  </div>
                                  <p className="text-[10px] text-muted-foreground">{formatUSD(item.priceMinor)} each</p>
                                  <div className="flex items-center justify-between mt-1.5">
                                    <div className="flex items-center gap-1 bg-background border rounded-md p-0.5">
                                      <button onClick={() => updateQty(item.product.id!, item.variant?.id, -1)} className="h-5 w-5 flex items-center justify-center hover:bg-muted rounded text-muted-foreground hover:text-foreground cursor-pointer"><Minus className="h-2.5 w-2.5" /></button>
                                      <span className="w-6 text-center text-xs font-bold">{item.quantity}</span>
                                      <button onClick={() => updateQty(item.product.id!, item.variant?.id, 1)} className="h-5 w-5 flex items-center justify-center hover:bg-muted rounded text-muted-foreground hover:text-foreground cursor-pointer"><Plus className="h-2.5 w-2.5" /></button>
                                    </div>
                                    <span className="font-bold text-xs">{formatUSD(item.priceMinor * item.quantity)}</span>
                                  </div>
                                </div>
                              </div>
                            );
                          })}
                        </div>

                        {/* Store Subtotal Footer */}
                        <div className="px-3 py-1.5 bg-muted/5 border-t border-border/30 flex justify-between items-center text-[10px] text-muted-foreground">
                          <span>Store subtotal:</span>
                          <span className="font-semibold text-foreground">{formatUSD(group.subtotal)}</span>
                        </div>
                      </div>
                    );
                  })
                })()
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

      {/* Floating Cart FAB */}
      <button
        onClick={() => setShowCart(true)}
        className="fixed bottom-6 right-6 z-40 flex items-center gap-2 rounded-full bg-gradient-to-r from-orange-500 to-rose-500 text-white px-4 py-3 shadow-2xl hover:scale-105 active:scale-95 transition-all hover:shadow-orange-500/30 cursor-pointer border border-white/20 focus:outline-none group"
        title="View Cart"
      >
        <div className="relative">
          <ShoppingCart className="h-5 w-5 transition-transform group-hover:-rotate-6" />
          {cart.length > 0 && (
            <span className="absolute -top-2.5 -right-2.5 bg-stone-950 text-white text-[10px] font-bold w-5 h-5 flex items-center justify-center rounded-full border border-white dark:bg-white dark:text-stone-950">
              {cart.reduce((s, i) => s + i.quantity, 0)}
            </span>
          )}
        </div>
        <span className="font-bold text-sm">Cart</span>
        {cart.length > 0 && (
          <span className="text-xs font-black border-l border-white/20 pl-2">
            {formatUSD(cartTotal)}
          </span>
        )}
      </button>

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

            {/* Review Items dropdown grouped by Seller */}
            <div className="border border-border/60 rounded-xl p-3 bg-muted/5 space-y-2">
              <details className="group cursor-pointer">
                <summary className="flex items-center justify-between text-xs font-bold text-muted-foreground uppercase tracking-wide select-none">
                  <span className="flex items-center gap-1.5"><ShoppingBag className="h-3.5 w-3.5" /> Review Items ({cart.reduce((s, i) => s + i.quantity, 0)})</span>
                  <span className="text-[10px] group-open:rotate-180 transition-transform">▼</span>
                </summary>
                <div className="space-y-3 pt-2 mt-2 border-t border-border/40 max-h-48 overflow-y-auto">
                  {(() => {
                    const grouped = {} as Record<string, { name: string; items: CartItem[]; subtotal: number }>;
                    cart.forEach(item => {
                      const bizId = item.product.business?.id || 'system';
                      const bizName = item.product.business?.name || 'V-ERP System Store';
                      if (!grouped[bizId]) {
                        grouped[bizId] = { name: bizName, items: [], subtotal: 0 };
                      }
                      grouped[bizId].items.push(item);
                      grouped[bizId].subtotal += item.priceMinor * item.quantity;
                    });

                    return Object.values(grouped).map((group, idx) => (
                      <div key={idx} className="border border-border/40 rounded-lg bg-muted/10 p-2 space-y-1">
                        <div className="text-[9px] font-bold text-muted-foreground uppercase tracking-wider border-b border-border/20 pb-0.5 mb-1 flex justify-between">
                          <span>{group.name}</span>
                          <span className="text-foreground font-semibold">Sub: {formatUSD(group.subtotal)}</span>
                        </div>
                        {group.items.map((item, i) => {
                          const variantLabel = item.variant?.optionValues 
                            ? ` (${Object.values(item.variant.optionValues).join('/')})` 
                            : '';
                          return (
                            <div key={i} className="flex justify-between items-center text-[11px]">
                              <span className="text-muted-foreground truncate flex-1">
                                {item.product.name}{variantLabel} × {item.quantity}
                              </span>
                              <span className="font-medium ml-2 shrink-0">{formatUSD(item.priceMinor * item.quantity)}</span>
                            </div>
                          );
                        })}
                      </div>
                    ));
                  })()}
                </div>
              </details>
            </div>

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
              <Label>Payment Method *</Label>
              <div className="flex gap-4 mt-2">
                <label className="flex items-center gap-2 text-sm cursor-pointer">
                  <input type="radio" name="paymentMethod" value="COD" checked={paymentMethod === 'COD'} onChange={(e) => setPaymentMethod(e.target.value as any)} />
                  Thanh toán khi nhận hàng (COD)
                </label>
                <label className="flex items-center gap-2 text-sm cursor-pointer">
                  <input type="radio" name="paymentMethod" value="BANK_TRANSFER" checked={paymentMethod === 'BANK_TRANSFER'} onChange={(e) => setPaymentMethod(e.target.value as any)} />
                  Chuyển khoản (VietQR)
                </label>
              </div>
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
            {isCustomerRole && (
              <p className="rounded-lg bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
                Your order is saved as Pending. You can pay now, or return to the shop and complete payment later from Orders.
              </p>
            )}
            {error && (
              <div className="rounded-lg bg-destructive/15 px-3 py-2 text-xs text-destructive flex items-center gap-2">
                <AlertCircle className="h-4 w-4" />
                {error}
              </div>
            )}
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
