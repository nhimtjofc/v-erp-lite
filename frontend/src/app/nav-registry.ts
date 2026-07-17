import {
  LayoutDashboard,
  Users,
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
  Wallet,
  Banknote,
  Globe,
  Coins,
  ToggleRight,
  MessageSquare,
  PlugZap,
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
    roles: ['admin', 'operator', 'businessOwner'],
    items: [
      { labelKey: 'nav:business', to: '/business', icon: Building2, hideForRoles: ['customer', 'operator'] },
      {
        labelKey: 'nav:settings',
        to: '/settings/regional',
        icon: Settings,
        hideForRoles: ['customer'],
        children: [
          { labelKey: 'nav:regional', to: '/settings/regional', icon: Globe },
          { labelKey: 'nav:currency', to: '/settings/currency', icon: Coins },
          { labelKey: 'nav:features', to: '/settings/features', icon: ToggleRight },
          { labelKey: 'nav:communication', to: '/settings/communication', icon: MessageSquare },
          { labelKey: 'nav:security', to: '/settings/security', icon: Shield },
          { labelKey: 'nav:integrations', to: '/settings/integrations', icon: PlugZap },
        ],
      },
    ],
  },
  {
    labelKey: 'nav:finance',
    items: [
      // Seller wallet: business staff only (matches WalletResource roles).
      { labelKey: 'nav:wallet', to: '/wallet', icon: Wallet, roles: ['businessOwner'] },
      // Admin payout approval dashboard: admin only.
      { labelKey: 'nav:payouts', to: '/admin/payouts', icon: Banknote, adminOnly: true },
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
