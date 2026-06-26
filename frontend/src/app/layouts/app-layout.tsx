import { type ReactNode, useEffect, useState, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useRouter, useLocation } from '@tanstack/react-router'
import { logout, getUser } from '@/shared/auth'
import { useMeQuery } from '@/shared/api/gen/@tanstack/react-query.gen'
import { navGroups, profileGroup, type NavItem } from '@/app/nav-registry'
import { Button } from '@/shared/ui/button'
import { Avatar, AvatarFallback } from '@/shared/ui/avatar'
import {
    DropdownMenu,
    DropdownMenuTrigger,
    DropdownMenuContent,
    DropdownMenuLinkItem,
    DropdownMenuItem,
    DropdownMenuSeparator,
    DropdownMenuGroup,
    DropdownMenuGroupLabel,
} from '@/shared/ui/dropdown-menu'
import { SimpleTooltip } from '@/shared/ui/tooltip'
import { CollapsibleSection } from '@/shared/ui/collapsible'
import { cn } from '@/shared/lib/cn'
import { Menu as BaseMenu } from '@base-ui/react/menu'
import {
    Menu as MenuIcon,
    X,
    LogOut,
    User as UserIcon,
    Building2,
    Sun,
    Moon,
    ChevronLeft,
    ChevronRight,
    ChevronDown,
} from 'lucide-react'
import type { User } from 'oidc-client-ts'
import { useTheme } from '@/shared/hooks/use-theme'
import { getInitials } from '@/shared/utils/user'

/* ------------------------------------------------------------------ */
/*  Base UI Menu recursive item list for collapsed sidebar popups      */
/* ------------------------------------------------------------------ */

const popupClasses =
    'z-50 min-w-52 rounded-lg border bg-popover p-1.5 shadow-xl ' +
    'data-[state=open]:animate-in data-[state=closed]:animate-out ' +
    'data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 ' +
    'data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95'

function MenuItemList({
    items,
    onNavigate,
}: {
    items: NavItem[]
    onNavigate?: () => void
}) {
    const { t } = useTranslation()
    const location = useLocation()
    const router = useRouter()

    return items.map((item) => {
        const hasChildren = item.children && item.children.length > 0
        const isActive =
            location.pathname === item.to ||
            (hasChildren && item.children!.some((c) => location.pathname === c.to))

        if (hasChildren) {
            return (
                <BaseMenu.SubmenuRoot key={item.to + ':' + item.labelKey}>
                    <BaseMenu.SubmenuTrigger
                        className={cn(
                            'flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-sm transition-colors cursor-default',
                            isActive
                                ? 'bg-accent text-accent-foreground font-medium'
                                : 'text-popover-foreground hover:bg-accent hover:text-accent-foreground'
                        )}
                    >
                        <item.icon className="h-4 w-4 shrink-0" />
                        {t(item.labelKey)}
                        <ChevronRight className="ml-auto h-3.5 w-3.5 shrink-0 opacity-70" />
                    </BaseMenu.SubmenuTrigger>
                    <BaseMenu.Portal>
                        <BaseMenu.Positioner align="start" side="right" sideOffset={8}>
                            <BaseMenu.Popup className={popupClasses}>
                                <BaseMenu.Viewport className="flex flex-col gap-0.5">
                                    <MenuItemList items={item.children!} onNavigate={onNavigate} />
                                </BaseMenu.Viewport>
                            </BaseMenu.Popup>
                        </BaseMenu.Positioner>
                    </BaseMenu.Portal>
                </BaseMenu.SubmenuRoot>
            )
        }

        return (
            <BaseMenu.Item
                key={item.to + ':' + item.labelKey}
                className={cn(
                    'flex items-center gap-2 rounded-md px-2 py-1.5 text-sm transition-colors cursor-default',
                    location.pathname === item.to
                        ? 'bg-accent text-accent-foreground font-medium'
                        : 'text-popover-foreground hover:bg-accent hover:text-accent-foreground'
                )}
                onClick={() => {
                    router.navigate({ to: item.to })
                    onNavigate?.()
                }}
            >
                <item.icon className="h-4 w-4 shrink-0" />
                {t(item.labelKey)}
            </BaseMenu.Item>
        )
    })
}

/* ------------------------------------------------------------------ */
/*  Nav item helpers                                                   */
/* ------------------------------------------------------------------ */

function SidebarNavItem({
    item,
    collapsed,
    onNavigate,
}: {
    item: NavItem
    collapsed: boolean
    onNavigate?: () => void
}) {
    const location = useLocation()
    const { t } = useTranslation()
    const [popupOpen, setPopupOpen] = useState(false)
    const [inlineOpen, setInlineOpen] = useState(false)
    const hasChildren = item.children && item.children.length > 0
    const isActive =
        location.pathname === item.to ||
        (hasChildren && item.children?.some((c) => location.pathname === c.to))

    const baseClasses = cn(
        'flex items-center gap-3 rounded-md text-sm font-medium transition-colors',
        'focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-sidebar-ring',
        isActive
            ? 'bg-sidebar-accent text-sidebar-accent-foreground'
            : 'text-sidebar-foreground/80 hover:bg-sidebar-accent hover:text-sidebar-accent-foreground'
    )

    // Collapsed mode
    if (collapsed) {
        if (hasChildren) {
            return (
                <BaseMenu.Root open={popupOpen} onOpenChange={setPopupOpen}>
                    <BaseMenu.Trigger
                        className={cn(baseClasses, 'justify-center px-3 py-2.5', isActive && 'bg-sidebar-accent')}
                        aria-label={t(item.labelKey)}
                    >
                        <item.icon className="h-4 w-4 shrink-0" />
                    </BaseMenu.Trigger>
                    <BaseMenu.Portal>
                        <BaseMenu.Positioner align="start" side="right" sideOffset={8}>
                            <BaseMenu.Popup className={popupClasses}>
                                <BaseMenu.Viewport className="flex flex-col gap-0.5">
                                    <MenuItemList items={item.children!} onNavigate={onNavigate} />
                                </BaseMenu.Viewport>
                            </BaseMenu.Popup>
                        </BaseMenu.Positioner>
                    </BaseMenu.Portal>
                </BaseMenu.Root>
            )
        }

        return (
            <SimpleTooltip content={t(item.labelKey)} side="right" sideOffset={12}>
                <Link
                    to={item.to}
                    className={cn(baseClasses, 'justify-center px-3 py-2.5')}
                    activeProps={{ className: cn(baseClasses, 'justify-center px-3 py-2.5 bg-sidebar-accent text-sidebar-accent-foreground') }}
                    onClick={onNavigate}
                >
                    <item.icon className="h-4 w-4 shrink-0" />
                </Link>
            </SimpleTooltip>
        )
    }

    // Expanded mode
    if (hasChildren) {
        return (
            <CollapsibleSection
                open={inlineOpen}
                onOpenChange={setInlineOpen}
                triggerClassName={cn(baseClasses, 'w-full px-3 py-2 text-left')}
                triggerContent={
                    <>
                        <item.icon className="h-4 w-4 shrink-0" />
                        <span className="whitespace-nowrap">{t(item.labelKey)}</span>
                        <ChevronDown
                            className={cn(
                                'ml-auto h-3.5 w-3.5 shrink-0 transition-transform duration-200',
                                inlineOpen && 'rotate-180'
                            )}
                        />
                    </>
                }
            >
                <div className="ml-4 border-l border-sidebar-border pl-3 pt-1">
                    {item.children!.map((child) => {
                        const childActive = location.pathname === child.to
                        return (
                            <Link
                                key={child.to + ':' + child.labelKey}
                                to={child.to}
                                className={cn(
                                    'flex items-center gap-2 rounded-md px-2 py-1.5 text-sm transition-colors',
                                    childActive
                                        ? 'bg-sidebar-accent text-sidebar-accent-foreground font-medium'
                                        : 'text-sidebar-foreground/70 hover:bg-sidebar-accent hover:text-sidebar-accent-foreground'
                                )}
                                onClick={onNavigate}
                            >
                                <child.icon className="h-4 w-4 shrink-0" />
                                {t(child.labelKey)}
                            </Link>
                        )
                    })}
                </div>
            </CollapsibleSection>
        )
    }

    return (
        <Link
            to={item.to}
            className={cn(baseClasses, 'px-3 py-2')}
            activeProps={{
                className: cn(
                    baseClasses,
                    'px-3 py-2 bg-sidebar-accent text-sidebar-accent-foreground'
                ),
            }}
            onClick={onNavigate}
        >
            <item.icon className="h-4 w-4 shrink-0" />
            <span className="whitespace-nowrap">{t(item.labelKey)}</span>
        </Link>
    )
}

/* ------------------------------------------------------------------ */
/*  Breadcrumb                                                          */
/* ------------------------------------------------------------------ */

/** Build a flat path → labelKey map from the nav tree.
 *  Leaf items take precedence when paths collide (e.g., Settings parent
 *  and Regional child both point to `/settings/regional` — child wins). */
function buildPathMap(): Map<string, string> {
    const map = new Map<string, string>()
    const walk = (items: NavItem[]) => {
        for (const item of items) {
            // Walk children first so leaf labels win over parent section labels
            if (item.children) walk(item.children)
            if (!map.has(item.to)) {
                map.set(item.to, item.labelKey)
            }
        }
    }
    for (const group of navGroups) walk(group.items)
    walk(profileGroup.items)
    return map
}

/**
 * Map a parent directory prefix to its section label.
 * e.g., `/settings` → `nav:settings`  (via Settings item with children)
 *        `/admin`   → `nav:systemAdmin` (via group label)
 */
function buildDirMap(): Map<string, string> {
    const map = new Map<string, string>()
    // Items with children define a parent section
    for (const group of [...navGroups, profileGroup]) {
        for (const item of group.items) {
            if (item.children && item.to) {
                const dir = '/' + item.to.split('/')[1]
                map.set(dir, item.labelKey)
            }
        }
    }
    // Groups define section label for their first-level directory
    for (const group of [...navGroups, profileGroup]) {
        if (group.items.length > 0) {
            const dir = '/' + group.items[0].to.split('/')[1]
            if (!map.has(dir)) map.set(dir, group.labelKey)
        }
    }
    return map
}

const PATH_MAP = buildPathMap()
const DIR_MAP = buildDirMap()

function Breadcrumbs({ pathname }: { pathname: string }) {
    const { t } = useTranslation()
    const segments = pathname.split('/').filter(Boolean)

    const crumbs: { label: string; to: string }[] = []
    let acc = ''

    for (const segment of segments) {
        acc += '/' + segment
        const labelKey = PATH_MAP.get(acc) ?? DIR_MAP.get(acc)
        if (!labelKey) continue
        const label = t(labelKey)
        // Skip if the same label would appear consecutively
        if (crumbs.length > 0 && crumbs[crumbs.length - 1].label === label) continue
        crumbs.push({ label, to: acc })
    }

    if (crumbs.length === 0) return null

    return (
        <nav aria-label="Breadcrumb" className="ml-3 flex items-center gap-1.5 text-sm text-muted-foreground">
            {crumbs.map((crumb, i) => (
                <span key={crumb.to} className="flex items-center gap-1.5">
                    {i > 0 && <ChevronRight className="h-3.5 w-3.5 shrink-0" />}
                    {i < crumbs.length - 1 ? (
                        <Link to={crumb.to} className="hover:text-foreground transition-colors">
                            {crumb.label}
                        </Link>
                    ) : (
                        <span className="text-foreground font-medium">{crumb.label}</span>
                    )}
                </span>
            ))}
        </nav>
    )
}

/* ------------------------------------------------------------------ */
/*  Main layout                                                        */
/* ------------------------------------------------------------------ */

export function AppLayout({ children }: { children: ReactNode }) {
    const router = useRouter()
    const location = useLocation()
    const [user, setUser] = useState<User | null>(null)
    const [mobileOpen, setMobileOpen] = useState(false)
    const [collapsed, setCollapsed] = useState(() => {
        try {
            return localStorage.getItem('sidebar:collapsed') === 'true'
        } catch {
            return false
        }
    })
    const { isDark, setIsDark } = useTheme()
    const { t } = useTranslation()
    const { data: me } = useMeQuery()
    const isAdmin = me?.role?.name === 'admin'

    useEffect(() => {
        getUser().then(setUser)
    }, [])

    const toggleCollapsed = useCallback(() => {
        setCollapsed((prev) => {
            const next = !prev
            try {
                localStorage.setItem('sidebar:collapsed', String(next))
            } catch {
                // ignore
            }
            return next
        })
    }, [])

    const initials = getInitials(user)

    const handleLogout = async () => {
        await logout()
    }

    const renderGroup = (group: { labelKey: string; items: NavItem[]; adminOnly?: boolean }) => (
        <div key={group.labelKey}>
            <div
                className={cn(
                    'mb-1 px-3 text-xs font-semibold uppercase tracking-wider text-sidebar-foreground/50 transition-all duration-200',
                    collapsed && 'h-0 opacity-0 mb-0 overflow-hidden'
                )}
                aria-hidden={collapsed}
            >
                {t(group.labelKey)}
            </div>
            <div className="space-y-0.5">
                {group.items.map((item) => (
                    <SidebarNavItem
                        key={item.to}
                        item={item}
                        collapsed={collapsed}
                        onNavigate={undefined}
                    />
                ))}
            </div>
        </div>
    )

    return (
        <div className="flex h-screen overflow-hidden">
            {/* Desktop Sidebar */}
            <aside
                className={cn(
                    'relative hidden md:flex flex-col border-r bg-sidebar transition-[width] duration-300 ease-[cubic-bezier(0.4,0,0.2,1)]',
                    collapsed ? 'w-16' : 'w-64'
                )}
                data-collapsed={collapsed}
            >
                {/* Header with toggle */}
                <div
                    className={cn(
                        'flex h-14 shrink-0 items-center border-b border-sidebar-border transition-all duration-300',
                        collapsed ? 'justify-center px-2' : 'justify-between px-4'
                    )}
                >
                    {collapsed ? (
                        <button
                            onClick={toggleCollapsed}
                            className="flex h-10 w-10 items-center justify-center rounded-md text-sidebar-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground transition-colors"
                            aria-label="Expand sidebar"
                        >
                            <ChevronRight className="h-4 w-4" />
                        </button>
                    ) : (
                        <>
                            <Link
                                to="/dashboard"
                                className="flex items-center gap-2 font-semibold text-sidebar-foreground"
                            >
                                <div className="flex h-6 w-6 shrink-0 items-center justify-center rounded-md bg-sidebar-primary text-sidebar-primary-foreground">
                                    <span className="text-xs font-bold">V</span>
                                </div>
                                <span className="whitespace-nowrap transition-opacity duration-200">
                                    {t('nav:appName')}
                                </span>
                            </Link>
                            <button
                                onClick={toggleCollapsed}
                                className="flex h-8 w-8 items-center justify-center rounded-md text-sidebar-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground transition-colors"
                                aria-label="Collapse sidebar"
                            >
                                <ChevronLeft className="h-4 w-4" />
                            </button>
                        </>
                    )}
                </div>

                {/* Navigation */}
                <nav className="flex flex-1 flex-col overflow-y-auto overflow-x-hidden p-3">
                    <div className="flex-1 space-y-4">
                        {navGroups.map((group) => {
                            if (group.adminOnly && !isAdmin) return null
                            return renderGroup(group)
                        })}
                    </div>
                    <div className="mt-auto space-y-1 border-t border-sidebar-border pt-3">
                        {renderGroup(profileGroup)}
                    </div>
                </nav>

            </aside>

            {/* Mobile Drawer — always expanded */}
            {mobileOpen && (
                <>
                    <div
                        className="fixed inset-0 z-40 bg-black/50 backdrop-blur-sm md:hidden"
                        onClick={() => setMobileOpen(false)}
                    />
                    <aside className="fixed inset-y-0 left-0 z-50 flex w-64 flex-col border-r bg-sidebar shadow-xl md:hidden">
                        <div className="flex h-14 items-center justify-between border-b border-sidebar-border px-4">
                            <Link
                                to="/dashboard"
                                className="flex items-center gap-2 font-semibold text-sidebar-foreground"
                                onClick={() => setMobileOpen(false)}
                            >
                                <div className="flex h-6 w-6 items-center justify-center rounded-md bg-sidebar-primary text-sidebar-primary-foreground">
                                    <span className="text-xs font-bold">V</span>
                                </div>
                                <span>{t('nav:appName')}</span>
                            </Link>
                            <Button
                                variant="ghost"
                                size="icon"
                                className="h-8 w-8 text-sidebar-foreground"
                                onClick={() => setMobileOpen(false)}
                            >
                                <X className="h-4 w-4" />
                            </Button>
                        </div>
                        <nav className="flex flex-1 flex-col p-3">
                            <div className="flex-1 space-y-4 overflow-y-auto">
                                {navGroups.map((group) => {
                                    if (group.adminOnly && !isAdmin) return null
                                    return (
                                        <div key={group.labelKey}>
                                            <div className="mb-1 px-3 text-xs font-semibold uppercase tracking-wider text-sidebar-foreground/50">
                                                {t(group.labelKey)}
                                            </div>
                                            <div className="space-y-0.5">
                                                {group.items.map((item) => (
                                                    <SidebarNavItem
                                                        key={item.to}
                                                        item={item}
                                                        collapsed={false}
                                                        onNavigate={() => setMobileOpen(false)}
                                                    />
                                                ))}
                                            </div>
                                        </div>
                                    )
                                })}
                            </div>
                            <div className="mt-auto space-y-1 border-t border-sidebar-border pt-3">
                                <div>
                                    <div className="mb-1 px-3 text-xs font-semibold uppercase tracking-wider text-sidebar-foreground/50">
                                        {t(profileGroup.labelKey)}
                                    </div>
                                    <div className="space-y-0.5">
                                        {profileGroup.items.map((item) => (
                                            <SidebarNavItem
                                                key={item.to}
                                                item={item}
                                                collapsed={false}
                                                onNavigate={() => setMobileOpen(false)}
                                            />
                                        ))}
                                    </div>
                                </div>
                            </div>
                        </nav>
                    </aside>
                </>
            )}

            {/* Main Content Area */}
            <div className="flex flex-1 flex-col overflow-hidden">
                <header className="sticky top-0 z-30 flex h-14 items-center gap-4 border-b bg-background px-4">
                    <Button
                        variant="ghost"
                        size="icon"
                        className="md:hidden"
                        onClick={() => setMobileOpen(true)}
                        aria-label="Open menu"
                    >
                        <MenuIcon className="h-5 w-5" />
                    </Button>

                    <Breadcrumbs pathname={location.pathname} />

                    <div className="ml-auto flex items-center gap-3">
                        <DropdownMenu>
                            <DropdownMenuTrigger className="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm outline-none transition-colors hover:bg-accent data-[state=open]:bg-accent">
                                <Avatar className="h-7 w-7">
                                    <AvatarFallback className="text-xs">{initials}</AvatarFallback>
                                </Avatar>
                                {user && (
                                    <span className="hidden text-sm text-muted-foreground sm:inline">
                                        {user.profile.email}
                                    </span>
                                )}
                            </DropdownMenuTrigger>
                            <DropdownMenuContent>
                                {user && (
                                    <DropdownMenuGroup>
                                        <DropdownMenuGroupLabel>
                                            {user.profile.name || user.profile.email}
                                        </DropdownMenuGroupLabel>
                                        <div className="border-b px-2 pb-1 mb-1">
                                            <p className="text-xs text-muted-foreground">{user.profile.email}</p>
                                        </div>
                                    </DropdownMenuGroup>
                                )}
                                <DropdownMenuLinkItem
                                    className="cursor-pointer"
                                    onClick={() => router.navigate({ to: '/profile' })}
                                >
                                    <UserIcon className="h-4 w-4" />
                                    {t('nav:profile')}
                                </DropdownMenuLinkItem>
                                <DropdownMenuLinkItem
                                    className="cursor-pointer"
                                    onClick={() => router.navigate({ to: '/business' })}
                                >
                                    <Building2 className="h-4 w-4" />
                                    {t('nav:business')}
                                </DropdownMenuLinkItem>
                                <DropdownMenuSeparator />
                                <DropdownMenuItem
                                    className="cursor-pointer"
                                    onClick={() => setIsDark(!isDark)}
                                >
                                    {isDark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
                                    {isDark ? t('nav:lightMode') : t('nav:darkMode')}
                                </DropdownMenuItem>
                                <DropdownMenuSeparator />
                                <DropdownMenuItem
                                    className="cursor-pointer text-destructive focus:text-destructive data-[highlighted]:text-destructive"
                                    onClick={handleLogout}
                                >
                                    <LogOut className="h-4 w-4" />
                                    {t('nav:logout')}
                                </DropdownMenuItem>
                            </DropdownMenuContent>
                        </DropdownMenu>
                    </div>
                </header>

                <main className="flex-1 overflow-y-auto p-6">{children}</main>
            </div>
        </div>
    )
}
