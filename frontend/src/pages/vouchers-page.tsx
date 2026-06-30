import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import {
  useList9Query,
  useCreate8Mutation,
  useUpdate7Mutation,
  useDelete8Mutation,
  type Voucher,
} from '@/shared/api'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'
import { Badge } from '@/shared/ui/badge'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/shared/ui/dialog'
import { formatUSD } from '@/shared/utils/currency'
import { Plus, Search, Pencil, Trash2 } from 'lucide-react'
import { useQueryClient } from '@tanstack/react-query'

export function VouchersPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  
  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), 300)
    return () => clearTimeout(timer)
  }, [search])

  const { data: vouchers, isLoading } = useList9Query(
    debouncedSearch ? { query: { search: debouncedSearch } } : undefined
  )
  const voucherList = vouchers ?? []

  const createMutation = useCreate8Mutation({
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['list9'], refetchType: 'active' })
      await queryClient.refetchQueries({ queryKey: ['list9'], type: 'active' })
      setIsDialogOpen(false)
      resetForm()
    },
  })

  const updateMutation = useUpdate7Mutation({
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['list9'], refetchType: 'active' })
      await queryClient.refetchQueries({ queryKey: ['list9'], type: 'active' })
      setIsDialogOpen(false)
      resetForm()
    },
  })

  const deleteMutation = useDelete8Mutation({
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['list9'], refetchType: 'active' })
      await queryClient.refetchQueries({ queryKey: ['list9'], type: 'active' })
    },
  })

  // Dialog & Form states
  const [isDialogOpen, setIsDialogOpen] = useState(false)
  const [editingVoucher, setEditingVoucher] = useState<Voucher | null>(null)
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [discountType, setDiscountType] = useState('PERCENTAGE')
  const [discountValue, setDiscountValue] = useState(0)
  const [minOrderValue, setMinOrderValue] = useState(0)
  const [maxDiscountAmount, setMaxDiscountAmount] = useState<number | undefined>(undefined)
  const [startTime, setStartTime] = useState('')
  const [endTime, setEndTime] = useState('')
  const [usageLimit, setUsageLimit] = useState<number | undefined>(undefined)
  const [active, setActive] = useState(true)
  const [formError, setFormError] = useState('')

  const resetForm = () => {
    setEditingVoucher(null)
    setCode('')
    setName('')
    setDiscountType('PERCENTAGE')
    setDiscountValue(0)
    setMinOrderValue(0)
    setMaxDiscountAmount(undefined)
    setStartTime('')
    setEndTime('')
    setUsageLimit(undefined)
    setActive(true)
    setFormError('')
  }

  const handleOpenCreate = () => {
    resetForm()
    setIsDialogOpen(true)
  }

  const handleOpenEdit = (voucher: Voucher) => {
    setEditingVoucher(voucher)
    setCode(voucher.code ?? '')
    setName(voucher.name ?? '')
    setDiscountType(voucher.discountType ?? 'PERCENTAGE')
    setDiscountValue(Number(voucher.discountValue))
    setMinOrderValue(Number(voucher.minOrderValue))
    setMaxDiscountAmount(voucher.maxDiscountAmount ? Number(voucher.maxDiscountAmount) : undefined)
    setStartTime(voucher.startTime ? new Date(voucher.startTime).toISOString().slice(0, 16) : '')
    setEndTime(voucher.endTime ? new Date(voucher.endTime).toISOString().slice(0, 16) : '')
    setUsageLimit(voucher.usageLimit ?? undefined)
    setActive(voucher.active ?? true)
    setFormError('')
    setIsDialogOpen(true)
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!code.trim()) {
      setFormError('Code is required')
      return
    }
    if (!name.trim()) {
      setFormError('Name is required')
      return
    }
    if (discountValue <= 0) {
      setFormError('Discount value must be greater than 0')
      return
    }

    const payload: Omit<Voucher, 'id' | 'createdAt' | 'updatedAt' | 'version' | 'tenantId'> = {
      code: code.trim().toUpperCase(),
      name: name.trim(),
      discountType,
      discountValue: discountValue,
      minOrderValue: minOrderValue,
      maxDiscountAmount: maxDiscountAmount || undefined,
      startTime: startTime ? new Date(startTime).toISOString() : undefined,
      endTime: endTime ? new Date(endTime).toISOString() : undefined,
      usageLimit: usageLimit || undefined,
      usedCount: editingVoucher ? editingVoucher.usedCount : 0,
      active,
    }

    if (editingVoucher) {
      updateMutation.mutate({
        path: { id: editingVoucher.id! },
        body: { ...editingVoucher, ...payload } as any,
      })
    } else {
      createMutation.mutate({
        body: payload as any,
      })
    }
  }

  const handleDelete = (id: string) => {
    if (window.confirm(t('common:confirmDelete', 'Are you sure you want to delete this item?'))) {
      deleteMutation.mutate({ path: { id } })
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Vouchers</h1>
          <p className="text-muted-foreground mt-1">
            Manage your store discount codes and promotion vouchers.
          </p>
        </div>
        <Button onClick={handleOpenCreate}>
          <Plus className="h-4 w-4" />
          Create Voucher
        </Button>
      </div>

      <Card>
        <CardHeader>
          <div className="flex items-center gap-2">
            <div className="relative flex-1 max-w-sm">
              <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
              <Input
                placeholder="Search vouchers by code..."
                className="pl-8"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <p className="text-sm text-muted-foreground py-8 text-center">{t('common:loading', 'Loading...')}</p>
          ) : voucherList.length === 0 ? (
            <p className="text-sm text-muted-foreground py-8 text-center">No vouchers found.</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Code</TableHead>
                  <TableHead>Name</TableHead>
                  <TableHead>Discount Value</TableHead>
                  <TableHead>Min Order Value</TableHead>
                  <TableHead>Usage Limit</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {voucherList.map((voucher) => {
                  const val = Number(voucher.discountValue)
                  const isPercent = voucher.discountType === 'PERCENTAGE'
                  const discountFormatted = isPercent ? `${val}%` : formatUSD(val)
                  
                  return (
                    <TableRow key={voucher.id!}>
                      <TableCell className="font-mono font-bold text-primary">
                        {voucher.code}
                      </TableCell>
                      <TableCell className="font-medium">{voucher.name}</TableCell>
                      <TableCell className="font-medium">{discountFormatted}</TableCell>
                      <TableCell>{formatUSD(Number(voucher.minOrderValue))}</TableCell>
                      <TableCell>
                        {voucher.usedCount} / {voucher.usageLimit ?? '∞'}
                      </TableCell>
                      <TableCell>
                        <Badge variant={voucher.active ? 'default' : 'secondary'}>
                          {voucher.active ? 'Active' : 'Inactive'}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-2">
                          <Button variant="ghost" size="icon" onClick={() => handleOpenEdit(voucher)}>
                            <Pencil className="h-4 w-4" />
                          </Button>
                          <Button variant="ghost" size="icon" className="text-destructive hover:text-destructive hover:bg-destructive/10" onClick={() => handleDelete(voucher.id!)}>
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
        <DialogContent className="sm:max-w-[500px]">
          <form onSubmit={handleSubmit}>
            <DialogHeader>
              <DialogTitle>{editingVoucher ? 'Edit Voucher' : 'Create Voucher'}</DialogTitle>
              <DialogDescription>
                Configure promotion code details. Fields marked with * are required.
              </DialogDescription>
            </DialogHeader>

            <div className="grid gap-4 py-4">
              {formError && (
                <div className="text-sm font-semibold text-destructive bg-destructive/10 p-2.5 rounded border border-destructive/20">
                  {formError}
                </div>
              )}

              <div className="grid grid-cols-3 items-center gap-4">
                <Label htmlFor="code" className="text-right">Code *</Label>
                <Input
                  id="code"
                  className="col-span-2 font-mono uppercase"
                  placeholder="WELCOME10"
                  value={code}
                  onChange={(e) => setCode(e.target.value)}
                  required
                />
              </div>

              <div className="grid grid-cols-3 items-center gap-4">
                <Label htmlFor="name" className="text-right">Name *</Label>
                <Input
                  id="name"
                  className="col-span-2"
                  placeholder="10% off for new customers"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  required
                />
              </div>

              <div className="grid grid-cols-3 items-center gap-4">
                <Label htmlFor="discountType" className="text-right">Discount Type</Label>
                <select
                  id="discountType"
                  className="col-span-2 flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors"
                  value={discountType}
                  onChange={(e) => setDiscountType(e.target.value)}
                >
                  <option value="PERCENTAGE">Percentage (%)</option>
                  <option value="FIXED_AMOUNT">Fixed Amount ($)</option>
                </select>
              </div>

              <div className="grid grid-cols-3 items-center gap-4">
                <Label htmlFor="discountValue" className="text-right">Discount Value *</Label>
                <Input
                  id="discountValue"
                  type="number"
                  step="any"
                  className="col-span-2"
                  value={discountValue}
                  onChange={(e) => setDiscountValue(Number(e.target.value))}
                  min={0.01}
                  required
                />
              </div>

              <div className="grid grid-cols-3 items-center gap-4">
                <Label htmlFor="minOrderValue" className="text-right">Min Order Value</Label>
                <Input
                  id="minOrderValue"
                  type="number"
                  className="col-span-2"
                  value={minOrderValue}
                  onChange={(e) => setMinOrderValue(Number(e.target.value))}
                  min={0}
                />
              </div>

              <div className="grid grid-cols-3 items-center gap-4">
                <Label htmlFor="maxDiscount" className="text-right">Max Discount Amount</Label>
                <Input
                  id="maxDiscount"
                  type="number"
                  className="col-span-2"
                  placeholder="Optional limit for % type"
                  value={maxDiscountAmount ?? ''}
                  onChange={(e) => setMaxDiscountAmount(e.target.value ? Number(e.target.value) : undefined)}
                  min={0}
                />
              </div>

              <div className="grid grid-cols-3 items-center gap-4">
                <Label htmlFor="startTime" className="text-right">Starts At</Label>
                <Input
                  id="startTime"
                  type="datetime-local"
                  className="col-span-2"
                  value={startTime}
                  onChange={(e) => setStartTime(e.target.value)}
                />
              </div>

              <div className="grid grid-cols-3 items-center gap-4">
                <Label htmlFor="endTime" className="text-right">Ends At</Label>
                <Input
                  id="endTime"
                  type="datetime-local"
                  className="col-span-2"
                  value={endTime}
                  onChange={(e) => setEndTime(e.target.value)}
                />
              </div>

              <div className="grid grid-cols-3 items-center gap-4">
                <Label htmlFor="usageLimit" className="text-right">Usage Limit</Label>
                <Input
                  id="usageLimit"
                  type="number"
                  className="col-span-2"
                  placeholder="Infinite if empty"
                  value={usageLimit ?? ''}
                  onChange={(e) => setUsageLimit(e.target.value ? Number(e.target.value) : undefined)}
                  min={1}
                />
              </div>

              <div className="grid grid-cols-3 items-center gap-4">
                <Label htmlFor="active" className="text-right">Active Status</Label>
                <div className="col-span-2 flex items-center">
                  <input
                    type="checkbox"
                    id="active"
                    className="h-4 w-4 rounded border-gray-300 text-primary focus:ring-primary"
                    checked={active}
                    onChange={(e) => setActive(e.target.checked)}
                  />
                  <span className="ml-2 text-sm text-muted-foreground">Enabled and usable</span>
                </div>
              </div>
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setIsDialogOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={createMutation.isPending || updateMutation.isPending}>
                {editingVoucher ? 'Save Changes' : 'Create Voucher'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
