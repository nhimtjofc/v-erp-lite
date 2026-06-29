import { useState, useEffect, useMemo, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from '@tanstack/react-router'
import {
    useList3Query, // customers
    useList5Query, // products
    useCheckoutMutation,
    validateVoucher,
    type Customer,
    type Product,
    type ProductVariant,
} from '@/shared/api'
import { catalogApi } from '@/shared/api/catalog'
import { useQuery } from '@tanstack/react-query'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Badge } from '@/shared/ui/badge'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/shared/ui/dialog'
import {
    ShoppingCart, User, Truck, Tag, AlertCircle, Trash2, Plus, Minus, ArrowLeft,
    Search, Package, CheckCircle2, Zap, Info, X, ChevronRight, Flame, Sparkles,
    AlertTriangle, ShoppingBag, Star, Filter,
} from 'lucide-react'
import { Link } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { formatUSD } from '@/shared/utils/currency'

// ─── Types ────────────────────────────────────────────────────────────────────

interface CartLine {
    product: Product
    variant?: ProductVariant
    quantity: number
    price: number
    isSalePrice: boolean
    originalPrice: number
}

// ─── Constants ────────────────────────────────────────────────────────────────

const FREE_SHIPPING_THRESHOLD = 100
const FLAT_SHIPPING_FEE = 10
const VAT_RATE = 0.10
const NEW_PRODUCT_DAYS = 14

// ─── Helpers ──────────────────────────────────────────────────────────────────

function isActiveSale(product: Product): boolean {
    if (!product.salePrice || !product.saleStartsAt || !product.saleEndsAt) return false
    const now = Date.now()
    return now > new Date(product.saleStartsAt).getTime() && now < new Date(product.saleEndsAt).getTime()
}

function isNewProduct(product: Product): boolean {
    if (!(product as any).createdAt) return false
    const created = new Date((product as any).createdAt).getTime()
    return Date.now() - created < NEW_PRODUCT_DAYS * 24 * 60 * 60 * 1000
}

function getDiscountPct(base: number, sale: number): number {
    if (!base || !sale || base <= sale) return 0
    return Math.round(((base - sale) / base) * 100)
}

function getAvailableStock(product: Product, variant?: ProductVariant | null): number {
    const stock = (variant ? variant.stockQuantity : product.stockQuantity) ?? 0
    const reserved = (variant ? variant.reservedQuantity : product.reservedQuantity) ?? 0
    return Math.max(0, stock - reserved)
}

function fmt(n: number): string {
    return formatUSD(n)
}

// ─── Product Card ─────────────────────────────────────────────────────────────

interface ProductCardProps {
    product: Product
    onAddToCart: (product: Product) => void
    cartQty: number
}

function ProductCard({ product, onAddToCart, cartQty }: ProductCardProps) {
    const sale = isActiveSale(product)
    const isNew = isNewProduct(product)
    const hasVariants = (product.variants?.length ?? 0) > 0
    const avail = hasVariants
        ? (product.variants ?? []).reduce((sum, v) => sum + Math.max(0, (v.stockQuantity ?? 0) - (v.reservedQuantity ?? 0)), 0)
        : getAvailableStock(product)
    const isOutOfStock = avail <= 0
    const isLowStock = !isOutOfStock && product.lowStockThreshold != null && product.lowStockThreshold > 0 && avail <= product.lowStockThreshold
    const discPct = sale ? getDiscountPct(product.basePrice as number, product.salePrice as number) : 0

    const displayPrice = sale ? product.salePrice : product.basePrice

    return (
        <div className={`group relative bg-card border rounded-xl overflow-hidden transition-all duration-200 hover:shadow-lg hover:-translate-y-0.5 ${
            isOutOfStock ? 'opacity-60' : 'hover:border-primary/40'
        }`}>
            {/* Image / Placeholder */}
            <div className="relative aspect-square bg-gradient-to-br from-muted to-muted/50 overflow-hidden">
                {product.imageUrl ? (
                    <img src={product.imageUrl} alt={product.name ?? ''} className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300" />
                ) : (
                    <div className="w-full h-full flex items-center justify-center">
                        <Package className="h-12 w-12 text-muted-foreground/30" />
                    </div>
                )}

                {/* Badges */}
                <div className="absolute top-2 left-2 flex flex-col gap-1">
                    {sale && (
                        <div className="flex items-center gap-0.5 bg-red-500 text-white text-[10px] font-bold px-1.5 py-0.5 rounded-md shadow">
                            <Flame className="h-2.5 w-2.5" />
                            -{discPct}%
                        </div>
                    )}
                    {isNew && !sale && (
                        <div className="flex items-center gap-0.5 bg-blue-500 text-white text-[10px] font-bold px-1.5 py-0.5 rounded-md shadow">
                            <Sparkles className="h-2.5 w-2.5" />
                            NEW
                        </div>
                    )}
                    {isLowStock && (
                        <div className="flex items-center gap-0.5 bg-amber-500 text-white text-[10px] font-bold px-1.5 py-0.5 rounded-md shadow">
                            <AlertTriangle className="h-2.5 w-2.5" />
                            Sắp hết
                        </div>
                    )}
                    {isOutOfStock && (
                        <div className="bg-gray-600 text-white text-[10px] font-bold px-1.5 py-0.5 rounded-md shadow">
                            Hết hàng
                        </div>
                    )}
                </div>

                {/* Cart qty bubble */}
                {cartQty > 0 && (
                    <div className="absolute top-2 right-2 h-6 w-6 bg-primary text-primary-foreground rounded-full flex items-center justify-center text-[10px] font-bold shadow-lg">
                        {cartQty}
                    </div>
                )}

                {/* Has variants indicator */}
                {hasVariants && (
                    <div className="absolute bottom-2 right-2 bg-background/90 backdrop-blur-sm text-foreground text-[10px] font-semibold px-1.5 py-0.5 rounded border border-border">
                        {product.variants!.length} variants
                    </div>
                )}
            </div>

            {/* Content */}
            <div className="p-3">
                <div className="mb-2">
                    <h3 className="font-semibold text-sm text-foreground line-clamp-2 leading-tight mb-0.5">{product.name}</h3>
                    <p className="text-[10px] text-muted-foreground font-mono">SKU: {product.sku}</p>
                </div>

                {/* Price */}
                <div className="flex items-end justify-between mb-2.5">
                    <div>
                        <div className={`font-bold text-base ${sale ? 'text-red-500' : 'text-foreground'}`}>
                            {fmt(Number(displayPrice))}
                        </div>
                        {sale && (
                            <div className="text-[10px] line-through text-muted-foreground">
                                {fmt(Number(product.basePrice))}
                            </div>
                        )}
                    </div>
                    <div className={`text-[10px] font-medium px-1.5 py-0.5 rounded-full ${
                        isOutOfStock ? 'bg-red-100 text-red-600 dark:bg-red-900/30 dark:text-red-400'
                            : isLowStock ? 'bg-amber-100 text-amber-600 dark:bg-amber-900/30 dark:text-amber-400'
                            : 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400'
                    }`}>
                        {isOutOfStock ? 'Hết' : isLowStock ? `Còn ${avail}` : `${avail} còn`}
                    </div>
                </div>

                {/* Stock bar */}
                {!isOutOfStock && (product.stockQuantity ?? 0) > 0 && (
                    <div className="mb-2.5 h-1 bg-muted rounded-full overflow-hidden">
                        <div
                            className={`h-full rounded-full transition-all ${isLowStock ? 'bg-amber-400' : 'bg-emerald-400'}`}
                            style={{ width: `${Math.min(100, (avail / ((product.stockQuantity ?? 1))) * 100)}%` }}
                        />
                    </div>
                )}

                {/* Add button */}
                <Button
                    size="sm"
                    className="w-full h-8 text-xs font-semibold gap-1.5 transition-all"
                    disabled={isOutOfStock}
                    variant={isOutOfStock ? 'secondary' : 'default'}
                    onClick={(e) => {
                        e.stopPropagation()
                        if (!isOutOfStock) onAddToCart(product)
                    }}
                >
                    <ShoppingCart className="h-3.5 w-3.5" />
                    {isOutOfStock ? 'Hết hàng' : hasVariants ? 'Chọn phiên bản' : 'Thêm vào giỏ'}
                </Button>
            </div>
        </div>
    )
}

// ─── Cart Drawer ──────────────────────────────────────────────────────────────

interface CartDrawerProps {
    open: boolean
    onClose: () => void
    cart: CartLine[]
    onUpdateQty: (index: number, qty: number) => void
    onRemoveItem: (index: number) => void
    onClearCart: () => void
    customers: Customer[] | undefined
    selectedCustomerId: string
    onCustomerChange: (id: string) => void
    shippingName: string
    shippingPhone: string
    shippingAddress: string
    shippingNote: string
    onShippingNameChange: (v: string) => void
    onShippingPhoneChange: (v: string) => void
    onShippingAddressChange: (v: string) => void
    onShippingNoteChange: (v: string) => void
    voucherCode: string
    onVoucherCodeChange: (v: string) => void
    appliedVoucher: string | null
    discountAmount: number
    voucherError: string
    voucherSuccess: string
    isValidatingVoucher: boolean
    onApplyVoucher: () => void
    onRemoveVoucher: () => void
    subtotal: number
    shippingFee: number
    taxAmount: number
    finalAmount: number
    freeShipGap: number
    freeShipProgress: number
    onCheckout: () => void
    isCheckingOut: boolean
    error: string
}

function CartDrawer({
    open, onClose, cart, onUpdateQty, onRemoveItem, onClearCart,
    customers, selectedCustomerId, onCustomerChange,
    shippingName, shippingPhone, shippingAddress, shippingNote,
    onShippingNameChange, onShippingPhoneChange, onShippingAddressChange, onShippingNoteChange,
    voucherCode, onVoucherCodeChange, appliedVoucher, discountAmount,
    voucherError, voucherSuccess, isValidatingVoucher,
    onApplyVoucher, onRemoveVoucher,
    subtotal, shippingFee, taxAmount, finalAmount, freeShipGap, freeShipProgress,
    onCheckout, isCheckingOut, error,
}: CartDrawerProps) {
    const totalQty = cart.reduce((s, i) => s + i.quantity, 0)

    return (
        <>
            {/* Backdrop */}
            {open && (
                <div
                    className="fixed inset-0 z-40 bg-black/40 backdrop-blur-sm"
                    onClick={onClose}
                />
            )}

            {/* Drawer */}
            <div className={`fixed top-0 right-0 h-full z-50 w-full sm:w-[480px] bg-background border-l border-border shadow-2xl flex flex-col transition-transform duration-300 ease-out ${
                open ? 'translate-x-0' : 'translate-x-full'
            }`}>
                {/* Header */}
                <div className="flex items-center justify-between px-4 py-3 border-b border-border bg-card">
                    <div className="flex items-center gap-2">
                        <ShoppingCart className="h-5 w-5 text-primary" />
                        <h2 className="font-bold text-base">Giỏ hàng</h2>
                        {totalQty > 0 && (
                            <span className="bg-primary text-primary-foreground text-xs font-bold px-1.5 py-0.5 rounded-full min-w-[20px] text-center">
                                {totalQty}
                            </span>
                        )}
                    </div>
                    <div className="flex items-center gap-1">
                        {cart.length > 0 && (
                            <Button variant="ghost" size="sm" className="text-destructive hover:text-destructive text-xs h-8" onClick={onClearCart}>
                                <Trash2 className="h-3.5 w-3.5 mr-1" />
                                Xóa tất cả
                            </Button>
                        )}
                        <Button variant="ghost" size="icon" className="h-8 w-8" onClick={onClose}>
                            <X className="h-4 w-4" />
                        </Button>
                    </div>
                </div>

                {/* Scrollable content */}
                <div className="flex-1 overflow-y-auto">
                    {error && (
                        <div className="mx-4 mt-3 text-sm font-semibold text-destructive bg-destructive/10 p-3 rounded-lg border border-destructive/20 flex items-start gap-2">
                            <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
                            <div>{error}</div>
                        </div>
                    )}

                    {/* Cart items */}
                    {cart.length === 0 ? (
                        <div className="flex flex-col items-center justify-center py-16 gap-3 text-center px-6">
                            <div className="h-16 w-16 rounded-full bg-muted flex items-center justify-center">
                                <ShoppingCart className="h-8 w-8 text-muted-foreground/40" />
                            </div>
                            <p className="font-semibold text-foreground">Giỏ hàng trống</p>
                            <p className="text-sm text-muted-foreground">Chọn sản phẩm từ danh mục để thêm vào giỏ</p>
                            <Button variant="outline" size="sm" onClick={onClose}>Duyệt sản phẩm</Button>
                        </div>
                    ) : (
                        <div className="px-4 pt-3 pb-1 space-y-3">
                            {cart.map((line, index) => {
                                const itemSub = line.price * line.quantity
                                const variantName = line.variant
                                    ? Object.entries(line.variant.optionValues ?? {}).map(([_, v]) => v).join(' / ')
                                    : null
                                return (
                                    <div key={index} className="flex gap-3 bg-muted/30 rounded-xl p-3">
                                        {/* Thumbnail */}
                                        <div className="h-14 w-14 rounded-lg bg-muted flex items-center justify-center shrink-0 overflow-hidden border border-border">
                                            {line.product.imageUrl
                                                ? <img src={line.product.imageUrl} alt="" className="w-full h-full object-cover" />
                                                : <Package className="h-6 w-6 text-muted-foreground/50" />
                                            }
                                        </div>

                                        <div className="flex-1 min-w-0">
                                            <div className="flex items-start justify-between gap-2">
                                                <div className="min-w-0">
                                                    <p className="font-semibold text-sm text-foreground truncate">{line.product.name}</p>
                                                    {variantName && <p className="text-xs text-muted-foreground">{variantName}</p>}
                                                    {line.isSalePrice && (
                                                        <div className="flex items-center gap-1 mt-0.5">
                                                            <span className="text-[10px] line-through text-muted-foreground">{fmt(line.originalPrice)}</span>
                                                            <Badge variant="destructive" className="text-[9px] px-1 py-0">
                                                                <Zap className="h-2 w-2 mr-0.5" /> Sale
                                                            </Badge>
                                                        </div>
                                                    )}
                                                </div>
                                                <Button
                                                    type="button"
                                                    variant="ghost"
                                                    size="icon"
                                                    onClick={() => onRemoveItem(index)}
                                                    className="h-6 w-6 text-muted-foreground hover:text-destructive shrink-0"
                                                >
                                                    <X className="h-3.5 w-3.5" />
                                                </Button>
                                            </div>

                                            <div className="flex items-center justify-between mt-2">
                                                <div className="flex items-center gap-1.5 bg-background border border-border rounded-lg p-0.5">
                                                    <button
                                                        type="button"
                                                        onClick={() => onUpdateQty(index, line.quantity - 1)}
                                                        className="h-6 w-6 flex items-center justify-center rounded-md hover:bg-muted transition-colors text-muted-foreground hover:text-foreground"
                                                    >
                                                        <Minus className="h-3 w-3" />
                                                    </button>
                                                    <span className="w-7 text-center text-sm font-bold">{line.quantity}</span>
                                                    <button
                                                        type="button"
                                                        onClick={() => onUpdateQty(index, line.quantity + 1)}
                                                        className="h-6 w-6 flex items-center justify-center rounded-md hover:bg-muted transition-colors text-muted-foreground hover:text-foreground"
                                                    >
                                                        <Plus className="h-3 w-3" />
                                                    </button>
                                                </div>
                                                <span className="font-bold text-sm text-foreground">{fmt(itemSub)}</span>
                                            </div>
                                        </div>
                                    </div>
                                )
                            })}
                        </div>
                    )}

                    {cart.length > 0 && (
                        <div className="px-4 pb-4 space-y-4 mt-2">
                            {/* Free shipping progress */}
                            <div className="rounded-xl border border-border bg-muted/30 p-3 space-y-2">
                                {freeShipGap > 0 ? (
                                    <p className="text-xs text-muted-foreground flex items-center gap-1">
                                        <Truck className="h-3.5 w-3.5 shrink-0 text-primary" />
                                        Thêm <span className="font-bold text-foreground mx-1">{fmt(freeShipGap)}</span> để được <span className="text-primary font-semibold ml-1">miễn phí ship!</span>
                                    </p>
                                ) : (
                                    <p className="text-xs text-emerald-600 dark:text-emerald-400 font-semibold flex items-center gap-1">
                                        <CheckCircle2 className="h-3.5 w-3.5" /> Bạn được miễn phí vận chuyển!
                                    </p>
                                )}
                                <div className="h-2 rounded-full bg-border overflow-hidden">
                                    <div
                                        className="h-full rounded-full bg-gradient-to-r from-blue-500 to-primary transition-all duration-700"
                                        style={{ width: `${freeShipProgress}%` }}
                                    />
                                </div>
                            </div>

                            {/* Voucher */}
                            <div className="space-y-2">
                                <div className="flex items-center gap-1.5 text-xs font-semibold text-muted-foreground uppercase tracking-wide">
                                    <Tag className="h-3.5 w-3.5" />
                                    Mã giảm giá
                                </div>
                                {appliedVoucher ? (
                                    <div className="flex items-center justify-between bg-emerald-50 dark:bg-emerald-900/20 border border-emerald-200 dark:border-emerald-700 rounded-lg px-3 py-2">
                                        <div className="flex items-center gap-2">
                                            <CheckCircle2 className="h-4 w-4 text-emerald-500" />
                                            <div>
                                                <div className="text-xs font-bold text-emerald-700 dark:text-emerald-300 font-mono">{appliedVoucher}</div>
                                                <div className="text-[10px] text-muted-foreground">Tiết kiệm {fmt(discountAmount)}</div>
                                            </div>
                                        </div>
                                        <Button variant="ghost" size="sm" onClick={onRemoveVoucher} className="text-xs text-destructive hover:text-destructive h-7 px-2">
                                            Xóa
                                        </Button>
                                    </div>
                                ) : (
                                    <div className="flex gap-2">
                                        <Input
                                            value={voucherCode}
                                            onChange={(e) => onVoucherCodeChange(e.target.value.toUpperCase())}
                                            onKeyDown={(e) => e.key === 'Enter' && onApplyVoucher()}
                                            placeholder="Nhập mã voucher..."
                                            className="font-mono uppercase h-9 text-xs"
                                            disabled={subtotal === 0 || isValidatingVoucher}
                                        />
                                        <Button
                                            onClick={onApplyVoucher}
                                            variant="secondary"
                                            className="h-9 text-xs px-3 shrink-0"
                                            disabled={subtotal === 0 || isValidatingVoucher || !voucherCode.trim()}
                                        >
                                            {isValidatingVoucher ? '...' : 'Áp dụng'}
                                        </Button>
                                    </div>
                                )}
                                {voucherError && (
                                    <p className="text-xs text-destructive font-medium flex items-center gap-1">
                                        <AlertCircle className="h-3.5 w-3.5" /> {voucherError}
                                    </p>
                                )}
                                {voucherSuccess && !appliedVoucher && (
                                    <p className="text-xs text-emerald-600 dark:text-emerald-400 font-medium">{voucherSuccess}</p>
                                )}
                            </div>

                            {/* Customer */}
                            <div className="space-y-1.5">
                                <div className="flex items-center gap-1.5 text-xs font-semibold text-muted-foreground uppercase tracking-wide">
                                    <User className="h-3.5 w-3.5" />
                                    Khách hàng *
                                </div>
                                <select
                                    className="flex h-9 w-full rounded-lg border border-input bg-transparent px-3 py-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                                    value={selectedCustomerId}
                                    onChange={(e) => onCustomerChange(e.target.value)}
                                >
                                    <option value="">Chọn khách hàng...</option>
                                    {customers?.map((c) => {
                                        const name = [c.firstName, c.lastName].filter(Boolean).join(' ') || 'Unnamed'
                                        return (
                                            <option key={c.id} value={c.id}>
                                                {name} ({c.email || c.phone || 'No contact'})
                                            </option>
                                        )
                                    })}
                                </select>
                            </div>

                            {/* Shipping */}
                            <div className="space-y-2">
                                <div className="flex items-center gap-1.5 text-xs font-semibold text-muted-foreground uppercase tracking-wide">
                                    <Truck className="h-3.5 w-3.5" />
                                    Thông tin giao hàng *
                                </div>
                                <Input
                                    value={shippingName}
                                    onChange={(e) => onShippingNameChange(e.target.value)}
                                    placeholder="Tên người nhận *"
                                    className="h-9 text-sm"
                                />
                                <Input
                                    value={shippingPhone}
                                    onChange={(e) => onShippingPhoneChange(e.target.value)}
                                    placeholder="Số điện thoại *"
                                    className="h-9 text-sm"
                                />
                                <textarea
                                    className="flex min-h-[70px] w-full rounded-lg border border-input bg-transparent px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring resize-none"
                                    value={shippingAddress}
                                    onChange={(e) => onShippingAddressChange(e.target.value)}
                                    placeholder="Địa chỉ giao hàng *"
                                />
                                <textarea
                                    className="flex min-h-[50px] w-full rounded-lg border border-input bg-transparent px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring resize-none"
                                    value={shippingNote}
                                    onChange={(e) => onShippingNoteChange(e.target.value)}
                                    placeholder="Ghi chú cho shipper (không bắt buộc)"
                                />
                            </div>

                            {/* Order summary */}
                            <div className="rounded-xl border border-primary/20 bg-primary/5 p-4 space-y-2">
                                <div className="font-semibold text-sm text-foreground mb-3">Tóm tắt đơn hàng</div>
                                <div className="space-y-1.5 text-sm">
                                    <div className="flex justify-between">
                                        <span className="text-muted-foreground">Tạm tính ({cart.reduce((s, i) => s + i.quantity, 0)} sản phẩm)</span>
                                        <span className="font-medium">{fmt(subtotal)}</span>
                                    </div>
                                    {discountAmount > 0 && (
                                        <div className="flex justify-between text-emerald-600 dark:text-emerald-400">
                                            <span>Giảm giá ({appliedVoucher})</span>
                                            <span>-{fmt(discountAmount)}</span>
                                        </div>
                                    )}
                                    <div className="flex justify-between">
                                        <span className="text-muted-foreground">Phí vận chuyển</span>
                                        <span>
                                            {shippingFee > 0
                                                ? `${fmt(shippingFee)}`
                                                : <span className="text-emerald-600 dark:text-emerald-400 font-semibold">Miễn phí</span>
                                            }
                                        </span>
                                    </div>
                                    <div className="flex justify-between">
                                        <span className="text-muted-foreground">VAT (10%)</span>
                                        <span>{fmt(taxAmount)}</span>
                                    </div>
                                    <div className="border-t border-border/60 pt-2 mt-1 flex justify-between text-base font-bold">
                                        <span>Tổng thanh toán</span>
                                        <span className="text-primary text-lg">{fmt(finalAmount)}</span>
                                    </div>
                                </div>
                            </div>
                        </div>
                    )}
                </div>

                {/* Checkout footer */}
                {cart.length > 0 && (
                    <div className="border-t border-border p-4 bg-card">
                        <Button
                            onClick={onCheckout}
                            className="w-full h-11 text-base font-bold gap-2 shadow-lg"
                            disabled={isCheckingOut}
                        >
                            {isCheckingOut ? (
                                <>
                                    <div className="h-4 w-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                                    Đang xử lý...
                                </>
                            ) : (
                                <>
                                    Đặt hàng · {fmt(finalAmount)}
                                    <ChevronRight className="h-4 w-4" />
                                </>
                            )}
                        </Button>
                    </div>
                )}
            </div>
        </>
    )
}

// ─── Variant Picker Dialog ────────────────────────────────────────────────────

interface VariantPickerProps {
    product: Product | null
    open: boolean
    onClose: () => void
    onConfirm: (product: Product, variant: ProductVariant | undefined, qty: number) => void
}

function VariantPickerDialog({ product, open, onClose, onConfirm }: VariantPickerProps) {
    const [selectedVariantId, setSelectedVariantId] = useState('')
    const [qty, setQty] = useState(1)

    useEffect(() => {
        if (open) {
            setSelectedVariantId('')
            setQty(1)
        }
    }, [open, product])

    if (!product) return null

    const variants = product.variants ?? []
    const selectedVariant = variants.find(v => v.id === selectedVariantId)
    const avail = selectedVariant
        ? getAvailableStock(product, selectedVariant)
        : !variants.length ? getAvailableStock(product) : 0
    const sale = isActiveSale(product)
    const displayPrice = selectedVariant
        ? selectedVariant.price
        : sale ? product.salePrice : product.basePrice

    return (
        <Dialog open={open} onOpenChange={onClose}>
            <DialogContent className="max-w-sm">
                <DialogHeader>
                    <DialogTitle className="text-base">{product.name}</DialogTitle>
                    <DialogDescription className="text-xs text-muted-foreground">SKU: {product.sku}</DialogDescription>
                </DialogHeader>

                <div className="space-y-4 py-1">
                    {variants.length > 0 && (
                        <div className="space-y-2">
                            <Label className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Chọn phiên bản *</Label>
                            <div className="grid grid-cols-1 gap-1.5">
                                {variants.map(v => {
                                    const vAvail = getAvailableStock(product, v)
                                    const opts = Object.entries(v.optionValues ?? {}).map(([k, val]) => `${k}: ${val}`).join(', ')
                                    const isSelected = selectedVariantId === v.id
                                    return (
                                        <button
                                            key={v.id}
                                            disabled={vAvail <= 0}
                                            onClick={() => setSelectedVariantId(v.id!)}
                                            className={`text-left px-3 py-2 rounded-lg border text-sm transition-all ${
                                                isSelected
                                                    ? 'border-primary bg-primary/5 font-semibold'
                                                    : vAvail <= 0
                                                    ? 'border-border opacity-50 cursor-not-allowed'
                                                    : 'border-border hover:border-primary/40 hover:bg-muted/50'
                                            }`}
                                        >
                                            <div className="flex items-center justify-between">
                                                <span>{opts}</span>
                                                <div className="text-right">
                                                    <div className="font-bold text-primary">{fmt(Number(v.price))}</div>
                                                    <div className={`text-[10px] ${vAvail <= 0 ? 'text-destructive' : 'text-muted-foreground'}`}>
                                                        {vAvail <= 0 ? 'Hết hàng' : `${vAvail} còn lại`}
                                                    </div>
                                                </div>
                                            </div>
                                        </button>
                                    )
                                })}
                            </div>
                        </div>
                    )}

                    <div className="flex items-center justify-between">
                        <Label className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Số lượng</Label>
                        <div className="flex items-center gap-2 bg-muted rounded-lg p-1">
                            <button
                                type="button"
                                onClick={() => setQty(q => Math.max(1, q - 1))}
                                className="h-7 w-7 flex items-center justify-center rounded-md bg-background hover:bg-accent transition-colors"
                            >
                                <Minus className="h-3.5 w-3.5" />
                            </button>
                            <span className="w-8 text-center font-bold text-sm">{qty}</span>
                            <button
                                type="button"
                                onClick={() => setQty(q => Math.min(q + 1, avail))}
                                disabled={qty >= avail}
                                className="h-7 w-7 flex items-center justify-center rounded-md bg-background hover:bg-accent transition-colors disabled:opacity-40"
                            >
                                <Plus className="h-3.5 w-3.5" />
                            </button>
                        </div>
                    </div>

                    <div className="flex justify-between items-center bg-muted/50 rounded-lg px-3 py-2">
                        <span className="text-sm text-muted-foreground">Giá</span>
                        <span className="font-bold text-primary text-base">{fmt(Number(displayPrice))}</span>
                    </div>
                </div>

                <DialogFooter>
                    <Button variant="outline" size="sm" onClick={onClose}>Hủy</Button>
                    <Button
                        size="sm"
                        disabled={variants.length > 0 && !selectedVariantId}
                        onClick={() => {
                            onConfirm(product, selectedVariant, qty)
                            onClose()
                        }}
                    >
                        <ShoppingCart className="h-3.5 w-3.5 mr-1.5" />
                        Thêm vào giỏ
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    )
}

// ─── Main Component ────────────────────────────────────────────────────────────

export function CartCheckoutPage() {
    const { t } = useTranslation()
    const navigate = useNavigate()
    const queryClient = useQueryClient()

    // Queries
    const { data: customers } = useList3Query()
    const { data: products } = useList5Query()
    const { data: categories } = useQuery({
        queryKey: ['categories'],
        queryFn: () => catalogApi.listCategories(),
    })
    const { data: tags } = useQuery({
        queryKey: ['tags'],
        queryFn: () => catalogApi.listTags(),
    })

    // Cart
    const [cart, setCart] = useState<CartLine[]>([])
    const [cartOpen, setCartOpen] = useState(false)

    // Filters
    const [search, setSearch] = useState('')
    const [activeCategory, setActiveCategory] = useState('')
    const [activeTag, setActiveTag] = useState('')
    const [activeSection, setActiveSection] = useState<'all' | 'sale' | 'new'>('all')

    // Variant picker
    const [pickerProduct, setPickerProduct] = useState<Product | null>(null)
    const [pickerOpen, setPickerOpen] = useState(false)

    // Customer / Checkout
    const [selectedCustomerId, setSelectedCustomerId] = useState('')
    const [shippingName, setShippingName] = useState('')
    const [shippingPhone, setShippingPhone] = useState('')
    const [shippingAddress, setShippingAddress] = useState('')
    const [shippingNote, setShippingNote] = useState('')

    // Voucher
    const [voucherCode, setVoucherCode] = useState('')
    const [appliedVoucher, setAppliedVoucher] = useState<string | null>(null)
    const [discountAmount, setDiscountAmount] = useState(0)
    const [voucherError, setVoucherError] = useState('')
    const [voucherSuccess, setVoucherSuccess] = useState('')
    const [isValidatingVoucher, setIsValidatingVoucher] = useState(false)

    // Checkout
    const [showConfirmDialog, setShowConfirmDialog] = useState(false)
    const [error, setError] = useState('')

    const checkoutMutation = useCheckoutMutation({
        onSuccess: (data: any) => {
            queryClient.invalidateQueries({ queryKey: ['list4'] })
            const createdOrder = data?.data
            if (createdOrder?.id) {
                navigate({ to: '/orders/$id', params: { id: createdOrder.id } })
            } else {
                navigate({ to: '/orders' })
            }
        },
        onError: (err: any) => {
            setError(err?.body?.message || err?.message || 'Failed to place order')
            setCartOpen(true)
        },
    })

    // Pre-fill shipping on customer change
    useEffect(() => {
        if (selectedCustomerId && customers) {
            const customer = customers.find(c => c.id === selectedCustomerId)
            if (customer) {
                const fullName = [customer.firstName, customer.lastName].filter(Boolean).join(' ') || 'Unnamed'
                setShippingName(fullName)
                setShippingPhone(customer.phone ?? '')
                setShippingAddress(customer.address ?? '')
            }
        } else {
            setShippingName('')
            setShippingPhone('')
            setShippingAddress('')
        }
    }, [selectedCustomerId, customers])

    // Live calculations
    const subtotal = cart.reduce((sum, item) => sum + item.price * item.quantity, 0)

    useEffect(() => {
        if (appliedVoucher && subtotal > 0) {
            handleApplyVoucher(true)
        } else if (subtotal === 0) {
            setAppliedVoucher(null)
            setDiscountAmount(0)
            setVoucherSuccess('')
        }
    }, [subtotal])

    const shippingFee = subtotal > 0 && (subtotal - discountAmount) < FREE_SHIPPING_THRESHOLD ? FLAT_SHIPPING_FEE : 0
    const taxAmount = Math.max(0, (subtotal - discountAmount) * VAT_RATE)
    const finalAmount = Math.max(0, subtotal - discountAmount + shippingFee + taxAmount)
    const amountAfterDiscount = subtotal - discountAmount
    const freeShipGap = Math.max(0, FREE_SHIPPING_THRESHOLD - amountAfterDiscount)
    const freeShipProgress = Math.min(100, (amountAfterDiscount / FREE_SHIPPING_THRESHOLD) * 100)

    const totalCartQty = cart.reduce((s, i) => s + i.quantity, 0)

    // Filtered products
    const activeProducts = useMemo(() => {
        if (!products) return []
        return products.filter(p => p.active)
    }, [products])

    const filteredProducts = useMemo(() => {
        let list = activeProducts
        const q = search.toLowerCase()
        if (q) list = list.filter(p => p.name?.toLowerCase().includes(q) || p.sku?.toLowerCase().includes(q))
        if (activeCategory) list = list.filter(p => (p as any).category?.id === activeCategory)
        if (activeTag) list = list.filter(p => (p as any).tags?.some((t: any) => t.id === activeTag))
        if (activeSection === 'sale') list = list.filter(p => isActiveSale(p))
        if (activeSection === 'new') list = list.filter(p => isNewProduct(p))
        return list
    }, [activeProducts, search, activeCategory, activeTag, activeSection])

    const hotSaleProducts = useMemo(() => activeProducts.filter(p => isActiveSale(p)).slice(0, 4), [activeProducts])
    const newProducts = useMemo(() => activeProducts.filter(p => isNewProduct(p)).slice(0, 4), [activeProducts])

    function getCartQtyForProduct(productId: string): number {
        return cart.filter(l => l.product.id === productId).reduce((s, l) => s + l.quantity, 0)
    }

    function handleProductCardClick(product: Product) {
        const hasVariants = (product.variants?.length ?? 0) > 0
        if (hasVariants) {
            setPickerProduct(product)
            setPickerOpen(true)
        } else {
            addToCart(product, undefined, 1)
        }
    }

    function addToCart(product: Product, variant: ProductVariant | undefined, qty: number) {
        const sale = isActiveSale(product)
        let price = sale ? (product.salePrice as number) : (product.basePrice as number)
        let originalPrice = product.basePrice as number

        if (variant) {
            price = variant.price as number
            originalPrice = variant.price as number
        }

        const avail = getAvailableStock(product, variant)
        const existingIndex = cart.findIndex(
            item => item.product.id === product.id && item.variant?.id === variant?.id
        )
        const existingQty = existingIndex > -1 ? cart[existingIndex].quantity : 0
        const requestedQty = existingQty + qty

        if (avail < requestedQty) {
            setError(`Chỉ còn ${avail} sản phẩm có thể đặt cho "${product.name}"`)
            setCartOpen(true)
            return
        }

        setError('')

        if (existingIndex > -1) {
            const updated = [...cart]
            updated[existingIndex].quantity = requestedQty
            setCart(updated)
        } else {
            setCart(prev => [...prev, {
                product, variant, quantity: qty, price,
                isSalePrice: sale && !variant,
                originalPrice,
            }])
        }

        // briefly open cart
        setCartOpen(true)
        setTimeout(() => setCartOpen(false), 1500)
    }

    function handleUpdateQty(index: number, newQty: number) {
        if (newQty <= 0) {
            handleRemoveItem(index)
            return
        }
        const item = cart[index]
        const avail = getAvailableStock(item.product, item.variant)
        if (avail < newQty) {
            setError(`Chỉ còn ${avail} sản phẩm cho "${item.product.name}"`)
            return
        }
        setError('')
        const updated = [...cart]
        updated[index].quantity = newQty
        setCart(updated)
    }

    function handleRemoveItem(index: number) {
        setCart(prev => prev.filter((_, i) => i !== index))
    }

    async function handleApplyVoucher(silent = false) {
        const codeToApply = voucherCode.trim() || appliedVoucher
        if (!codeToApply) {
            if (!silent) setVoucherError('Vui lòng nhập mã voucher')
            return
        }
        if (!silent) setIsValidatingVoucher(true)
        try {
            const res = await validateVoucher({ query: { code: codeToApply, subtotal: subtotal as any } })
            const result = res.data as any
            if (result.valid) {
                setAppliedVoucher(codeToApply)
                setDiscountAmount(Number(result.discountAmount))
                setVoucherSuccess(`Voucher "${codeToApply}" — tiết kiệm ${fmt(Number(result.discountAmount))}!`)
                setVoucherError('')
            } else {
                setAppliedVoucher(null)
                setDiscountAmount(0)
                setVoucherSuccess('')
                if (!silent) setVoucherError(result.message || 'Voucher không hợp lệ')
            }
        } catch {
            setAppliedVoucher(null)
            setDiscountAmount(0)
            setVoucherSuccess('')
            if (!silent) setVoucherError('Không thể xác thực voucher')
        } finally {
            if (!silent) setIsValidatingVoucher(false)
        }
    }

    function handleRemoveVoucher() {
        setAppliedVoucher(null)
        setDiscountAmount(0)
        setVoucherCode('')
        setVoucherSuccess('')
        setVoucherError('')
    }

    function handleCheckoutClick() {
        if (!selectedCustomerId) { setError('Vui lòng chọn khách hàng'); return }
        if (cart.length === 0) { setError('Giỏ hàng đang trống'); return }
        if (!shippingName.trim() || !shippingPhone.trim() || !shippingAddress.trim()) {
            setError('Vui lòng điền đầy đủ: tên, số điện thoại và địa chỉ giao hàng')
            return
        }
        setError('')
        setCartOpen(false)
        setShowConfirmDialog(true)
    }

    function handleConfirmCheckout() {
        setShowConfirmDialog(false)
        checkoutMutation.mutate({
            body: {
                customerId: selectedCustomerId,
                voucherCode: appliedVoucher ?? undefined,
                shippingName: shippingName.trim(),
                shippingPhone: shippingPhone.trim(),
                shippingAddress: shippingAddress.trim(),
                shippingNote: shippingNote.trim() || undefined,
                items: cart.map(line => ({
                    productId: line.product.id!,
                    productVariantId: line.variant?.id ?? null,
                    quantity: line.quantity,
                })) as any,
            },
        })
    }

    // ─── Section renderer ────────────────────────────────────────────────────

    function ProductGrid({ productList, label, icon: Icon, emptyText }: {
        productList: Product[]
        label?: string
        icon?: React.ElementType
        emptyText?: string
    }) {
        if (productList.length === 0) {
            return emptyText ? (
                <p className="text-sm text-muted-foreground py-4 text-center">{emptyText}</p>
            ) : null
        }
        return (
            <div>
                {label && Icon && (
                    <div className="flex items-center gap-2 mb-3">
                        <Icon className="h-4 w-4 text-primary" />
                        <h2 className="font-bold text-sm text-foreground">{label}</h2>
                        <div className="flex-1 h-px bg-border" />
                        <span className="text-xs text-muted-foreground">{productList.length} sản phẩm</span>
                    </div>
                )}
                <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-3">
                    {productList.map(product => (
                        <ProductCard
                            key={product.id}
                            product={product}
                            cartQty={getCartQtyForProduct(product.id!)}
                            onAddToCart={handleProductCardClick}
                        />
                    ))}
                </div>
            </div>
        )
    }

    return (
        <div className="space-y-5 pb-24">
            {/* Header */}
            <div className="flex items-center gap-3">
                <Link to="/orders" className="text-muted-foreground hover:text-foreground flex items-center gap-1 text-sm font-medium">
                    <ArrowLeft className="h-4 w-4" />
                    Về đơn hàng
                </Link>
                <span className="text-muted-foreground/40">/</span>
                <h1 className="text-lg font-bold">Tạo đơn hàng mới</h1>
            </div>

            {/* Filters */}
            <div className="bg-card border border-border rounded-xl p-3 space-y-3">
                {/* Search */}
                <div className="relative">
                    <Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
                    <input
                        type="text"
                        className="flex h-10 w-full rounded-lg border border-input bg-transparent pl-9 pr-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                        placeholder="Tìm sản phẩm theo tên hoặc SKU..."
                        value={search}
                        onChange={e => setSearch(e.target.value)}
                        autoComplete="off"
                    />
                </div>

                {/* Section pills */}
                <div className="flex items-center gap-2 flex-wrap">
                    {([
                        { key: 'all', label: 'Tất cả', icon: null },
                        { key: 'sale', label: '🔥 Hot Sale', icon: null },
                        { key: 'new', label: '✨ Hàng mới', icon: null },
                    ] as const).map(s => (
                        <button
                            key={s.key}
                            onClick={() => setActiveSection(s.key)}
                            className={`px-3 py-1 rounded-full text-xs font-semibold transition-all ${
                                activeSection === s.key
                                    ? 'bg-primary text-primary-foreground shadow-sm'
                                    : 'bg-muted text-muted-foreground hover:bg-accent hover:text-accent-foreground'
                            }`}
                        >
                            {s.label}
                        </button>
                    ))}

                    {/* Category filter */}
                    {(categories?.length ?? 0) > 0 && (
                        <select
                            className="h-7 rounded-full border border-input bg-transparent px-3 text-xs text-muted-foreground focus-visible:outline-none"
                            value={activeCategory}
                            onChange={e => setActiveCategory(e.target.value)}
                        >
                            <option value="">Danh mục</option>
                            {categories?.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
                        </select>
                    )}

                    {/* Tag filter */}
                    {(tags?.length ?? 0) > 0 && (
                        <select
                            className="h-7 rounded-full border border-input bg-transparent px-3 text-xs text-muted-foreground focus-visible:outline-none"
                            value={activeTag}
                            onChange={e => setActiveTag(e.target.value)}
                        >
                            <option value="">Tag</option>
                            {tags?.map(t => <option key={t.id} value={t.id}>#{t.name}</option>)}
                        </select>
                    )}

                    {(search || activeCategory || activeTag) && (
                        <button
                            onClick={() => { setSearch(''); setActiveCategory(''); setActiveTag('') }}
                            className="px-2 py-1 rounded-full text-[10px] font-medium text-destructive border border-destructive/30 hover:bg-destructive/10 transition-colors"
                        >
                            ✕ Xóa bộ lọc
                        </button>
                    )}

                    <span className="ml-auto text-xs text-muted-foreground">{filteredProducts.length} sản phẩm</span>
                </div>
            </div>

            {/* Product sections */}
            {activeSection === 'all' && !search && !activeCategory && !activeTag ? (
                <div className="space-y-8">
                    {/* Hot Sale section */}
                    {hotSaleProducts.length > 0 && (
                        <div className="bg-gradient-to-r from-red-50 to-orange-50 dark:from-red-900/10 dark:to-orange-900/10 border border-red-200/50 dark:border-red-700/30 rounded-2xl p-4">
                            <ProductGrid
                                productList={hotSaleProducts}
                                label="Hot Sale"
                                icon={Flame}
                            />
                        </div>
                    )}

                    {/* New products section */}
                    {newProducts.length > 0 && (
                        <div className="bg-gradient-to-r from-blue-50 to-indigo-50 dark:from-blue-900/10 dark:to-indigo-900/10 border border-blue-200/50 dark:border-blue-700/30 rounded-2xl p-4">
                            <ProductGrid
                                productList={newProducts}
                                label="Hàng mới về"
                                icon={Sparkles}
                            />
                        </div>
                    )}

                    {/* All products */}
                    <div>
                        <ProductGrid
                            productList={activeProducts}
                            label="Tất cả sản phẩm"
                            icon={ShoppingBag}
                        />
                    </div>
                </div>
            ) : (
                <ProductGrid
                    productList={filteredProducts}
                    emptyText="Không tìm thấy sản phẩm phù hợp"
                />
            )}

            {/* Floating Cart Button */}
            <div className="fixed bottom-6 right-6 z-30">
                <button
                    onClick={() => setCartOpen(true)}
                    className="relative flex items-center gap-2.5 bg-primary text-primary-foreground px-4 py-3 rounded-2xl shadow-xl hover:shadow-2xl transition-all duration-200 hover:-translate-y-0.5 active:scale-95"
                >
                    <ShoppingCart className="h-5 w-5" />
                    <span className="font-bold text-sm">
                        {cart.length === 0 ? 'Giỏ hàng' : `${fmt(finalAmount)}`}
                    </span>
                    {totalCartQty > 0 && (
                        <span className="absolute -top-2 -right-2 h-6 w-6 bg-red-500 text-white text-[10px] font-bold rounded-full flex items-center justify-center shadow-lg animate-bounce">
                            {totalCartQty > 99 ? '99+' : totalCartQty}
                        </span>
                    )}
                </button>
            </div>

            {/* Cart Drawer */}
            <CartDrawer
                open={cartOpen}
                onClose={() => setCartOpen(false)}
                cart={cart}
                onUpdateQty={handleUpdateQty}
                onRemoveItem={handleRemoveItem}
                onClearCart={() => setCart([])}
                customers={customers}
                selectedCustomerId={selectedCustomerId}
                onCustomerChange={setSelectedCustomerId}
                shippingName={shippingName}
                shippingPhone={shippingPhone}
                shippingAddress={shippingAddress}
                shippingNote={shippingNote}
                onShippingNameChange={setShippingName}
                onShippingPhoneChange={setShippingPhone}
                onShippingAddressChange={setShippingAddress}
                onShippingNoteChange={setShippingNote}
                voucherCode={voucherCode}
                onVoucherCodeChange={setVoucherCode}
                appliedVoucher={appliedVoucher}
                discountAmount={discountAmount}
                voucherError={voucherError}
                voucherSuccess={voucherSuccess}
                isValidatingVoucher={isValidatingVoucher}
                onApplyVoucher={() => handleApplyVoucher(false)}
                onRemoveVoucher={handleRemoveVoucher}
                subtotal={subtotal}
                shippingFee={shippingFee}
                taxAmount={taxAmount}
                finalAmount={finalAmount}
                freeShipGap={freeShipGap}
                freeShipProgress={freeShipProgress}
                onCheckout={handleCheckoutClick}
                isCheckingOut={checkoutMutation.isPending}
                error={error}
            />

            {/* Variant Picker */}
            <VariantPickerDialog
                product={pickerProduct}
                open={pickerOpen}
                onClose={() => setPickerOpen(false)}
                onConfirm={(product, variant, qty) => addToCart(product, variant, qty)}
            />

            {/* Order Confirmation Dialog */}
            <Dialog open={showConfirmDialog} onOpenChange={setShowConfirmDialog}>
                <DialogContent className="max-w-md">
                    <DialogHeader>
                        <DialogTitle className="flex items-center gap-2">
                            <CheckCircle2 className="h-5 w-5 text-primary" />
                            Xác nhận đặt hàng
                        </DialogTitle>
                        <DialogDescription>Kiểm tra thông tin trước khi đặt hàng</DialogDescription>
                    </DialogHeader>

                    <div className="space-y-4 py-2">
                        {/* Shipping */}
                        <div className="rounded-xl bg-muted/50 p-3 space-y-1 text-sm border border-border">
                            <div className="font-semibold text-xs text-muted-foreground uppercase tracking-wide mb-2">Giao đến</div>
                            <div className="font-semibold">{shippingName}</div>
                            <div className="text-muted-foreground">{shippingPhone}</div>
                            <div className="text-muted-foreground">{shippingAddress}</div>
                        </div>

                        {/* Items */}
                        <div className="space-y-1.5 text-sm max-h-36 overflow-y-auto">
                            {cart.map((line, i) => (
                                <div key={i} className="flex justify-between items-center text-xs">
                                    <span className="text-muted-foreground truncate flex-1">
                                        {line.product.name}
                                        {line.variant && ` (${Object.values(line.variant.optionValues ?? {}).join('/')})`}
                                        × {line.quantity}
                                    </span>
                                    <span className="font-medium ml-2 shrink-0">{fmt(line.price * line.quantity)}</span>
                                </div>
                            ))}
                        </div>

                        {/* Totals */}
                        <div className="border-t border-border pt-3 space-y-1 text-sm">
                            {discountAmount > 0 && (
                                <div className="flex justify-between text-emerald-600 dark:text-emerald-400 text-xs">
                                    <span>Giảm giá</span><span>-{fmt(discountAmount)}</span>
                                </div>
                            )}
                            <div className="flex justify-between text-xs text-muted-foreground">
                                <span>Phí ship</span>
                                <span>{shippingFee > 0 ? `${fmt(shippingFee)}` : 'Miễn phí'}</span>
                            </div>
                            <div className="flex justify-between text-xs text-muted-foreground">
                                <span>VAT 10%</span><span>{fmt(taxAmount)}</span>
                            </div>
                            <div className="flex justify-between font-bold text-base pt-1 border-t border-border">
                                <span>Tổng cộng</span>
                                <span className="text-primary">{fmt(finalAmount)}</span>
                            </div>
                        </div>
                    </div>

                    <DialogFooter>
                        <Button variant="outline" onClick={() => { setShowConfirmDialog(false); setCartOpen(true) }}>
                            Quay lại
                        </Button>
                        <Button onClick={handleConfirmCheckout} disabled={checkoutMutation.isPending}>
                            {checkoutMutation.isPending ? 'Đang xử lý...' : 'Xác nhận đặt hàng'}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </div>
    )
}
