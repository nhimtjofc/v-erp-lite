import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRouter } from '@tanstack/react-router'
import { ArrowLeft, Save, Trash2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { catalogApi } from '@/shared/api/catalog'
import type { Product } from '@/shared/api/catalog'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Switch } from '@/shared/ui/switch'

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
  }
}

function buildPayload(form: ProductFormState): Partial<Product> {
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
                  {(categoriesQuery.data ?? []).map((category) => (
                    <option key={category.id} value={category.id}>{category.name}</option>
                  ))}
                </select>
              </div>
            </div>

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
