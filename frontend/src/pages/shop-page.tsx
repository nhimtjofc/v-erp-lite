import { useState, useMemo } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useList5Query, validateVoucher, useCheckoutMutation, useList3Query, useList2Query, useMeQuery, type Product } from '@/shared/api'
import { useQueryClient } from '@tanstack/react-query'
import { formatUSD } from '@/shared/utils/currency'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Badge } from '@/shared/ui/badge'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/shared/ui/dialog'
import {
  ShoppingCart, Package, Tag, Truck, User, Plus, Minus, X, Search, Flame,
  Sparkles, AlertTriangle, CheckCircle2, ShoppingBag, ChevronRight, Zap,
  AlertCircle, Trash2,
} from 'lucide-react'

interface CartItem {
  product: Product
  quantity: number
  price: number
}

function isOnSale(product: Product): boolean {
  if (!product.salePrice || !product.saleStartsAt || !product.saleEndsAt) return false
  const now = Date.now()
  return now > new Date(product.saleStartsAt).getTime() && now < new Date(product.saleEndsAt).getTime()
}

function getAvailableStock(product: Product): number {
  const stock = product.stockQuantity ?? 0
  const reserved = product.reservedQuantity ?? 0
  return Math.max(0, stock - reserved)
}

export function ShopPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data: products } = useList5Query()
  const { data: customers } = useList3Query()
  const { data: categories } = useList2Query()
  const { data: me } = useMeQuery()
  const role = me?.role?.name
  const canUseOrders = role === 'admin' || role === 'businessOwner'
  const { data: allVouchers } = useQuery({
    queryKey: ['activeVouchers'],
    queryFn: async () => {
      const token = localStorage.getItem('access_token')
      const baseUrl = import.meta.env.VITE_API_BASE_URL || ''
      const res = await fetch(`${baseUrl}/api/vouchers`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      })
      if (!res.ok) return []
      return res.json()
    },
  })

  const [search, setSearch] = useState('')
  const [activeCategory, setActiveCategory] = useState('')
  const [cart, setCart] = useState<CartItem[]>([])
  const [showCart, setShowCart] = useState(false)
  const [showCheckout, setShowCheckout] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  const activeProducts = useMemo(() => {
    if (!products) return []
    return products.filter((p: Product) => p.active)
  }, [products])

  const filteredProducts = useMemo(() => {
    let list = activeProducts
    if (search) {
      const q = search.toLowerCase()
      list = list.filter((p: Product) => p.name?.toLowerCase().includes(q) || p.sku?.toLowerCase().includes(q))
    }
    if (activeCategory) {
      list = list.filter((p: any) => p.category?.id === activeCategory)
    }
    return list
  }, [activeProducts, search, activeCategory])

  const saleProducts = useMemo(() => activeProducts.filter(p => isOnSale(p)), [activeProducts])

  const vouchers = useMemo(() => {
    if (!allVouchers) return []
    return (allVouchers as any[]).filter((v: any) => v.active)
  }, [allVouchers])

  const cartTotal = useMemo(() => cart.reduce((s, i) => s + i.price * i.quantity, 0), [cart])

  function addToCart(product: Product) {
    if (!canUseOrders) return

    const hasVariants = (product.variants?.length ?? 0) > 0
    if (hasVariants) return
    const sale = isOnSale(product)
    const price = sale ? (product.salePrice as number) : (product.basePrice as number)
    const avail = getAvailableStock(product)
    const existing = cart.find(i => i.product.id === product.id)
    const currentQty = existing ? existing.quantity : 0
    if (currentQty >= avail) {
      setError(`Only ${avail} available`)
      return
    }
    setError('')
    if (existing) {
      setCart(cart.map(i => i.product.id === product.id ? { ...i, quantity: i.quantity + 1 } : i))
    } else {
      setCart([...cart, { product, quantity: 1, price }])
    }
    setShowCart(true)
  }

  function updateQty(productId: string, delta: number) {
    setCart(cart.map(i => {
      if (i.product.id !== productId) return i
      const newQty = i.quantity + delta
      if (newQty <= 0) return null
      return { ...i, quantity: newQty }
    }).filter(Boolean) as CartItem[])
  }

  const checkoutMutation = useCheckoutMutation({
    onSuccess: (data: any) => {
      queryClient.invalidateQueries({ queryKey: ['list4'] })
      setCart([])
      setShowCart(false)
      setShowCheckout(false)
      setError('')
      setSuccess('Order placed successfully.')
      const order = data?.data
      if (order?.id && role !== 'customer' && role !== 'user') {
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

  async function handleCheckout() {
    if (!canUseOrders) {
      setError('You do not have permission to use orders')
      setSuccess('')
      return
    }

    if (!customerName.trim() || !customerPhone.trim() || !customerAddress.trim()) {
      setError('Please fill in name, phone, and address')
      setSuccess('')
      return
    }

    let customerId = ''
    if (customers && customers.length > 0) {
      customerId = customers[0].id!
    }

    checkoutMutation.mutate({
      body: {
        customerId,
        shippingName: customerName.trim(),
        shippingPhone: customerPhone.trim(),
        shippingAddress: customerAddress.trim(),
        shippingNote: shippingNote.trim() || undefined,
        items: cart.map(i => ({
          productId: i.product.id!,
          productVariantId: null,
          quantity: i.quantity,
        })) as any,
      },
    })
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Shop</h1>
          <p className="text-sm text-muted-foreground">Browse products and place orders</p>
        </div>
        {canUseOrders && (
          <Button variant="outline" className="relative" onClick={() => setShowCart(true)}>
            <ShoppingCart className="h-4 w-4 mr-2" />
            Cart
            {cart.length > 0 && (
              <span className="ml-1 bg-primary text-primary-foreground text-xs font-bold px-1.5 py-0.5 rounded-full">
                {cart.reduce((s, i) => s + i.quantity, 0)}
              </span>
            )}
          </Button>
        )}
      </div>

      {success && (
        <div className="text-sm font-semibold text-emerald-700 bg-emerald-500/10 p-3 rounded-lg border border-emerald-500/20 flex items-start gap-2">
          <CheckCircle2 className="h-4 w-4 shrink-0 mt-0.5" />
          <div>{success}</div>
        </div>
      )}

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
            <Flame className="h-5 w-5 text-red-500" />
            <h2 className="font-bold text-lg">Hot Sale</h2>
          </div>
          <div className="grid gap-4 grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5">
            {saleProducts.slice(0, 10).map(product => {
              const avail = getAvailableStock(product)
              const discPct = product.salePrice ? Math.round(((Number(product.basePrice) - Number(product.salePrice)) / Number(product.basePrice)) * 100) : 0
              return (
                <Card key={product.id} className={`overflow-hidden ${avail <= 0 ? 'opacity-60' : ''}`}>
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
                      <span className="font-bold text-sm text-red-500">{formatUSD(Number(product.salePrice))}</span>
                      <span className="text-[10px] line-through text-muted-foreground">{formatUSD(Number(product.basePrice))}</span>
                    </div>
                    {canUseOrders && (
                      <Button size="sm" className="w-full mt-2 h-8 text-xs" disabled={avail <= 0} onClick={() => addToCart(product)}>
                        <ShoppingCart className="h-3 w-3 mr-1" />
                        {avail <= 0 ? 'Sold Out' : 'Add to Cart'}
                      </Button>
                    )}
                  </CardContent>
                </Card>
              )
            })}
          </div>
        </div>
      )}

      {/* Vouchers */}
      {vouchers.length > 0 && (
        <div>
          <div className="flex items-center gap-2 mb-3">
            <Tag className="h-5 w-5 text-primary" />
            <h2 className="font-bold text-lg">Available Vouchers</h2>
          </div>
          <div className="grid gap-3 grid-cols-1 sm:grid-cols-2 md:grid-cols-3">
            {vouchers.slice(0, 6).map((voucher: any) => (
              <Card key={voucher.id} className="border-primary/20 bg-primary/5">
                <CardContent className="p-4 flex items-center justify-between">
                  <div>
                    <p className="font-bold font-mono text-primary">{voucher.code}</p>
                    <p className="text-xs text-muted-foreground mt-1">{voucher.name}</p>
                  </div>
                  <Badge variant="outline" className="text-xs">
                    {voucher.discountType === 'PERCENTAGE' ? `${voucher.discountValue}% OFF` : `${formatUSD(Number(voucher.discountValue))} OFF`}
                  </Badge>
                </CardContent>
              </Card>
            ))}
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
            const sale = isOnSale(product)
            return (
              <Card key={product.id} className={`overflow-hidden ${avail <= 0 ? 'opacity-60' : ''}`}>
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
                      {formatUSD(sale ? Number(product.salePrice) : Number(product.basePrice))}
                    </span>
                    {sale && <span className="text-[10px] line-through text-muted-foreground">{formatUSD(Number(product.basePrice))}</span>}
                  </div>
                  <div className="flex items-center justify-between mt-2">
                    <span className={`text-[10px] ${avail <= 3 ? 'text-amber-500' : 'text-muted-foreground'}`}>
                      {avail <= 0 ? 'Sold Out' : `${avail} left`}
                    </span>
                    {canUseOrders && (
                      <Button size="sm" className="h-7 text-xs px-2" disabled={avail <= 0} onClick={() => addToCart(product)}>
                        <Plus className="h-3 w-3" />
                      </Button>
                    )}
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
      {showCart && canUseOrders && (
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
                cart.map(item => (
                  <div key={item.product.id} className="flex gap-3 bg-muted/30 rounded-xl p-3">
                    <div className="h-12 w-12 rounded-lg bg-muted flex items-center justify-center shrink-0 overflow-hidden">
                      {item.product.imageUrl ? (
                        <img src={item.product.imageUrl} alt="" className="w-full h-full object-cover" />
                      ) : (
                        <Package className="h-5 w-5 text-muted-foreground/50" />
                      )}
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-semibold truncate">{item.product.name}</p>
                      <p className="text-xs text-muted-foreground">{formatUSD(item.price)} each</p>
                      <div className="flex items-center justify-between mt-1">
                        <div className="flex items-center gap-1 bg-background border rounded-lg">
                          <button onClick={() => updateQty(item.product.id!, -1)} className="h-6 w-6 flex items-center justify-center hover:bg-muted rounded-l-lg"><Minus className="h-3 w-3" /></button>
                          <span className="w-6 text-center text-xs font-bold">{item.quantity}</span>
                          <button onClick={() => updateQty(item.product.id!, 1)} className="h-6 w-6 flex items-center justify-center hover:bg-muted rounded-r-lg"><Plus className="h-3 w-3" /></button>
                        </div>
                        <span className="font-bold text-sm">{formatUSD(item.price * item.quantity)}</span>
                      </div>
                    </div>
                  </div>
                ))
              )}
            </div>
            {cart.length > 0 && (
              <div className="border-t p-4 space-y-3">
                <div className="flex justify-between font-bold text-base">
                  <span>Total</span>
                  <span className="text-primary">{formatUSD(cartTotal)}</span>
                </div>
                <Button className="w-full" onClick={() => { setShowCheckout(true); setShowCart(false) }}>
                  Checkout <ChevronRight className="h-4 w-4 ml-1" />
                </Button>
              </div>
            )}
          </div>
        </>
      )}

      {/* Checkout Dialog */}
      <Dialog open={showCheckout && canUseOrders} onOpenChange={setShowCheckout}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Checkout</DialogTitle>
            <DialogDescription>Enter your shipping details</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
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
            <div className="border-t pt-3 flex justify-between font-bold text-base">
              <span>Total</span>
              <span className="text-primary">{formatUSD(cartTotal)}</span>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setShowCheckout(false)}>Cancel</Button>
            <Button onClick={handleCheckout} disabled={checkoutMutation.isPending}>
              {checkoutMutation.isPending ? 'Processing...' : `Place Order - ${formatUSD(cartTotal)}`}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
