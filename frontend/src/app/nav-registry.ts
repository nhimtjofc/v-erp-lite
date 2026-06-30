import {
  LayoutDashboard,
  Users,
  Building2,
  Settings,
  User,
  Shield,
  Sun,
  Building,
  ShoppingBag,
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
      { labelKey: 'nav:dashboard', to: '/dashboard', icon: LayoutDashboard, roles: ['admin', 'operator', 'businessOwner'] },
      { labelKey: 'nav:shop', to: '/shop', icon: ShoppingBag, roles: ['customer', 'user', 'operator', 'businessOwner'] },
      { labelKey: 'nav:customers', to: '/customers', icon: Users, roles: ['admin', 'operator', 'businessOwner'] },
      { labelKey: 'nav:products', to: '/products', icon: ShoppingBag, roles: ['admin', 'operator', 'businessOwner'] },
      { labelKey: 'nav:orders', to: '/orders', icon: Receipt, roles: ['admin', 'businessOwner'] },
      { labelKey: 'nav:vouchers', to: '/vouchers', icon: Tag, roles: ['admin', 'operator', 'businessOwner'] },
    ],
  },
  {
    labelKey: 'nav:businessAdmin',
    roles: ['operator', 'businessOwner'],
    items: [
      { labelKey: 'nav:business', to: '/business', icon: Building2 },
      { labelKey: 'nav:settings', to: '/settings/regional', icon: Settings },
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
