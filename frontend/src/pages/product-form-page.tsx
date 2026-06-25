import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRouter } from '@tanstack/react-router'
import { ArrowLeft, Save, Trash2, Plus } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { catalogApi } from '@/shared/api/catalog'
import type { Product, Category, ProductOption, ProductVariant } from '@/shared/api/catalog'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Switch } from '@/shared/ui/switch'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'

interface ProductFormPageProps {
  productId?: string
}

interface ProductFormState {
  sku: string
  name: string
  description: string
  imageUrl: string
  categoryId: string
  basePrice: string
  salePrice: string
  saleStartsAt: string
  saleEndsAt: string
  active: boolean
  stockQuantity: string
  hasVariants: boolean
  options: { name: string; values: string }[]
  variants: { sku: string; price: string; stockQuantity: string; optionValues: Record<string, string> }[]
  tagsInput: string
}

const emptyForm: ProductFormState = {
  sku: '',
  name: '',
  description: '',
  imageUrl: '',
  categoryId: '',
  basePrice: '',
  salePrice: '',
  saleStartsAt: '',
  saleEndsAt: '',
  active: true,
  stockQuantity: '0',
  hasVariants: false,
  options: [],
  variants: [],
  tagsInput: '',
}

function toLocalInputValue(value?: string | null) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toISOString().slice(0, 16)
}

function fromLocalInputValue(value: string) {
  return value ? new Date(value).toISOString() : null
}

function productToForm(product: Product): ProductFormState {
  const hasVariants = Boolean(product.variants && product.variants.length > 0)
  
  const options = (product.options || []).map(opt => ({
    name: opt.name,
    values: opt.values.join(', '),
  }))

  const variants = (product.variants || []).map(v => ({
    sku: v.sku,
    price: v.price.toString(),
    stockQuantity: v.stockQuantity.toString(),
    optionValues: v.optionValues,
  }))

  const tagsInput = (product.tags || []).map(t => t.name).join(', ')

  return {
    sku: product.sku || '',
    name: product.name || '',
    description: product.description || '',
    imageUrl: product.imageUrl || '',
    categoryId: product.category?.id || '',
    basePrice: product.basePrice?.toString() || '',
    salePrice: product.salePrice?.toString() || '',
    saleStartsAt: toLocalInputValue(product.saleStartsAt),
    saleEndsAt: toLocalInputValue(product.saleEndsAt),
    active: product.active ?? true,
    stockQuantity: product.stockQuantity?.toString() || '0',
    hasVariants,
    options,
    variants,
    tagsInput,
  }
}

function buildPayload(form: ProductFormState): Partial<Product> {
  const tags = form.tagsInput
    .split(',')
    .map(t => t.trim())
    .filter(Boolean)
    .map(name => ({ name }))

  const options = form.hasVariants
    ? form.options
        .filter(opt => opt.name.trim() && opt.values.trim())
        .map(opt => ({
          name: opt.name.trim(),
          values: opt.values.split(',').map(v => v.trim()).filter(Boolean),
        }))
    : []

  const variants = form.hasVariants
    ? form.variants.map(v => ({
        sku: v.sku.trim(),
        price: Number(v.price || 0),
        stockQuantity: Number(v.stockQuantity || 0),
        optionValues: v.optionValues,
      }))
    : []

  return {
    sku: form.sku.trim(),
    name: form.name.trim(),
    description: form.description.trim() || undefined,
    imageUrl: form.imageUrl.trim() || undefined,
    category: form.categoryId ? { id: form.categoryId, name: '' } : null,
    basePrice: Number(form.basePrice || 0),
    salePrice: form.salePrice ? Number(form.salePrice) : null,
    saleStartsAt: fromLocalInputValue(form.saleStartsAt),
    saleEndsAt: fromLocalInputValue(form.saleEndsAt),
    active: form.active,
    stockQuantity: form.hasVariants ? 0 : Number(form.stockQuantity || 0),
    options,
    variants,
    tags,
  }
}

export function ProductFormPage({ productId }: ProductFormPageProps) {
  const { t } = useTranslation(['catalog', 'common'])
  const router = useRouter()
  const queryClient = useQueryClient()
  const isEdit = Boolean(productId)
  const [form, setForm] = useState<ProductFormState>(emptyForm)
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<keyof ProductFormState, string>>>({})
  const [error, setError] = useState<string | null>(null)

  const categoriesQuery = useQuery({
    queryKey: ['categories'],
    queryFn: () => catalogApi.listCategories(),
  })

  const productQuery = useQuery({
    queryKey: ['product', productId],
    queryFn: () => catalogApi.getProduct(productId!),
    enabled: isEdit,
  })

  useEffect(() => {
    if (productQuery.data) {
      setForm(productToForm(productQuery.data))
    }
  }, [productQuery.data])

  // Cartesian variants generation
  const generateVariantsFromOptions = (opts: typeof form.options) => {
    const activeOpts = opts
      .filter(o => o.name.trim() && o.values.trim())
      .map(o => ({
        name: o.name.trim(),
        values: o.values.split(',').map(v => v.trim()).filter(Boolean),
      }))

    if (activeOpts.length === 0) return []

    const combine = (index: number, current: Record<string, string>): Record<string, string>[] => {
      if (index === activeOpts.length) {
        return [current]
      }
      const opt = activeOpts[index]
      const results: Record<string, string>[] = []
      for (const val of opt.values) {
        results.push(...combine(index + 1, { ...current, [opt.name]: val }))
      }
      return results
    }

    const combinations = combine(0, {})

    return combinations.map(comb => {
      const matchKey = (vOpt: Record<string, string>) => 
        Object.keys(comb).every(k => comb[k] === vOpt[k]) && Object.keys(vOpt).every(k => comb[k] === vOpt[k])
      
      const existing = form.variants.find(v => matchKey(v.optionValues))
      if (existing) return existing

      const optionSuffix = Object.values(comb)
        .map(v => v.toLowerCase().replace(/[^a-z0-9]/g, ''))
        .join('-')
      const defaultSku = `${form.sku ? form.sku.trim() : 'VAR'}-${optionSuffix}`

      return {
        sku: defaultSku,
        price: form.basePrice || '0',
        stockQuantity: '0',
        optionValues: comb,
      }
    })
  }

  useEffect(() => {
    if (form.hasVariants && form.options.length > 0) {
      const newVarList = generateVariantsFromOptions(form.options)
      const listKey = (lst: any[]) => lst.map(v => Object.entries(v.optionValues).map(e => `${e[0]}:${e[1]}`).join(',')).join(';')
      if (listKey(newVarList) !== listKey(form.variants)) {
        setForm(prev => ({ ...prev, variants: newVarList }))
      }
    }
  }, [form.options, form.hasVariants])

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload = buildPayload(form)
      if (isEdit) {
        return catalogApi.updateProduct(productId!, payload)
      }
      return catalogApi.createProduct(payload)
    },
    onSuccess: (product) => {
      queryClient.invalidateQueries({ queryKey: ['products'] })
      queryClient.invalidateQueries({ queryKey: ['product', product.id] })
      queryClient.invalidateQueries({ queryKey: ['tags'] })
      router.navigate({ to: '/products/$id', params: { id: product.id! } })
    },
    onError: (err) => setError(err instanceof Error ? err.message : t('saveFailed')),
  })

  const deleteMutation = useMutation({
    mutationFn: () => catalogApi.deleteProduct(productId!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['products'] })
      router.navigate({ to: '/products' })
    },
  })

  const updateField = <K extends keyof ProductFormState>(field: K, value: ProductFormState[K]) => {
    setForm((current) => ({ ...current, [field]: value }))
    setFieldErrors((current) => ({ ...current, [field]: undefined }))
  }

  const validateForm = () => {
    const errors: Partial<Record<keyof ProductFormState, string>> = {}
    if (!form.sku.trim()) errors.sku = t('requiredField')
    if (!form.name.trim()) errors.name = t('requiredField')
    if (!form.basePrice.trim()) {
      errors.basePrice = t('requiredField')
    } else if (Number(form.basePrice) < 0) {
      errors.basePrice = t('nonNegativeField')
    }
    if (form.salePrice && Number(form.salePrice) < 0) {
      errors.salePrice = t('nonNegativeField')
    }
    if (form.saleStartsAt && form.saleEndsAt && new Date(form.saleEndsAt) < new Date(form.saleStartsAt)) {
      errors.saleEndsAt = t('saleWindowInvalid')
    }
    
    if (!form.hasVariants) {
      if (!form.stockQuantity.trim()) {
        errors.stockQuantity = t('requiredField')
      } else if (Number(form.stockQuantity) < 0) {
        errors.stockQuantity = t('nonNegativeField')
      }
    } else {
      form.variants.forEach((v, idx) => {
        if (!v.sku.trim()) {
          setError(`Biến thể hàng thứ ${idx + 1} thiếu SKU.`)
        }
        if (Number(v.price) < 0 || Number(v.stockQuantity) < 0) {
          setError(`Giá hoặc Tồn kho của biến thể hàng thứ ${idx + 1} không hợp lệ.`)
        }
      })
    }

    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault()
    setError(null)
    if (!validateForm()) return
    saveMutation.mutate()
  }

  const handleDelete = () => {
    if (!productId || !confirm(t('deleteConfirm'))) return
    deleteMutation.mutate()
  }

  if (productQuery.isLoading) {
    return <p className="py-8 text-center text-sm text-muted-foreground">{t('common:loading')}</p>
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <Button variant="ghost" className="-ml-2 mb-2" onClick={() => router.navigate({ to: '/products' })}>
            <ArrowLeft className="h-4 w-4" />
            {t('productsBack')}
          </Button>
          <h1 className="text-3xl font-bold tracking-tight">{isEdit ? t('editProduct') : t('newProduct')}</h1>
          <p className="mt-1 text-muted-foreground">{t('configureProduct')}</p>
        </div>
        {isEdit && (
          <Button variant="destructive" onClick={handleDelete} disabled={deleteMutation.isPending}>
            <Trash2 className="h-4 w-4" />
            {t('deleteProduct')}
          </Button>
        )}
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t('productDetails')}</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-6" noValidate>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="sku">{t('sku')}</Label>
                <Input
                  id="sku"
                  value={form.sku}
                  onChange={(event) => updateField('sku', event.target.value)}
                  aria-invalid={Boolean(fieldErrors.sku)}
                />
                {fieldErrors.sku && <p className="text-sm text-destructive">{fieldErrors.sku}</p>}
              </div>
              <div className="space-y-2">
                <Label htmlFor="category">{t('category')}</Label>
                <select
                  id="category"
                  className="flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm shadow-sm"
                  value={form.categoryId}
                  onChange={(event) => updateField('categoryId', event.target.value)}
                >
                  <option value="">{t('uncategorized')}</option>
                  {(categoriesQuery.data ?? []).map((category) => {
                    const formatCategoryName = (cat: Category, allCats: Category[]): string => {
                      if (!cat.parent) return cat.name
                      const parentCat = allCats.find(c => c.id === cat.parent?.id)
                      if (parentCat) {
                        return `${formatCategoryName(parentCat, allCats)} > ${cat.name}`
                      }
                      return `${cat.parent.name} > ${cat.name}`
                    }
                    return (
                      <option key={category.id} value={category.id}>
                        {formatCategoryName(category, categoriesQuery.data ?? [])}
                      </option>
                    )
                  })}
                </select>
              </div>
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="name">{t('common:name')}</Label>
                <Input
                  id="name"
                  value={form.name}
                  onChange={(event) => updateField('name', event.target.value)}
                  aria-invalid={Boolean(fieldErrors.name)}
                />
                {fieldErrors.name && <p className="text-sm text-destructive">{fieldErrors.name}</p>}
              </div>
              <div className="space-y-2">
                <Label htmlFor="tagsInput">{t('productTags')}</Label>
                <Input
                  id="tagsInput"
                  value={form.tagsInput}
                  placeholder="e.g. hot, sale, new"
                  onChange={(event) => updateField('tagsInput', event.target.value)}
                />
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="description">{t('descriptionField')}</Label>
              <textarea
                id="description"
                className="flex min-h-[110px] w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                value={form.description}
                onChange={(event) => updateField('description', event.target.value)}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="imageUrl">{t('imageUrl')}</Label>
              <Input id="imageUrl" value={form.imageUrl} onChange={(event) => updateField('imageUrl', event.target.value)} />
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="basePrice">{t('basePrice')}</Label>
                <Input
                  id="basePrice"
                  type="number"
                  min="0"
                  step="0.01"
                  value={form.basePrice}
                  onChange={(event) => updateField('basePrice', event.target.value)}
                  aria-invalid={Boolean(fieldErrors.basePrice)}
                />
                {fieldErrors.basePrice && <p className="text-sm text-destructive">{fieldErrors.basePrice}</p>}
              </div>
              <div className="space-y-2">
                <Label htmlFor="salePrice">{t('salePrice')}</Label>
                <Input
                  id="salePrice"
                  type="number"
                  min="0"
                  step="0.01"
                  value={form.salePrice}
                  onChange={(event) => updateField('salePrice', event.target.value)}
                  aria-invalid={Boolean(fieldErrors.salePrice)}
                />
                {fieldErrors.salePrice && <p className="text-sm text-destructive">{fieldErrors.salePrice}</p>}
              </div>
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="saleStartsAt">{t('saleStarts')}</Label>
                <Input
                  id="saleStartsAt"
                  type="datetime-local"
                  value={form.saleStartsAt}
                  onChange={(event) => updateField('saleStartsAt', event.target.value)}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="saleEndsAt">{t('saleEnds')}</Label>
                <Input
                  id="saleEndsAt"
                  type="datetime-local"
                  value={form.saleEndsAt}
                  onChange={(event) => updateField('saleEndsAt', event.target.value)}
                  aria-invalid={Boolean(fieldErrors.saleEndsAt)}
                />
                {fieldErrors.saleEndsAt && <p className="text-sm text-destructive">{fieldErrors.saleEndsAt}</p>}
              </div>
            </div>

            <div className="flex items-center justify-between rounded-md border px-4 py-3">
              <div>
                <Label htmlFor="active">{t('active')}</Label>
                <p className="text-sm text-muted-foreground">{t('inactiveHint')}</p>
              </div>
              <Switch id="active" checked={form.active} onCheckedChange={(checked) => updateField('active', checked)} />
            </div>

            {!form.hasVariants && (
              <div className="space-y-2">
                <Label htmlFor="stockQuantity">{t('stockQuantity')}</Label>
                <Input
                  id="stockQuantity"
                  type="number"
                  min="0"
                  value={form.stockQuantity}
                  onChange={(event) => updateField('stockQuantity', event.target.value)}
                  aria-invalid={Boolean(fieldErrors.stockQuantity)}
                />
                {fieldErrors.stockQuantity && <p className="text-sm text-destructive">{fieldErrors.stockQuantity}</p>}
              </div>
            )}

            <div className="flex items-center justify-between rounded-md border px-4 py-3">
              <div>
                <Label htmlFor="hasVariants" className="font-semibold">{t('hasVariants')}</Label>
              </div>
              <Switch
                id="hasVariants"
                checked={form.hasVariants}
                onCheckedChange={(checked) => updateField('hasVariants', checked)}
              />
            </div>

            {form.hasVariants && (
              <div className="space-y-6 rounded-md border p-4 bg-muted/20">
                <div className="flex items-center justify-between border-b pb-2">
                  <h3 className="font-semibold text-base">{t('productOptions')}</h3>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => {
                      updateField('options', [...form.options, { name: '', values: '' }])
                    }}
                  >
                    <Plus className="h-4 w-4 mr-1" />
                    {t('addOption')}
                  </Button>
                </div>

                {form.options.length === 0 ? (
                  <p className="text-sm text-muted-foreground text-center py-4">Chưa cấu hình thuộc tính nào. Bấm nút phía trên để thêm.</p>
                ) : (
                  <div className="space-y-4">
                    {form.options.map((opt, i) => (
                      <div key={i} className="flex gap-4 items-end border p-3 rounded-md bg-background">
                        <div className="flex-1 space-y-1">
                          <Label htmlFor={`opt-name-${i}`} className="text-xs">{t('optionName')}</Label>
                          <Input
                            id={`opt-name-${i}`}
                            value={opt.name}
                            onChange={(e) => {
                              const next = [...form.options]
                              next[i].name = e.target.value
                              updateField('options', next)
                            }}
                            placeholder="e.g. Màu sắc"
                          />
                        </div>
                        <div className="flex-[2] space-y-1">
                          <Label htmlFor={`opt-vals-${i}`} className="text-xs">{t('optionValues')}</Label>
                          <Input
                            id={`opt-vals-${i}`}
                            value={opt.values}
                            onChange={(e) => {
                              const next = [...form.options]
                              next[i].values = e.target.value
                              updateField('options', next)
                            }}
                            placeholder="e.g. Đỏ, Xanh, Đen"
                          />
                        </div>
                        <Button
                          type="button"
                          variant="destructive"
                          size="icon"
                          onClick={() => {
                            const next = form.options.filter((_, idx) => idx !== i)
                            updateField('options', next)
                          }}
                        >
                          <Plus className="h-4 w-4 rotate-45" />
                        </Button>
                      </div>
                    ))}
                  </div>
                )}

                {form.variants.length > 0 && (
                  <div className="space-y-3 pt-2">
                    <h3 className="font-semibold text-base border-b pb-2">{t('variantsList')}</h3>
                    <div className="overflow-x-auto">
                      <Table>
                        <TableHeader>
                          <TableRow>
                            <TableHead className="min-w-[120px]">Biến thể</TableHead>
                            <TableHead className="min-w-[150px]">{t('sku')}</TableHead>
                            <TableHead className="min-w-[100px]">{t('variantPrice')}</TableHead>
                            <TableHead className="min-w-[100px]">{t('variantStock')}</TableHead>
                          </TableRow>
                        </TableHeader>
                        <TableBody>
                          {form.variants.map((v, i) => {
                            const label = Object.entries(v.optionValues)
                              .map(([k, val]) => `${k}: ${val}`)
                              .join(' / ')
                            return (
                              <TableRow key={i}>
                                <TableCell className="font-medium text-sm">{label}</TableCell>
                                <TableCell>
                                  <Input
                                    value={v.sku}
                                    onChange={(e) => {
                                      const next = [...form.variants]
                                      next[i].sku = e.target.value
                                      updateField('variants', next)
                                    }}
                                  />
                                </TableCell>
                                <TableCell>
                                  <Input
                                    type="number"
                                    min="0"
                                    step="0.01"
                                    value={v.price}
                                    onChange={(e) => {
                                      const next = [...form.variants]
                                      next[i].price = e.target.value
                                      updateField('variants', next)
                                    }}
                                  />
                                </TableCell>
                                <TableCell>
                                  <Input
                                    type="number"
                                    min="0"
                                    value={v.stockQuantity}
                                    onChange={(e) => {
                                      const next = [...form.variants]
                                      next[i].stockQuantity = e.target.value
                                      updateField('variants', next)
                                    }}
                                  />
                                </TableCell>
                              </TableRow>
                            )
                          })}
                        </TableBody>
                      </Table>
                    </div>
                  </div>
                )}
              </div>
            )}

            {error && <p className="text-sm text-destructive">{error}</p>}

            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => router.navigate({ to: '/products' })}>
                {t('common:cancel')}
              </Button>
              <Button type="submit" disabled={saveMutation.isPending}>
                <Save className="h-4 w-4" />
                {saveMutation.isPending ? t('common:saving') : t('saveProduct')}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}

