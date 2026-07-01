import { type ReactNode, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useRouter } from '@tanstack/react-router'
import { logout } from '@/shared/auth'
import { useMeQuery } from '@/shared/api/gen/@tanstack/react-query.gen'
import { navGroups, profileGroup, type NavGroup } from '@/app/nav-registry'
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
import { Menu, X, LogOut, User as UserIcon, Building2, Sun, Moon } from 'lucide-react'
import type { User } from 'oidc-client-ts'
import { useTheme } from '@/shared/hooks/use-theme'
import { getInitials } from '@/shared/utils/user'

import { useAuthStore } from '@/shared/store/auth-store'

export function AppLayout({ children }: { children: ReactNode }) {
  const router = useRouter()
  const { user } = useAuthStore()
  const [mobileOpen, setMobileOpen] = useState(false)
  const { isDark, setIsDark } = useTheme()
  const { t } = useTranslation()
  const { data: me } = useMeQuery()
  const isAdmin = me?.role?.name === 'admin'

  const initials = getInitials(user)

  const handleLogout = async () => {
    await logout()
  }

  const NavLinks = ({ onNavigate }: { onNavigate?: () => void }) => {
    const renderGroup = (group: NavGroup) => (
      <div key={group.labelKey}>
        <div className="mb-1 px-3 text-xs font-semibold uppercase tracking-wider text-sidebar-foreground/50">
          {t(group.labelKey)}
        </div>
        <div className="space-y-0.5">
          {group.items.map((item) => {
            if (item.adminOnly && !isAdmin) return null
            if (item.hideForRoles && me?.role?.name && item.hideForRoles.includes(me.role.name)) return null
            const Icon = item.icon
            return (
              <Link
                key={item.to}
                to={item.to}
                className="flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium text-sidebar-foreground/80 transition-colors hover:bg-sidebar-accent hover:text-sidebar-accent-foreground"
                activeProps={{
                  className:
                    'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium bg-sidebar-accent text-sidebar-accent-foreground transition-colors',
                }}
                onClick={onNavigate}
              >
                <Icon className="h-4 w-4 shrink-0" />
                {t(item.labelKey)}
              </Link>
            )
          })}
        </div>
      </div>
    )

    return (
      <nav className="flex flex-1 flex-col p-3">
        <div className="flex-1 space-y-4 overflow-y-auto">
          {navGroups.map((group) => {
            if (group.adminOnly && !isAdmin) return null
            return renderGroup(group)
          })}
        </div>
        <div className="mt-auto space-y-1 border-t border-sidebar-border pt-3">
          {renderGroup(profileGroup)}
        </div>
      </nav>
    )
  }

  return (
    <div className="flex h-screen overflow-hidden">
      {/* Desktop Sidebar */}
      <aside className="hidden md:flex w-64 flex-col border-r bg-sidebar">
        <div className="flex h-14 items-center border-b border-sidebar-border px-4">
          <Link
            to="/dashboard"
            className="flex items-center gap-2 font-semibold text-sidebar-foreground"
          >
            <div className="flex h-6 w-6 items-center justify-center rounded-md bg-sidebar-primary text-sidebar-primary-foreground">
              <span className="text-xs font-bold">V</span>
            </div>
            <span>{t('nav:appName')}</span>
          </Link>
        </div>
        <NavLinks />
      </aside>

      {/* Mobile Drawer */}
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
            <NavLinks onNavigate={() => setMobileOpen(false)} />
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
            <Menu className="h-5 w-5" />
          </Button>

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
