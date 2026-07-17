import { type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation } from '@tanstack/react-router'
import { logout } from '@/shared/auth'
import { useMeQuery } from '@/shared/api/gen/@tanstack/react-query.gen'
import { Avatar, AvatarFallback } from '@/shared/ui/avatar'
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuLinkItem,
  DropdownMenuItem,
  DropdownMenuSeparator,
} from '@/shared/ui/dropdown-menu'
import {
  ShoppingBag,
  ShoppingCart,
  Receipt,
  User,
  LogOut,
  Sun,
  Moon,
  Sparkles,
} from 'lucide-react'
import { useTheme } from '@/shared/hooks/use-theme'
import { getInitials } from '@/shared/utils/user'
import { useAuthStore } from '@/shared/store/auth-store'
import { cn } from '@/shared/lib/cn'

interface CustomerNavItem {
  to: string
  icon: ReactNode
  label: string
}

export function CustomerLayout({ children }: { children: ReactNode }) {
  const { user } = useAuthStore()
  const { isDark, setIsDark } = useTheme()
  const { t } = useTranslation()
  const { data: me } = useMeQuery()
  const location = useLocation()

  const initials = getInitials(user)
  const displayName = user?.profile.name || user?.profile.email || 'Shopper'

  const handleLogout = async () => {
    await logout()
  }

  const navItems: CustomerNavItem[] = [
    { to: '/shop', icon: <ShoppingBag className="h-5 w-5" />, label: t('nav:shop') },
    { to: '/cart', icon: <ShoppingCart className="h-5 w-5" />, label: t('nav:cart') },
    { to: '/orders', icon: <Receipt className="h-5 w-5" />, label: t('nav:orders') },
    { to: '/profile', icon: <User className="h-5 w-5" />, label: t('nav:profile') },
  ]

  const isNavActive = (path: string) =>
    location.pathname === path ||
    location.pathname.startsWith(`${path}/`) ||
    (path === '/shop' && location.pathname === '/')

  return (
    <div className="flex h-screen flex-col overflow-hidden bg-[#fff8ef] text-stone-950 dark:bg-slate-950 dark:text-slate-50">
      <div className="pointer-events-none fixed inset-0 overflow-hidden">
        <div className="absolute -left-32 -top-32 h-96 w-96 rounded-full bg-orange-300/35 blur-3xl dark:bg-orange-500/15" />
        <div className="absolute right-0 top-16 h-80 w-80 rounded-full bg-rose-300/30 blur-3xl dark:bg-fuchsia-500/10" />
        <div className="absolute bottom-0 left-1/3 h-96 w-96 rounded-full bg-amber-200/35 blur-3xl dark:bg-cyan-500/10" />
      </div>

      <header className="relative z-[80] border-b border-orange-200/70 bg-white/75 px-4 py-3 shadow-sm shadow-orange-100/60 backdrop-blur-xl dark:border-white/10 dark:bg-slate-950/75 dark:shadow-none">
        <div className="mx-auto flex max-w-7xl items-center gap-4">
          <Link to="/shop" className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-gradient-to-br from-orange-500 via-rose-500 to-fuchsia-500 text-white shadow-lg shadow-orange-500/25">
              <ShoppingBag className="h-5 w-5" />
            </div>
            <div className="leading-tight">
              <p className="text-base font-black tracking-tight">V-Store</p>
              <p className="hidden text-[11px] font-semibold uppercase tracking-[0.2em] text-orange-600/80 dark:text-orange-300 sm:block">
                Customer storefront
              </p>
            </div>
          </Link>

          <nav className="hidden items-center gap-1 rounded-full border border-orange-200/80 bg-white/70 p-1 shadow-sm dark:border-white/10 dark:bg-white/5 md:flex">
            {navItems.map((item) => {
              const isActive = isNavActive(item.to)
              return (
                <Link
                  key={item.to}
                  to={item.to}
                  className={cn(
                    'flex items-center gap-2 rounded-full px-4 py-2 text-sm font-bold transition-all',
                    isActive
                      ? 'bg-stone-950 text-white shadow-md shadow-stone-950/15 dark:bg-white dark:text-slate-950'
                      : 'text-stone-600 hover:bg-orange-50 hover:text-stone-950 dark:text-slate-300 dark:hover:bg-white/10 dark:hover:text-white'
                  )}
                >
                  {item.icon}
                  {item.label}
                </Link>
              )
            })}
          </nav>

          <DropdownMenu>
            <DropdownMenuTrigger className="ml-auto flex items-center gap-2 rounded-full border border-orange-200/80 bg-white/75 px-2 py-1.5 text-sm outline-none transition-all hover:shadow-md hover:shadow-orange-100 data-[state=open]:shadow-md dark:border-white/10 dark:bg-white/5 dark:hover:shadow-none">
              <Avatar className="h-9 w-9 ring-2 ring-orange-200 dark:ring-orange-500/20">
                <AvatarFallback className="bg-gradient-to-br from-orange-500 to-rose-500 text-xs font-bold text-white">
                  {initials}
                </AvatarFallback>
              </Avatar>
              <div className="hidden max-w-36 text-left leading-tight sm:block">
                <p className="truncate text-xs font-bold">{displayName}</p>
                <p className="truncate text-[10px] text-stone-500 dark:text-slate-400">
                  {me?.role?.name === 'customer' ? 'Customer' : 'Shopper'}
                </p>
              </div>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-56">
              <div className="px-3 py-2 border-b">
                <p className="text-sm font-semibold">{user?.profile.name || user?.profile.email}</p>
                <p className="text-xs text-muted-foreground truncate">{user?.profile.email}</p>
              </div>
              <DropdownMenuLinkItem
                className="cursor-pointer"
                onClick={() => window.location.href = '/profile'}
              >
                <User className="h-4 w-4" />
                {t('nav:profile')}
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

      <main className="relative z-10 flex-1 overflow-y-auto px-4 py-6 pb-24 md:px-8 md:pb-8">
        <div className="mx-auto max-w-7xl">
          <div className="mb-6 hidden rounded-3xl border border-orange-200/70 bg-white/60 p-4 shadow-sm shadow-orange-100/60 backdrop-blur-xl dark:border-white/10 dark:bg-white/[0.03] dark:shadow-none md:flex md:items-center md:justify-between">
            <div>
              <p className="flex items-center gap-2 text-xs font-black uppercase tracking-[0.24em] text-orange-600 dark:text-orange-300">
                <Sparkles className="h-4 w-4" />
                Made for shopping
              </p>
              <h1 className="mt-1 text-2xl font-black tracking-tight">
                Hi {displayName.split('@')[0]}, ready to browse?
              </h1>
            </div>
            <div className="pointer-events-none rounded-2xl bg-gradient-to-r from-orange-500 to-rose-500 px-4 py-3 text-sm font-bold text-white shadow-lg shadow-orange-500/20">
              Fast checkout · Vouchers · Live stock
            </div>
          </div>
          {children}
        </div>
      </main>

      <nav className="fixed bottom-0 left-0 right-0 z-40 border-t border-orange-200/80 bg-white/90 backdrop-blur-xl dark:border-white/10 dark:bg-slate-950/90 md:hidden">
        <div className="flex items-center justify-around h-16 px-2">
          {navItems.map((item) => {
            const isActive = isNavActive(item.to)
            return (
              <Link
                key={item.to}
                to={item.to}
                className={cn(
                  'flex flex-col items-center justify-center gap-1 px-3 py-1.5 rounded-xl transition-all min-w-[60px]',
                  isActive
                    ? 'text-orange-600 dark:text-orange-300'
                    : 'text-stone-500 hover:text-stone-950 dark:text-slate-400 dark:hover:text-white'
                )}
              >
                <div
                  className={cn(
                    'flex items-center justify-center w-10 h-10 rounded-xl transition-all',
                    isActive && 'bg-orange-500/10 shadow-inner'
                  )}
                >
                  {item.icon}
                </div>
                <span className="text-[10px] font-semibold leading-none">{item.label}</span>
              </Link>
            )
          })}
        </div>
      </nav>
    </div>
  )
}
