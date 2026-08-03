import React, { useEffect, useState } from 'react'
import {
  fetchProvinces,
  fetchDistricts,
  fetchWards,
  type Province,
  type District,
  type Ward,
} from '@/shared/lib/vietnam-provinces'
import { Input } from './input'
import { Label } from './label'
import { cn } from '@/shared/lib/cn'
import { MapPin, Loader2 } from 'lucide-react'

export interface CascadingAddressSelectProps {
  provinceCode: string
  districtCode: string
  wardCode: string
  streetAddress: string
  onProvinceChange: (code: string, name: string) => void
  onDistrictChange: (code: string, name: string) => void
  onWardChange: (code: string, name: string) => void
  onStreetAddressChange: (street: string) => void
  disabled?: boolean
  required?: boolean
  compact?: boolean
  className?: string
  labels?: {
    province?: string
    district?: string
    ward?: string
    street?: string
  }
}

export function CascadingAddressSelect({
  provinceCode,
  districtCode,
  wardCode,
  streetAddress,
  onProvinceChange,
  onDistrictChange,
  onWardChange,
  onStreetAddressChange,
  disabled = false,
  required = true,
  compact = false,
  className,
  labels,
}: CascadingAddressSelectProps) {
  const [provinces, setProvinces] = useState<Province[]>([])
  const [districts, setDistricts] = useState<District[]>([])
  const [wards, setWards] = useState<Ward[]>([])

  const [loadingProvinces, setLoadingProvinces] = useState(false)
  const [loadingDistricts, setLoadingDistricts] = useState(false)
  const [loadingWards, setLoadingWards] = useState(false)

  // 1. Fetch Provinces on mount
  useEffect(() => {
    let isMounted = true
    setLoadingProvinces(true)
    fetchProvinces()
      .then((data) => {
        if (isMounted) {
          setProvinces(data)
        }
      })
      .finally(() => {
        if (isMounted) setLoadingProvinces(false)
      })
    return () => {
      isMounted = false
    }
  }, [])

  // 2. Fetch Districts whenever provinceCode changes
  useEffect(() => {
    let isMounted = true
    if (!provinceCode) {
      setDistricts([])
      setWards([])
      return
    }

    setLoadingDistricts(true)
    fetchDistricts(provinceCode)
      .then((data) => {
        if (isMounted) {
          setDistricts(data)
        }
      })
      .finally(() => {
        if (isMounted) setLoadingDistricts(false)
      })

    return () => {
      isMounted = false
    }
  }, [provinceCode])

  // 3. Fetch Wards whenever districtCode changes
  useEffect(() => {
    let isMounted = true
    if (!districtCode) {
      setWards([])
      return
    }

    setLoadingWards(true)
    fetchWards(districtCode)
      .then((data) => {
        if (isMounted) {
          setWards(data)
        }
      })
      .finally(() => {
        if (isMounted) setLoadingWards(false)
      })

    return () => {
      isMounted = false
    }
  }, [districtCode])

  const handleSelectProvince = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const code = e.target.value
    const selected = provinces.find((p) => String(p.code) === code)
    onProvinceChange(code, selected ? selected.name : '')
  }

  const handleSelectDistrict = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const code = e.target.value
    const selected = districts.find((d) => String(d.code) === code)
    onDistrictChange(code, selected ? selected.name : '')
  }

  const handleSelectWard = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const code = e.target.value
    const selected = wards.find((w) => String(w.code) === code)
    onWardChange(code, selected ? selected.name : '')
  }

  return (
    <div className={cn('space-y-3', className)}>
      {/* 3-Level Cascading Dropdowns */}
      <div className={cn('grid gap-3', compact ? 'grid-cols-1 sm:grid-cols-3' : 'grid-cols-1 sm:grid-cols-3')}>
        {/* Province Select */}
        <div className="space-y-1.5">
          <Label className="text-xs font-medium text-foreground flex items-center justify-between">
            <span>{labels?.province ?? 'Tỉnh / Thành phố'} {required && <span className="text-destructive">*</span>}</span>
            {loadingProvinces && <Loader2 className="h-3 w-3 animate-spin text-muted-foreground" />}
          </Label>
          <div className="relative">
            <select
              aria-label="Tỉnh/Thành phố"
              disabled={disabled || loadingProvinces}
              value={provinceCode}
              onChange={handleSelectProvince}
              className={cn(
                'flex h-9 w-full rounded-md border border-input bg-background px-3 py-1.5 text-xs shadow-sm ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 text-foreground cursor-pointer appearance-auto',
                !provinceCode && 'text-muted-foreground'
              )}
            >
              <option value="" disabled>
                -- Chọn Tỉnh / Thành phố --
              </option>
              {provinces.map((p) => (
                <option key={String(p.code)} value={String(p.code)}>
                  {p.name}
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* District Select */}
        <div className="space-y-1.5">
          <Label className="text-xs font-medium text-foreground flex items-center justify-between">
            <span>{labels?.district ?? 'Quận / Huyện'} {required && <span className="text-destructive">*</span>}</span>
            {loadingDistricts && <Loader2 className="h-3 w-3 animate-spin text-muted-foreground" />}
          </Label>
          <div className="relative">
            <select
              aria-label="Quận/Huyện"
              disabled={disabled || !provinceCode || loadingDistricts}
              value={districtCode}
              onChange={handleSelectDistrict}
              className={cn(
                'flex h-9 w-full rounded-md border border-input bg-background px-3 py-1.5 text-xs shadow-sm ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 text-foreground cursor-pointer appearance-auto',
                !districtCode && 'text-muted-foreground'
              )}
            >
              <option value="" disabled>
                {!provinceCode ? '-- Chọn Tỉnh trước --' : '-- Chọn Quận / Huyện --'}
              </option>
              {districts.map((d) => (
                <option key={String(d.code)} value={String(d.code)}>
                  {d.name}
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* Ward Select */}
        <div className="space-y-1.5">
          <Label className="text-xs font-medium text-foreground flex items-center justify-between">
            <span>{labels?.ward ?? 'Phường / Xã'} {required && <span className="text-destructive">*</span>}</span>
            {loadingWards && <Loader2 className="h-3 w-3 animate-spin text-muted-foreground" />}
          </Label>
          <div className="relative">
            <select
              aria-label="Phường/Xã"
              disabled={disabled || !districtCode || loadingWards}
              value={wardCode}
              onChange={handleSelectWard}
              className={cn(
                'flex h-9 w-full rounded-md border border-input bg-background px-3 py-1.5 text-xs shadow-sm ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 text-foreground cursor-pointer appearance-auto',
                !wardCode && 'text-muted-foreground'
              )}
            >
              <option value="" disabled>
                {!districtCode ? '-- Chọn Quận/Huyện trước --' : '-- Chọn Phường / Xã --'}
              </option>
              {wards.map((w) => (
                <option key={String(w.code)} value={String(w.code)}>
                  {w.name}
                </option>
              ))}
            </select>
          </div>
        </div>
      </div>

      {/* Street Address Input */}
      <div className="space-y-1.5">
        <Label className="text-xs font-medium text-foreground flex items-center gap-1.5">
          <MapPin className="h-3.5 w-3.5 text-primary" />
          <span>{labels?.street ?? 'Địa chỉ cụ thể (Số nhà, tên đường, tòa nhà)'} {required && <span className="text-destructive">*</span>}</span>
        </Label>
        <Input
          disabled={disabled}
          value={streetAddress}
          onChange={(e) => onStreetAddressChange(e.target.value)}
          placeholder="Ví dụ: Tầng 4, Tòa nhà Landmark, 720A Điện Biên Phủ"
          className="h-9 text-xs"
        />
      </div>
    </div>
  )
}
