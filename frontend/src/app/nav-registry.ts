import {
  LayoutDashboard,
  Users,
  Package,
  ShoppingCart,
  ShoppingBag,
  Building2,
  Settings,
  User,
  Shield,
  Sun,
  Building,
  Receipt,
  Tag,
  type LucideIcon,
} from 'lucide-react'

export interface NavItem {
  /** i18n key in the 'nav' namespace */
  labelKey: string
  to: string
  icon: LucideIcon
  roles?: string[]
  /** Nested submenu items (mechanism preserved for future use) */
  children?: NavItem[]
  /** If true, only shown to admin */
  adminOnly?: boolean
  /** List of roles that cannot see this item */
  hideForRoles?: string[]
}

export interface NavGroup {
  /** i18n key for the section header */
  labelKey: string
  items: NavItem[]
  roles?: string[]
  /** If true, only shown to users with role.name === 'admin' */
  adminOnly?: boolean
}

export const navGroups: NavGroup[] = [
  {
    labelKey: 'nav:businessFeatures',
    items: [
      { labelKey: 'nav:dashboard', to: '/dashboard', icon: LayoutDashboard },
      { labelKey: 'nav:shop', to: '/shop', icon: ShoppingBag },
      { labelKey: 'nav:customers', to: '/customers', icon: Users, hideForRoles: ['customer'] },
      { labelKey: 'nav:products', to: '/products', icon: ShoppingBag, hideForRoles: ['customer'] },
      { labelKey: 'nav:orders', to: '/orders', icon: Receipt },
      { labelKey: 'nav:vouchers', to: '/vouchers', icon: Tag, hideForRoles: ['customer'] },
      { labelKey: 'nav:cart', to: '/cart', icon: ShoppingCart },
    ],
  },
  {
    labelKey: 'nav:businessAdmin',
    roles: ['operator', 'businessOwner'],
    items: [
      { labelKey: 'nav:business', to: '/business', icon: Building2, hideForRoles: ['customer'] },
      { labelKey: 'nav:settings', to: '/settings/regional', icon: Settings, hideForRoles: ['customer'] },
    ],
  },
  {
    labelKey: 'nav:systemAdmin',
    adminOnly: true,
    roles: ['systemAdmin'],
    items: [
      { labelKey: 'nav:tenants', to: '/admin/tenants', icon: Building },
    ],
  },
]

export const profileGroup: NavGroup = {
  labelKey: 'nav:profile',
  items: [
    { labelKey: 'nav:personalInfo', to: '/profile', icon: User },
    { labelKey: 'nav:preferences', to: '/profile/preferences', icon: Sun },
    { labelKey: 'nav:security', to: '/profile/security', icon: Shield },
  ],
}
