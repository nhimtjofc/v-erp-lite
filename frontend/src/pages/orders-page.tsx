import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from '@tanstack/react-router'
import { useList4Query, type Order } from '@/shared/api'
import { Button } from '@/shared/ui/button'
import { Badge } from '@/shared/ui/badge'
import { Input } from '@/shared/ui/input'
import { Link } from '@tanstack/react-router'
import { formatUSD } from '@/shared/utils/currency'
import {
    Plus, Search, Eye, TrendingUp, ShoppingBag, Clock, CheckCircle2,
    Package, ChevronRight, Truck, XCircle, Filter, RotateCcw,
    ArrowUpRight,
} from 'lucide-react'

// ─── Stats via direct fetch ────────────────────────────────────────────────────

interface OrderStats {
    totalRevenue: number
    totalOrders: number
    countByStatus: Record<string, number>
}

async function fetchOrderStats(): Promise<OrderStats | null> {
    const token = localStorage.getItem('access_token')
    const baseUrl = import.meta.env.VITE_API_BASE_URL || ''
    try {
        const res = await fetch(`${baseUrl}/api/orders/stats`, {
            headers: token ? { Authorization: `Bearer ${token}` } : {},
        })
        if (!res.ok) return null
        return res.json()
    } catch {
        return null
    }
}

// ─── Status config ─────────────────────────────────────────────────────────────

const STATUS_CONFIG: Record<string, {
    label: string
    color: string
    bg: string
    dot: string
    icon: React.ElementType
    badgeVariant: string
}> = {
    PENDING: {
        label: 'Pending', color: 'text-amber-600 dark:text-amber-400',
        bg: 'bg-amber-50 dark:bg-amber-900/20', dot: 'bg-amber-500',
        icon: Clock, badgeVariant: 'warning',
    },
    CONFIRMED: {
        label: 'Confirmed', color: 'text-blue-600 dark:text-blue-400',
        bg: 'bg-blue-50 dark:bg-blue-900/20', dot: 'bg-blue-500',
        icon: CheckCircle2, badgeVariant: 'info',
    },
    SHIPPED: {
        label: 'Shipped', color: 'text-orange-600 dark:text-orange-400',
        bg: 'bg-orange-50 dark:bg-orange-900/20', dot: 'bg-orange-500',
        icon: Truck, badgeVariant: 'orange',
    },
    COMPLETED: {
        label: 'Completed', color: 'text-emerald-600 dark:text-emerald-400',
        bg: 'bg-emerald-50 dark:bg-emerald-900/20', dot: 'bg-emerald-500',
        icon: CheckCircle2, badgeVariant: 'success',
    },
    CANCELLED: {
        label: 'Cancelled', color: 'text-red-500 dark:text-red-400',
        bg: 'bg-red-50 dark:bg-red-900/20', dot: 'bg-red-500',
        icon: XCircle, badgeVariant: 'destructive',
    },
}

const STATUS_TABS = [
    { key: '', label: 'All Orders' },
    { key: 'PENDING', label: 'Pending' },
    { key: 'CONFIRMED', label: 'Confirmed' },
    { key: 'SHIPPED', label: 'Shipped' },
    { key: 'COMPLETED', label: 'Completed' },
    { key: 'CANCELLED', label: 'Cancelled' },
] as const

function getStatusCfg(status: string) {
    return STATUS_CONFIG[status?.toUpperCase()] ?? {
        label: status, color: 'text-muted-foreground',
        bg: 'bg-muted', dot: 'bg-muted-foreground',
        icon: Clock, badgeVariant: 'secondary',
    }
}

function getInitials(firstName?: string, lastName?: string): string {
    const f = (firstName ?? '').charAt(0).toUpperCase()
    const l = (lastName ?? '').charAt(0).toUpperCase()
    return (f + l) || '?'
}

const AVATAR_COLORS = [
    'from-violet-500 to-purple-600',
    'from-blue-500 to-cyan-600',
    'from-emerald-500 to-teal-600',
    'from-orange-500 to-amber-600',
    'from-pink-500 to-rose-600',
    'from-indigo-500 to-blue-600',
]

function getAvatarColor(name: string): string {
    let hash = 0
    for (let i = 0; i < name.length; i++) hash = name.charCodeAt(i) + ((hash << 5) - hash)
    return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length]
}

// ─── Order Card Component ──────────────────────────────────────────────────────

function OrderCard({ order, onClick }: { order: Order; onClick: () => void }) {
    const customerFirst = order.customer?.firstName ?? ''
    const customerLast = order.customer?.lastName ?? ''
    const customerName = [customerFirst, customerLast].filter(Boolean).join(' ') || 'Unnamed'
    const initials = getInitials(customerFirst, customerLast)
    const avatarColor = getAvatarColor(customerName)
    const itemCount = order.items?.length ?? 0
    const status = order.status?.toUpperCase() ?? 'PENDING'
    const cfg = getStatusCfg(status)
    const Icon = cfg.icon
    const orderDate = new Date(order.orderDate!)
    const isToday = new Date().toDateString() === orderDate.toDateString()

    const firstItemName = order.items?.[0]?.product?.name ?? ''

    return (
        <div
            onClick={onClick}
            className="group relative bg-card border border-border rounded-xl p-4 hover:border-primary/40 hover:shadow-md transition-all duration-200 cursor-pointer"
        >
            {/* Top row */}
            <div className="flex items-start justify-between gap-3 mb-3">
                <div className="flex items-center gap-3">
                    {/* Customer avatar */}
                    <div className={`h-10 w-10 rounded-full bg-gradient-to-br ${avatarColor} flex items-center justify-center text-white text-xs font-bold shrink-0 shadow-sm`}>
                        {initials}
                    </div>
                    <div className="min-w-0">
                        <div className="font-semibold text-sm text-foreground truncate">{customerName}</div>
                        <div className="text-xs text-muted-foreground">{order.customer?.phone || order.customer?.email || 'No contact'}</div>
                    </div>
                </div>

                {/* Status badge */}
                <div className={`flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold ${cfg.bg} ${cfg.color} shrink-0`}>
                    <span className={`h-1.5 w-1.5 rounded-full ${cfg.dot} ${status === 'PENDING' ? 'animate-pulse' : ''}`} />
                    {cfg.label}
                </div>
            </div>

            {/* Order info row */}
            <div className="flex items-center gap-2 mb-3">
                <Package className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
                <span className="text-xs text-muted-foreground truncate flex-1">
                    {firstItemName || 'No items'}
                    {itemCount > 1 && ` +${itemCount - 1} more`}
                </span>
                <span className="text-xs text-muted-foreground shrink-0 bg-muted px-1.5 py-0.5 rounded">
                    {itemCount} item{itemCount !== 1 ? 's' : ''}
                </span>
            </div>

            {/* Bottom row */}
            <div className="flex items-center justify-between">
                <div>
                    <div className="font-mono text-xs text-primary font-bold">{order.orderNumber}</div>
                    <div className="text-xs text-muted-foreground mt-0.5">
                        {isToday
                            ? `Today ${orderDate.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`
                            : orderDate.toLocaleDateString('en-US', { day: '2-digit', month: '2-digit', year: '2-digit' })
                        }
                    </div>
                </div>
                <div className="text-right">
                    <div className="font-bold text-foreground">
                        {formatUSD(Number(order.finalAmount))}
                    </div>
                    <div className="text-xs text-muted-foreground">Total</div>
                </div>
            </div>

            {/* Hover arrow */}
            <div className="absolute top-3 right-3 opacity-0 group-hover:opacity-100 transition-opacity">
                <ChevronRight className="h-4 w-4 text-primary" />
            </div>
        </div>
    )
}

// ─── Stats Card ───────────────────────────────────────────────────────────────

function StatCard({
    label, value, sub, icon: Icon, gradient, trend,
}: {
    label: string
    value: string
    sub?: string
    icon: React.ElementType
    gradient: string
    trend?: string
}) {
    return (
        <div className="relative overflow-hidden bg-card border border-border rounded-xl p-4 hover:shadow-md transition-shadow">
            <div className="flex items-start justify-between">
                <div className="min-w-0">
                    <p className="text-xs text-muted-foreground font-medium mb-1">{label}</p>
                    <p className="font-bold text-lg text-foreground leading-tight truncate">{value}</p>
                    {sub && <p className="text-xs text-muted-foreground mt-0.5">{sub}</p>}
                    {trend && (
                        <div className="flex items-center gap-1 mt-1">
                            <ArrowUpRight className="h-3 w-3 text-emerald-500" />
                            <span className="text-xs text-emerald-600 dark:text-emerald-400 font-medium">{trend}</span>
                        </div>
                    )}
                </div>
                <div className={`p-2.5 rounded-xl bg-gradient-to-br ${gradient} shadow-sm`}>
                    <Icon className="h-4 w-4 text-white" />
                </div>
            </div>
        </div>
    )
}

// ─── Main Component ────────────────────────────────────────────────────────────

export function OrdersPage() {
    const { t } = useTranslation()
    const navigate = useNavigate()

    const [search, setSearch] = useState('')
    const [debouncedSearch, setDebouncedSearch] = useState('')
    const [activeStatus, setActiveStatus] = useState('')
    const [stats, setStats] = useState<OrderStats | null>(null)
    const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid')

    useEffect(() => {
        const timer = setTimeout(() => setDebouncedSearch(search), 300)
        return () => clearTimeout(timer)
    }, [search])

    useEffect(() => {
        fetchOrderStats().then(setStats)
    }, [])

    const queryParams: any = {}
    if (debouncedSearch) queryParams.search = debouncedSearch
    if (activeStatus) queryParams.status = activeStatus

    const { data: orders, isLoading } = useList4Query(
        Object.keys(queryParams).length > 0 ? { query: queryParams } : undefined
    )
    const orderList = orders ?? []

    const totalRevenue = stats?.totalRevenue ?? 0
    const revenueStr = formatUSD(totalRevenue)

    return (
        <div className="space-y-5">

            {/* ── Header ── */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <div>
                    <h1 className="text-2xl font-bold tracking-tight">Đơn hàng</h1>
                    <p className="text-muted-foreground text-sm mt-0.5">
                        Quản lý và theo dõi toàn bộ đơn hàng bán ra
                    </p>
                </div>
                <Link to="/orders/new">
                    <Button className="gap-2 shadow-sm">
                        <Plus className="h-4 w-4" />
                        Tạo đơn mới
                    </Button>
                </Link>
            </div>

            {/* ── Stats ── */}
            {stats && (
                <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
                    <StatCard
                        label="Doanh thu"
                        value={revenueStr}
                        icon={TrendingUp}
                        gradient="from-violet-500 to-purple-600"
                        trend="Tổng tích lũy"
                    />
                    <StatCard
                        label="Tổng đơn"
                        value={stats.totalOrders.toString()}
                        sub={`${stats.countByStatus.COMPLETED ?? 0} hoàn thành`}
                        icon={ShoppingBag}
                        gradient="from-blue-500 to-cyan-600"
                    />
                    <StatCard
                        label="Chờ xử lý"
                        value={((stats.countByStatus.PENDING ?? 0) + (stats.countByStatus.CONFIRMED ?? 0)).toString()}
                        sub="Cần hành động"
                        icon={Clock}
                        gradient="from-amber-500 to-orange-600"
                    />
                    <StatCard
                        label="Hoàn thành"
                        value={(stats.countByStatus.COMPLETED ?? 0).toString()}
                        sub={`${stats.countByStatus.CANCELLED ?? 0} đã hủy`}
                        icon={CheckCircle2}
                        gradient="from-emerald-500 to-teal-600"
                    />
                </div>
            )}

            {/* ── Filter + Search ── */}
            <div className="bg-card border border-border rounded-xl overflow-hidden">
                {/* Search bar */}
                <div className="flex items-center gap-2 p-3 border-b border-border">
                    <div className="relative flex-1 max-w-sm">
                        <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
                        <Input
                            placeholder="Tìm kiếm đơn hàng, tên KH, số điện thoại..."
                            className="pl-8 h-9"
                            value={search}
                            onChange={(e) => setSearch(e.target.value)}
                        />
                    </div>
                    {search && (
                        <Button variant="ghost" size="icon" className="h-9 w-9" onClick={() => setSearch('')}>
                            <RotateCcw className="h-4 w-4" />
                        </Button>
                    )}
                    <div className="ml-auto flex items-center gap-1">
                        <Button
                            variant={viewMode === 'grid' ? 'secondary' : 'ghost'}
                            size="icon"
                            className="h-8 w-8"
                            onClick={() => setViewMode('grid')}
                            title="Grid view"
                        >
                            <div className="grid grid-cols-2 gap-0.5 w-3 h-3">
                                {[0,1,2,3].map(i => <div key={i} className="bg-current rounded-[1px]" />)}
                            </div>
                        </Button>
                        <Button
                            variant={viewMode === 'list' ? 'secondary' : 'ghost'}
                            size="icon"
                            className="h-8 w-8"
                            onClick={() => setViewMode('list')}
                            title="List view"
                        >
                            <div className="space-y-1 w-3">
                                {[0,1,2].map(i => <div key={i} className="h-0.5 bg-current rounded-full" />)}
                            </div>
                        </Button>
                    </div>
                </div>

                {/* Status tabs */}
                <div className="flex items-center gap-0.5 px-3 py-2 overflow-x-auto">
                    {STATUS_TABS.map((tab) => {
                        const count = tab.key
                            ? (stats?.countByStatus[tab.key] ?? 0)
                            : (stats?.totalOrders ?? orderList.length)
                        const isActive = activeStatus === tab.key
                        const cfg = tab.key ? getStatusCfg(tab.key) : null
                        return (
                            <button
                                key={tab.key}
                                onClick={() => setActiveStatus(tab.key)}
                                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium whitespace-nowrap transition-all ${
                                    isActive
                                        ? 'bg-primary text-primary-foreground shadow-sm'
                                        : 'text-muted-foreground hover:bg-muted hover:text-foreground'
                                }`}
                            >
                                {cfg && (
                                    <span className={`h-1.5 w-1.5 rounded-full ${isActive ? 'bg-primary-foreground' : cfg.dot}`} />
                                )}
                                {tab.label}
                                {stats && (
                                    <span className={`text-[10px] px-1.5 py-0.5 rounded-full font-bold leading-none ${
                                        isActive
                                            ? 'bg-primary-foreground/20 text-primary-foreground'
                                            : 'bg-muted text-muted-foreground'
                                    }`}>
                                        {count}
                                    </span>
                                )}
                            </button>
                        )
                    })}
                </div>

                {/* Content */}
                <div className="p-3">
                    {isLoading ? (
                        /* Skeleton */
                        <div className={viewMode === 'grid'
                            ? 'grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3'
                            : 'space-y-2'
                        }>
                            {Array.from({ length: 6 }).map((_, i) => (
                                <div key={i} className="animate-pulse bg-muted rounded-xl h-[120px]" />
                            ))}
                        </div>
                    ) : orderList.length === 0 ? (
                        <div className="text-center py-16 space-y-3">
                            <div className="mx-auto w-16 h-16 rounded-full bg-muted flex items-center justify-center">
                                <ShoppingBag className="h-8 w-8 text-muted-foreground/50" />
                            </div>
                            <div>
                                <p className="font-semibold text-foreground">Không tìm thấy đơn hàng</p>
                                <p className="text-sm text-muted-foreground mt-1">
                                    {activeStatus
                                        ? `Không có đơn hàng nào ở trạng thái "${getStatusCfg(activeStatus).label}"`
                                        : 'Chưa có đơn hàng nào. Hãy tạo đơn đầu tiên!'
                                    }
                                </p>
                            </div>
                            {!activeStatus && (
                                <Link to="/orders/new">
                                    <Button size="sm" className="mt-1 gap-2">
                                        <Plus className="h-4 w-4" /> Tạo đơn mới
                                    </Button>
                                </Link>
                            )}
                        </div>
                    ) : viewMode === 'grid' ? (
                        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                            {orderList.map((order) => (
                                <OrderCard
                                    key={order.id!}
                                    order={order}
                                    onClick={() => navigate({ to: '/orders/$id', params: { id: order.id! } })}
                                />
                            ))}
                        </div>
                    ) : (
                        /* List view */
                        <div className="divide-y divide-border">
                            {orderList.map((order) => {
                                const customerFirst = order.customer?.firstName ?? ''
                                const customerLast = order.customer?.lastName ?? ''
                                const customerName = [customerFirst, customerLast].filter(Boolean).join(' ') || 'Unnamed'
                                const status = order.status?.toUpperCase() ?? 'PENDING'
                                const cfg = getStatusCfg(status)
                                const itemCount = order.items?.length ?? 0

                                return (
                                    <div
                                        key={order.id!}
                                        className="flex items-center gap-4 py-3 px-1 hover:bg-muted/50 rounded-lg cursor-pointer group transition-colors"
                                        onClick={() => navigate({ to: '/orders/$id', params: { id: order.id! } })}
                                    >
                                        <div className={`h-8 w-8 rounded-full bg-gradient-to-br ${getAvatarColor(customerName)} flex items-center justify-center text-white text-[10px] font-bold shrink-0`}>
                                            {getInitials(customerFirst, customerLast)}
                                        </div>
                                        <div className="flex-1 min-w-0">
                                            <div className="flex items-center gap-2">
                                                <span className="font-mono text-xs font-bold text-primary">{order.orderNumber}</span>
                                                <span className="text-sm font-medium truncate">{customerName}</span>
                                            </div>
                                            <div className="text-xs text-muted-foreground">
                                                {itemCount} item{itemCount !== 1 ? 's' : ''} · {new Date(order.orderDate!).toLocaleDateString('en-US')}
                                            </div>
                                        </div>
                                        <div className={`hidden sm:flex items-center gap-1.5 px-2 py-0.5 rounded-full text-xs font-semibold ${cfg.bg} ${cfg.color}`}>
                                            <span className={`h-1.5 w-1.5 rounded-full ${cfg.dot}`} />
                                            {cfg.label}
                                        </div>
                                        <div className="text-right shrink-0">
                                            <div className="font-bold text-sm">{formatUSD(Number(order.finalAmount))}</div>
                                        </div>
                                        <ChevronRight className="h-4 w-4 text-muted-foreground opacity-0 group-hover:opacity-100 transition-opacity" />
                                    </div>
                                )
                            })}
                        </div>
                    )}
                </div>

                {/* Footer */}
                {orderList.length > 0 && (
                    <div className="px-4 py-2.5 border-t border-border bg-muted/30 text-xs text-muted-foreground flex items-center justify-between">
                        <span>Hiển thị {orderList.length} đơn hàng</span>
                        {search && (
                            <button
                                onClick={() => setSearch('')}
                                className="text-primary hover:underline"
                            >
                                Xóa bộ lọc
                            </button>
                        )}
                    </div>
                )}
            </div>
        </div>
    )
}
