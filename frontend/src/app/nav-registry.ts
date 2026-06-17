import {
  LayoutDashboard,
  Users,
  Building2,
  Settings,
  User,
  Shield,
  Sun,
  Building,
  type LucideIcon,
} from 'lucide-react'

export interface NavItem {
  /** i18n key in the 'nav' namespace */
  labelKey: string
  to: string
  icon: LucideIcon
}

export interface NavGroup {
  /** i18n key for the section header */
  labelKey: string
  items: NavItem[]
  /** If true, only shown to users with role.name === 'admin' */
  adminOnly?: boolean
}

export const navGroups: NavGroup[] = [
  {
    labelKey: 'nav:businessFeatures',
    items: [
      { labelKey: 'nav:dashboard', to: '/dashboard', icon: LayoutDashboard },
      { labelKey: 'nav:customers', to: '/customers', icon: Users },
    ],
  },
  {
    labelKey: 'nav:businessAdmin',
    items: [
      { labelKey: 'nav:business', to: '/business', icon: Building2 },
      { labelKey: 'nav:settings', to: '/settings/regional', icon: Settings },
    ],
  },
  {
    labelKey: 'nav:systemAdmin',
    adminOnly: true,
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
