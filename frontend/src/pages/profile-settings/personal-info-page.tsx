import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { getUser } from '@/shared/auth'
import { useMeQuery, useUpdateMeMutation, useGetMyCustomerProfileQuery } from '@/shared/api'
import { useSellerWalletQuery } from '@/shared/api/wallet'
import { Badge } from '@/shared/ui/badge'
import { Avatar, AvatarFallback } from '@/shared/ui/avatar'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Separator } from '@/shared/ui/separator'
import type { User as OidcUser } from 'oidc-client-ts'
import QRCode from 'qrcode'
import {
  Briefcase,
  Link,
  Mail,
  Phone,
  Clock,
  User as UserIcon,
  Pencil,
  Check,
  X,
  AlertTriangle,
  Shield,
  Crown,
  Star,
  Sparkles,
  QrCode,
  CreditCard,
  Wallet,
} from 'lucide-react'
import { Link as RouterLink } from '@tanstack/react-router'
import { formatUSD, formatMoneyMinor } from '@/shared/utils/currency'

const TIMEZONES = [
  'UTC',
  'America/New_York',
  'America/Chicago',
  'America/Denver',
  'America/Los_Angeles',
  'Europe/London',
  'Europe/Paris',
  'Europe/Berlin',
  'Asia/Tokyo',
  'Asia/Shanghai',
  'Asia/Singapore',
  'Australia/Sydney',
  'Pacific/Auckland',
]

function getInitials(oidcUser: OidcUser | null) {
  if (oidcUser?.profile?.name) {
    return oidcUser.profile.name
      .split(' ')
      .map((n) => n[0])
      .join('')
      .toUpperCase()
      .slice(0, 2)
  }
  return oidcUser?.profile?.email?.[0]?.toUpperCase() ?? '?'
}

function getDisplayName(oidcUser: OidcUser | null) {
  return (
    oidcUser?.profile?.name ||
    oidcUser?.profile?.preferred_username ||
    'User'
  )
}

export function PersonalInfoPage() {
  const [oidcUser, setOidcUser] = useState<OidcUser | null>(null)
  const { data: profile, isLoading } = useMeQuery()
  const updateMeMutation = useUpdateMeMutation()
  const { t } = useTranslation()

  const [flipped, setFlipped] = useState(false)
  const [cardQrUrl, setCardQrUrl] = useState('')
  const { data: customerProfile } = useGetMyCustomerProfileQuery()
  const { data: userWallet } = useSellerWalletQuery()

  useEffect(() => {
    if (!customerProfile?.cardNumber) return
    QRCode.toDataURL(customerProfile.cardNumber, {
      width: 140,
      margin: 1,
      color: {
        dark: '#0f172a',
        light: '#ffffff',
      }
    })
      .then(setCardQrUrl)
      .catch((err) => console.error('Card QR generation failed:', err))
  }, [customerProfile?.cardNumber])

  const [editing, setEditing] = useState(false)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState<{
    type: 'success' | 'error'
    text: string
  } | null>(null)

  // Form fields
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [phone, setPhone] = useState('')
  const [jobTitle, setJobTitle] = useState('')
  const [bio, setBio] = useState('')
  const [timezone, setTimezone] = useState('UTC')
  const [websiteUrl, setWebsiteUrl] = useState('')

  useEffect(() => {
    getUser().then(setOidcUser)
  }, [])

  useEffect(() => {
    if (profile) {
      setFirstName(profile.firstName ?? '')
      setLastName(profile.lastName ?? '')
      setPhone(profile.phone ?? '')
      setJobTitle(profile.jobTitle ?? '')
      setBio(profile.bio ?? '')
      setTimezone(profile.timezone ?? 'UTC')
      setWebsiteUrl(profile.websiteUrl ?? '')
    }
  }, [profile])

  function resetFields() {
    if (!profile) return
    setFirstName(profile.firstName ?? '')
    setLastName(profile.lastName ?? '')
    setPhone(profile.phone ?? '')
    setJobTitle(profile.jobTitle ?? '')
    setBio(profile.bio ?? '')
    setTimezone(profile.timezone ?? 'UTC')
    setWebsiteUrl(profile.websiteUrl ?? '')
  }

  async function handleSave() {
    setSaving(true)
    setMessage(null)
    try {
      await updateMeMutation.mutateAsync({
        body: {
          firstName: firstName || undefined,
          lastName: lastName || undefined,
          phone: phone || undefined,
          jobTitle: jobTitle || undefined,
          bio: bio || undefined,
          websiteUrl: websiteUrl || undefined,
          timezone: timezone || undefined,
        },
      })
      setEditing(false)
      setMessage({ type: 'success', text: t('profile:profileUpdated') })
    } catch {
      setMessage({ type: 'error', text: t('profile:profileUpdateFailed') })
    } finally {
      setSaving(false)
      setTimeout(() => setMessage(null), 4000)
    }
  }

  function handleCancel() {
    resetFields()
    setEditing(false)
    setMessage(null)
  }

  const initials = getInitials(oidcUser)
  const displayName = getDisplayName(oidcUser)

  // Card customization helpers & variables
  const totalSpendMinor = customerProfile?.totalSpendMinor ?? 0
  const points = customerProfile?.points ?? 0
  const tier = customerProfile?.tier ?? 'SILVER'
  const cardNumber = customerProfile?.cardNumber ?? 'MEM-0000-0000'

  let nextTier = ''
  let spendNeeded = 0
  let progressPercent = 0
  let progressText = ''

  if (tier === 'SILVER') {
    nextTier = 'GOLD'
    spendNeeded = Math.max(0, 200 - totalSpendMinor)
    progressPercent = Math.min(100, (totalSpendMinor / 200) * 100)
    progressText = `Tiêu thêm $${spendNeeded.toFixed(2)} để thăng hạng VÀNG`
  } else if (tier === 'GOLD') {
    nextTier = 'PLATINUM'
    spendNeeded = Math.max(0, 1000 - totalSpendMinor)
    progressPercent = Math.min(100, ((totalSpendMinor - 200) / (1000 - 200)) * 100)
    progressText = `Tiêu thêm $${spendNeeded.toFixed(2)} để thăng hạng BẠCH KIM`
  } else if (tier === 'PLATINUM') {
    nextTier = 'DIAMOND'
    spendNeeded = Math.max(0, 3000 - totalSpendMinor)
    progressPercent = Math.min(100, ((totalSpendMinor - 1000) / (3000 - 1000)) * 100)
    progressText = `Tiêu thêm $${spendNeeded.toFixed(2)} để thăng hạng KIM CƯƠNG`
  } else {
    progressPercent = 100
    progressText = 'Chúc mừng! Bạn đã đạt hạng thẻ cao nhất VIP KIM CƯƠNG.'
  }

  const getCardBg = () => {
    switch (tier) {
      case 'GOLD':
        return 'from-amber-400 via-amber-500 to-yellow-600 shadow-amber-500/20'
      case 'PLATINUM':
        return 'from-cyan-600 via-sky-700 to-slate-900 shadow-cyan-500/20'
      case 'DIAMOND':
        return 'from-neutral-900 via-slate-950 to-indigo-950 shadow-indigo-950/40 border-indigo-900/30'
      case 'SILVER':
      default:
        return 'from-slate-400 via-slate-500 to-slate-600 shadow-slate-500/20'
    }
  }

  const getTierBadgeColor = () => {
    switch (tier) {
      case 'GOLD':
        return 'bg-amber-400/20 text-amber-200 border-amber-400/30'
      case 'PLATINUM':
        return 'bg-cyan-400/20 text-cyan-200 border-cyan-400/30'
      case 'DIAMOND':
        return 'bg-purple-400/20 text-purple-200 border-purple-400/30'
      case 'SILVER':
      default:
        return 'bg-slate-400/20 text-slate-200 border-slate-400/30'
    }
  }

  if (isLoading) {
    return (
      <p className="text-muted-foreground py-12 text-center">Loading profile...</p>
    )
  }

  return (
    <div className="mx-auto max-w-3xl">
      {/* Page Header */}
      <div className="flex items-start justify-between gap-4 pb-8">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('nav:personalInfo')}</h1>
          <p className="text-muted-foreground mt-1">
            {t('profile:description')}
          </p>
        </div>
        {!editing && (
          <Button
            variant="outline"
            size="sm"
            onClick={() => setEditing(true)}
            className="shrink-0 gap-2"
          >
            <Pencil className="h-3.5 w-3.5" />
            {t('common:edit')}
          </Button>
        )}
      </div>

      {/* Style for 3D flip card and styling */}
      <style>{`
        .perspective-1000 {
          perspective: 1000px;
        }
        .preserve-3d {
          transform-style: preserve-3d;
        }
        .backface-hidden {
          backface-visibility: hidden;
          -webkit-backface-visibility: hidden;
        }
        .rotate-y-180 {
          transform: rotateY(180deg);
        }
      `}</style>

      {/* Membership Card Section */}
      {customerProfile && (
        <section className="mb-8 border-b border-border/50 pb-8">
          <div className="flex items-center gap-2 mb-6">
            <CreditCard className="h-5 w-5 text-primary" />
            <h2 className="text-xl font-bold tracking-tight">Thẻ hội viên VIP điện tử</h2>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-8 items-center">
            {/* 3D Rotating Card Container */}
            <div className="flex justify-center">
              <div 
                className="w-[340px] h-[210px] cursor-pointer perspective-1000 group relative"
                onClick={() => setFlipped(!flipped)}
              >
                <div className={`w-full h-full duration-700 preserve-3d relative ${flipped ? 'rotate-y-180' : ''}`}>
                  
                  {/* Card Front Side */}
                  <div className={`absolute inset-0 w-full h-full rounded-2xl p-6 flex flex-col justify-between backface-hidden shadow-xl transition-all border ${getCardBg()}`}>
                    {/* Glossy overlay effect */}
                    <div className="absolute inset-0 rounded-2xl bg-gradient-to-tr from-transparent via-white/5 to-white/10 pointer-events-none" />
                    
                    {/* Top Row: App Logo and Tier Badge */}
                    <div className="flex justify-between items-start z-10">
                      <div>
                        <p className="text-xs uppercase tracking-widest font-black opacity-80">V-ERP Lite</p>
                        <p className="text-[9px] uppercase tracking-widest font-semibold opacity-60">Loyalty Program</p>
                      </div>
                      <span className={`px-2.5 py-0.5 rounded-full text-[10px] font-bold tracking-wider uppercase border ${getTierBadgeColor()} flex items-center gap-1 backdrop-blur-md`}>
                        {tier === 'DIAMOND' && <Crown className="h-3 w-3 shrink-0" />}
                        {(tier === 'GOLD' || tier === 'PLATINUM') && <Star className="h-3 w-3 shrink-0" />}
                        {tier === 'SILVER' && <Sparkles className="h-3 w-3 shrink-0" />}
                        {tier} VIP
                      </span>
                    </div>

                    {/* Middle Row: Chip & Contactless wave */}
                    <div className="flex items-center gap-3 z-10">
                      {/* NFC Gold Chip */}
                      <div className="w-10 h-7 bg-gradient-to-br from-amber-300 via-amber-400 to-yellow-500 rounded-md border border-amber-600/30 relative overflow-hidden flex flex-col justify-between p-1 shadow-inner">
                        <div className="h-[1px] bg-amber-800/30 w-full mt-1.5" />
                        <div className="h-[1px] bg-amber-800/30 w-full mb-1.5" />
                        <div className="absolute inset-y-0 left-1/2 w-[1px] bg-amber-800/30" />
                        <div className="absolute inset-y-0 left-1/3 w-[1px] bg-amber-800/30" />
                        <div className="absolute inset-y-0 right-1/3 w-[1px] bg-amber-800/30" />
                      </div>
                      
                      {/* Sideways Contactless waves */}
                      <svg className="w-5 h-5 text-white/50 rotate-90" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round">
                        <path d="M5 8c2-2 5-2 7 0" />
                        <path d="M3 5c3.5-3.5 8.5-3.5 12 0" />
                        <path d="M1 2c5-5 12-5 17 0" />
                      </svg>
                    </div>

                    {/* Bottom Row: Card number, Name, and helper label */}
                    <div className="space-y-1.5 z-10">
                      <p className="font-mono text-lg tracking-[0.18em] font-semibold text-center select-all">
                        {cardNumber}
                      </p>
                      <div className="flex justify-between items-end">
                        <div>
                          <p className="text-[9px] uppercase tracking-wider opacity-50">Chủ thẻ</p>
                          <p className="text-sm font-bold tracking-wide truncate max-w-[200px]">
                            {[customerProfile.firstName, customerProfile.lastName].filter(Boolean).join(' ') || 'MEMBER'}
                          </p>
                        </div>
                        <div className="text-right">
                          <p className="text-[9px] uppercase tracking-wider opacity-40">Mã chi nhánh</p>
                          <p className="text-xs font-mono font-medium opacity-80">{customerProfile.tenantId}</p>
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* Card Back Side */}
                  <div className="absolute inset-0 w-full h-full rounded-2xl bg-slate-900 border border-slate-800 text-white flex flex-col justify-between py-4 px-5 backface-hidden rotate-y-180 shadow-2xl">
                    {/* Magnetic Strip */}
                    <div className="absolute top-4 left-0 w-full h-9 bg-slate-950" />
                    
                    {/* QR Code holder and details */}
                    <div className="flex gap-4 items-center mt-9 z-10">
                      <div className="bg-white p-1 rounded-lg shadow-md shrink-0 flex items-center justify-center">
                        {cardQrUrl ? (
                          <img src={cardQrUrl} alt="Card QR" className="w-[100px] h-[100px]" />
                        ) : (
                          <div className="w-[100px] h-[100px] bg-slate-200 animate-pulse rounded" />
                        )}
                      </div>
                      <div className="space-y-1">
                        <p className="text-[11px] font-bold tracking-wide text-indigo-400">TÍCH ĐIỂM THÀNH VIÊN</p>
                        <p className="text-[9px] leading-relaxed text-slate-400">
                          Đưa mã QR này cho nhân viên khi thanh toán tại quầy để tích lũy điểm thưởng và nhận chiết khấu VIP.
                        </p>
                        <p className="text-[10px] font-mono text-slate-300 font-bold select-all bg-slate-950/80 px-2 py-0.5 rounded border border-slate-800 inline-block mt-1">
                          {cardNumber}
                        </p>
                      </div>
                    </div>

                    {/* Back Bottom Text */}
                    <div className="flex justify-between items-center text-[8px] text-slate-500 border-t border-slate-800/60 pt-2 z-10">
                      <span>V-ERP Lite Loyalty System v1.0</span>
                      <span>Chạm thẻ để lật lại</span>
                    </div>
                  </div>
                  
                </div>
              </div>
            </div>

            {/* Right side: Detailed Stats, Progress & Benefits */}
            <div className="space-y-5">
              <div className="flex gap-4">
                <div className="bg-accent/40 rounded-xl p-3 flex-1 border border-border/40 text-center">
                  <p className="text-xs font-semibold text-muted-foreground uppercase">Điểm tích lũy</p>
                  <p className="text-2xl font-black text-primary mt-1">{points.toLocaleString()} pts</p>
                </div>
                <div className="bg-accent/40 rounded-xl p-3 flex-1 border border-border/40 text-center">
                  <p className="text-xs font-semibold text-muted-foreground uppercase">Tổng chi tiêu</p>
                  <p className="text-2xl font-black text-foreground mt-1">
                    {new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(totalSpendMinor)}
                  </p>
                </div>
              </div>

              {/* Wallet Integration Section */}
              <div className="bg-primary/5 rounded-xl p-4 border border-primary/20 flex flex-col sm:flex-row items-center justify-between gap-4">
                <div className="flex items-center gap-3">
                  <div className="w-12 h-12 rounded-full bg-primary/10 flex items-center justify-center shrink-0">
                    <Wallet className="h-6 w-6 text-primary" />
                  </div>
                  <div>
                    <p className="text-xs font-semibold text-primary uppercase">Số dư ví cá nhân</p>
                    <p className="text-2xl font-black text-foreground">
                      {userWallet 
                        ? formatMoneyMinor(userWallet.availableBalanceMinor ?? 0, userWallet.currency || 'VND')
                        : '---'}
                    </p>
                  </div>
                </div>
                <RouterLink to="/user-wallet">
                  <Button variant="default" className="w-full sm:w-auto shadow-md">
                    Quản lý & Nạp tiền
                  </Button>
                </RouterLink>
              </div>

              {/* Tier Progress bar */}
              <div className="space-y-2">
                <div className="flex justify-between text-xs font-bold">
                  <span className="text-muted-foreground">Tiến trình hạng thẻ</span>
                  <span className="text-primary">{tier === 'DIAMOND' ? 'Tối đa' : `${progressPercent.toFixed(0)}%`}</span>
                </div>
                
                {/* Visual Progress bar */}
                <div className="w-full bg-accent h-2.5 rounded-full overflow-hidden border border-border/30">
                  <div 
                    className={`h-full rounded-full transition-all duration-1000 ${
                      tier === 'DIAMOND' ? 'bg-purple-500 shadow-[0_0_8px_rgba(168,85,247,0.5)]' :
                      tier === 'PLATINUM' ? 'bg-cyan-500 shadow-[0_0_8px_rgba(6,182,212,0.5)]' :
                      tier === 'GOLD' ? 'bg-amber-500 shadow-[0_0_8px_rgba(245,158,11,0.5)]' : 'bg-slate-400'
                    }`}
                    style={{ width: `${progressPercent}%` }}
                  />
                </div>
                <p className="text-xs text-muted-foreground italic">{progressText}</p>
              </div>

              {/* VIP Benefits List */}
              <div className="rounded-xl border border-border/60 p-4 bg-card">
                <h4 className="text-xs font-bold uppercase tracking-wider text-muted-foreground mb-3 flex items-center gap-1.5">
                  <Sparkles className="h-3.5 w-3.5 text-primary" /> Quyền lợi hạng thẻ {tier} VIP
                </h4>
                <ul className="space-y-2">
                  {tier === 'SILVER' && (
                    <>
                      <li className="text-xs flex items-center gap-2 text-foreground/95">
                        <Check className="h-4 w-4 text-success shrink-0" />
                        Tích lũy 1% điểm thưởng trên mỗi đơn hàng.
                      </li>
                      <li className="text-xs flex items-center gap-2 text-foreground/95">
                        <Check className="h-4 w-4 text-success shrink-0" />
                        Được quy đổi điểm lấy Voucher quà tặng đặc biệt.
                      </li>
                    </>
                  )}
                  {tier === 'GOLD' && (
                    <>
                      <li className="text-xs flex items-center gap-2 text-foreground/95">
                        <Check className="h-4 w-4 text-success shrink-0" />
                        <strong>Giảm giá trực tiếp 5%</strong> khi mua hàng.
                      </li>
                      <li className="text-xs flex items-center gap-2 text-foreground/95">
                        <Check className="h-4 w-4 text-success shrink-0" />
                        Tích lũy 2% điểm thưởng trên mỗi đơn hàng.
                      </li>
                      <li className="text-xs flex items-center gap-2 text-foreground/95">
                        <Check className="h-4 w-4 text-success shrink-0" />
                        Quà tặng nhân dịp sinh nhật của bạn.
                      </li>
                    </>
                  )}
                  {tier === 'PLATINUM' && (
                    <>
                      <li className="text-xs flex items-center gap-2 text-foreground/95">
                        <Check className="h-4 w-4 text-success shrink-0" />
                        <strong>Giảm giá trực tiếp 10%</strong> trên mọi hóa đơn.
                      </li>
                      <li className="text-xs flex items-center gap-2 text-foreground/95">
                        <Check className="h-4 w-4 text-success shrink-0" />
                        Tích lũy 3% điểm thưởng trên mỗi đơn hàng.
                      </li>
                      <li className="text-xs flex items-center gap-2 text-foreground/95">
                        <Check className="h-4 w-4 text-success shrink-0" />
                        Hỗ trợ chăm sóc khách hàng VIP ưu tiên 24/7.
                      </li>
                      <li className="text-xs flex items-center gap-2 text-foreground/95">
                        <Check className="h-4 w-4 text-success shrink-0" />
                        Quà sinh nhật cao cấp và Voucher sinh nhật đặc biệt.
                      </li>
                    </>
                  )}
                  {tier === 'DIAMOND' && (
                    <>
                      <li className="text-xs flex items-center gap-2 text-foreground/95">
                        <Check className="h-4 w-4 text-success shrink-0" />
                        <strong>Giảm giá trực tiếp 15%</strong> trên mọi hóa đơn.
                      </li>
                      <li className="text-xs flex items-center gap-2 text-foreground/95">
                        <Check className="h-4 w-4 text-success shrink-0" />
                        Tích lũy 5% điểm thưởng trên mỗi đơn hàng.
                      </li>
                      <li className="text-xs flex items-center gap-2 text-foreground/95">
                        <Check className="h-4 w-4 text-success shrink-0" />
                        Miễn phí vận chuyển cho tất cả các đơn hàng.
                      </li>
                      <li className="text-xs flex items-center gap-2 text-foreground/95">
                        <Check className="h-4 w-4 text-success shrink-0" />
                        Quà tặng VIP đặc quyền cuối năm và đặc quyền ưu tiên.
                      </li>
                    </>
                  )}
                </ul>
              </div>
            </div>
          </div>
        </section>
      )}

      {/* Identity */}
      <section className="border-b border-border/50 pb-7 mb-7">
        <div className="flex flex-col items-center gap-4 sm:flex-row sm:items-start">
          <Avatar className="h-20 w-20 ring-2 ring-border">
            <AvatarFallback className="text-xl font-semibold">
              {initials}
            </AvatarFallback>
          </Avatar>
          <div className="flex-1 text-center sm:text-left">
            <h2 className="text-xl font-semibold tracking-tight">
              {displayName}
            </h2>
            {editing ? (
              <div className="mt-2 grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="jobTitle">{t('profile:jobTitle')}</Label>
                  <div className="relative">
                    <Briefcase className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
                    <Input
                      id="jobTitle"
                      value={jobTitle}
                      onChange={(e) => setJobTitle(e.target.value)}
                      placeholder={t('profile:jobTitlePlaceholder')}
                      className="pl-9"
                    />
                  </div>
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="website">{t('profile:website')}</Label>
                  <div className="relative">
                    <Link className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
                    <Input
                      id="website"
                      type="url"
                      value={websiteUrl}
                      onChange={(e) => setWebsiteUrl(e.target.value)}
                      placeholder={t('profile:websitePlaceholder')}
                      className="pl-9"
                    />
                  </div>
                </div>
              </div>
            ) : (
              <div className="mt-1 space-y-0.5">
                <p className="text-sm text-muted-foreground">
                  {profile?.jobTitle || t('profile:noJobTitle')}
                </p>
                <p className="text-sm text-muted-foreground">
                  {oidcUser?.profile?.email}
                </p>
                {profile?.websiteUrl && (
                  <a
                    href={profile.websiteUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-1 text-sm text-primary hover:underline"
                  >
                    <Link className="h-3.5 w-3.5" />
                    {profile.websiteUrl}
                  </a>
                )}
              </div>
            )}
          </div>
        </div>
      </section>

      {/* About */}
      <section className="border-b border-border/50 pb-7 mb-7">
        <div className="flex items-center gap-2 mb-4">
          <UserIcon className="h-5 w-5 text-muted-foreground" />
          <h2 className="text-base font-semibold">{t('profile:about')}</h2>
        </div>
        {editing ? (
          <div className="space-y-1.5">
            <Label htmlFor="bio">{t('profile:bio')}</Label>
            <textarea
              id="bio"
              value={bio}
              onChange={(e) => setBio(e.target.value)}
              placeholder={t('profile:bioPlaceholder')}
              rows={4}
              className="flex w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 resize-y min-h-[80px]"
            />
          </div>
        ) : (
          <p className="text-sm leading-relaxed text-foreground/80">
            {profile?.bio || (
              <span className="italic text-muted-foreground">
                {t('profile:noBio')}
              </span>
            )}
          </p>
        )}
      </section>

      {/* Contact & Work */}
      <section className="border-b border-border/50 pb-7 mb-7">
        <div className="grid grid-cols-1 gap-8 md:grid-cols-2">
          <div>
            <div className="flex items-center gap-2 mb-4">
              <Mail className="h-5 w-5 text-muted-foreground" />
              <h2 className="text-base font-semibold">{t('profile:contact')}</h2>
            </div>
            <div className="space-y-4">
              <div className="space-y-1.5">
                <Label className="text-xs text-muted-foreground">{t('profile:email')}</Label>
                {editing ? (
                  <Input
                    value={profile?.email || oidcUser?.profile?.email || ''}
                    disabled
                    className="bg-muted/50"
                  />
                ) : (
                  <p className="text-sm font-medium">
                    {profile?.email || oidcUser?.profile?.email || '-'}
                  </p>
                )}
              </div>
              <div className="space-y-1.5">
                <Label className="text-xs text-muted-foreground">{t('profile:phone')}</Label>
                {editing ? (
                  <div className="relative">
                    <Phone className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
                    <Input
                      id="phone"
                      type="tel"
                      value={phone}
                      onChange={(e) => setPhone(e.target.value)}
                      placeholder={t('profile:phonePlaceholder')}
                      className="pl-9"
                    />
                  </div>
                ) : (
                  <p className="text-sm font-medium">
                    {profile?.phone || <span className="text-muted-foreground">-</span>}
                  </p>
                )}
              </div>
            </div>
          </div>
          <div>
            <div className="flex items-center gap-2 mb-4">
              <Briefcase className="h-5 w-5 text-muted-foreground" />
              <h2 className="text-base font-semibold">{t('profile:work')}</h2>
            </div>
            <div className="space-y-4">
              <div className="space-y-1.5">
                <Label className="text-xs text-muted-foreground">{t('profile:jobTitle')}</Label>
                {editing ? (
                  <Input
                    value={jobTitle}
                    onChange={(e) => setJobTitle(e.target.value)}
                    placeholder={t('profile:jobTitlePlaceholder')}
                  />
                ) : (
                  <p className="text-sm font-medium">
                    {profile?.jobTitle || <span className="text-muted-foreground">-</span>}
                  </p>
                )}
              </div>
              <div className="space-y-1.5">
                <Label className="text-xs text-muted-foreground">{t('profile:timezone')}</Label>
                {editing ? (
                  <div className="relative">
                    <Clock className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
                    <select
                      id="timezone"
                      value={timezone}
                      onChange={(e) => setTimezone(e.target.value)}
                      className="flex h-9 w-full appearance-none rounded-md border border-input bg-transparent pl-9 pr-8 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      {TIMEZONES.map((tz) => (
                        <option key={tz} value={tz}>{tz}</option>
                      ))}
                    </select>
                  </div>
                ) : (
                  <p className="text-sm font-medium">
                    {profile?.timezone || 'UTC'}
                  </p>
                )}
              </div>
              <div className="space-y-1.5">
                <Label className="text-xs text-muted-foreground">{t('profile:tenant')}</Label>
                <p className="text-sm font-medium">
                  {profile?.tenantId || 'default'}
                </p>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Account */}
      <section className="border-b border-border/50 pb-7 mb-7">
        <div className="flex items-center gap-2 mb-4">
          <Shield className="h-5 w-5 text-muted-foreground" />
          <h2 className="text-base font-semibold">{t('profile:account')}</h2>
        </div>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">{t('profile:role')}</Label>
            <div><Badge variant="secondary">{profile?.role?.name || 'user'}</Badge></div>
          </div>
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">{t('profile:status')}</Label>
            <div>
              <Badge variant={profile?.active !== false ? 'default' : 'secondary'}>
                {profile?.active !== false ? t('profile:active') : t('profile:inactive')}
              </Badge>
            </div>
          </div>
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">{t('profile:created')}</Label>
            <p className="text-sm font-medium">
              {profile?.createdAt ? new Date(profile.createdAt).toLocaleDateString() : '-'}
            </p>
          </div>
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">{t('profile:lastUpdated')}</Label>
            <p className="text-sm font-medium">
              {profile?.updatedAt ? new Date(profile.updatedAt).toLocaleDateString() : '-'}
            </p>
          </div>
        </div>
      </section>

      {/* Keycloak Connection */}
      <section className="border-b border-border/50 pb-7 mb-7">
        <div className="flex items-center gap-2 mb-4">
          <AlertTriangle className="h-5 w-5 text-muted-foreground" />
          <h2 className="text-base font-semibold text-destructive">{t('profile:keycloak')}</h2>
        </div>
        {!editing && (
          <div className="space-y-3">
            <div className="space-y-1">
              <Label className="text-xs text-muted-foreground">{t('profile:keycloakSubject')}</Label>
              <p className="break-all font-mono text-xs text-muted-foreground">
                {oidcUser?.profile?.sub || '-'}
              </p>
            </div>
            <div className="space-y-1">
              <Label className="text-xs text-muted-foreground">{t('profile:keycloakIssuer')}</Label>
              <p className="break-all font-mono text-xs text-muted-foreground">
                {oidcUser?.profile?.iss || '-'}
              </p>
            </div>
            <Separator />
            <p className="text-xs text-muted-foreground">
              {t('profile:keycloakNote')}
            </p>
          </div>
        )}
      </section>

      {/* Edit Actions */}
      {editing && (
        <div className="sticky bottom-4 z-20 rounded-lg border bg-card/95 p-4 shadow-lg backdrop-blur supports-[backdrop-filter]:bg-card/80">
          {message && (
            <div
              className={`mb-3 flex items-center gap-2 text-sm ${
                message.type === 'success'
                  ? 'text-success'
                  : 'text-destructive'
              }`}
            >
              {message.type === 'success' ? (
                <Check className="h-4 w-4" />
              ) : (
                <X className="h-4 w-4" />
              )}
              {message.text}
            </div>
          )}
          <div className="flex items-center justify-between gap-3">
            <p className="text-sm text-muted-foreground">
              {t('profile:unsavedChanges')}
            </p>
            <div className="flex gap-2">
              <Button
                variant="outline"
                onClick={handleCancel}
                disabled={saving}
                className="gap-2"
              >
                <X className="h-4 w-4" />
                {t('common:cancel')}
              </Button>
              <Button
                onClick={handleSave}
                disabled={saving}
                className="gap-2"
              >
                <Check className="h-4 w-4" />
                {saving ? t('common:saving') : t('common:save')}
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Static message when not editing */}
      {!editing && message && (
        <div
          className={`flex items-center gap-2 text-sm mt-4 ${
            message.type === 'success'
              ? 'text-success'
              : 'text-destructive'
          }`}
        >
          {message.type === 'success' ? (
            <Check className="h-4 w-4" />
          ) : (
            <X className="h-4 w-4" />
          )}
          {message.text}
        </div>
      )}
    </div>
  )
}
