import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { PackagePlus, Plus, Search, Tags } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { catalogApi } from '@/shared/api/catalog'
import type { Category } from '@/shared/api/catalog'
import { useMeQuery } from '@/shared/api/gen/@tanstack/react-query.gen'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'

function formatMoney(value?: number | null, language?: string) {
  if (value === null || value === undefined) return '-'
  return new Intl.NumberFormat(language, { style: 'currency', currency: 'USD' }).format(value)
}

export function ProductsPage() {
  const { t, i18n } = useTranslation(['catalog', 'common'])
  const { data: me, isLoading: isMeLoading } = useMeQuery()
  const roleName = me?.role?.name
  const isCustomer = roleName === 'customer'

  const queryClient = useQueryClient()
  const canManageProducts = roleName === 'admin' || roleName === 'businessOwner'
  const canManageTaxonomy = roleName === 'admin'
  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [tagId, setTagId] = useState('')
  const [newCategory, setNewCategory] = useState('')
  const [newCategoryParentId, setNewCategoryParentId] = useState('')
  const [newTag, setNewTag] = useState('')

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), 300)
    return () => clearTimeout(timer)
  }, [search])

  const categoriesQuery = useQuery({
    queryKey: ['categories'],
    queryFn: () => catalogApi.listCategories(),
  })

  const tagsQuery = useQuery({
    queryKey: ['tags'],
    queryFn: () => catalogApi.listTags(),
  })

  const productsQuery = useQuery({
    queryKey: ['products', debouncedSearch, categoryId, tagId],
    queryFn: () => catalogApi.listProducts({
      search: debouncedSearch || undefined,
      categoryId: categoryId || undefined,
      tagId: tagId || undefined,
    }),
  })

  const createCategory = useMutation({
    mutationFn: (data: { name: string; parentId?: string }) =>
      catalogApi.createCategory({
        name: data.name,
        active: true,
        parent: data.parentId ? { id: data.parentId, name: '' } : null,
      }),
    onSuccess: async () => {
      setNewCategory('')
      setNewCategoryParentId('')
      await queryClient.invalidateQueries({ queryKey: ['categories'], refetchType: 'active' })
      await queryClient.refetchQueries({ queryKey: ['categories'], type: 'active' })
      await queryClient.invalidateQueries({ queryKey: ['products'], refetchType: 'active' })
      await queryClient.refetchQueries({ queryKey: ['products'], type: 'active' })
    },
  })

  const createTag = useMutation({
    mutationFn: (name: string) => catalogApi.createTag({ name, active: true }),
    onSuccess: async () => {
      setNewTag('')
      await queryClient.invalidateQueries({ queryKey: ['tags'], refetchType: 'active' })
      await queryClient.refetchQueries({ queryKey: ['tags'], type: 'active' })
    },
  })

  const deleteTag = useMutation({
    mutationFn: (id: string) => catalogApi.deleteTag(id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['tags'], refetchType: 'active' })
      await queryClient.refetchQueries({ queryKey: ['tags'], type: 'active' })
      await queryClient.invalidateQueries({ queryKey: ['products'], refetchType: 'active' })
      await queryClient.refetchQueries({ queryKey: ['products'], type: 'active' })
    },
  })

  const categories = categoriesQuery.data ?? []
  const tags = tagsQuery.data ?? []
  const products = productsQuery.data ?? []

  if (isMeLoading) {
    return <div className="text-center py-8 text-muted-foreground">Loading access control...</div>
  }

  if (!roleName || isCustomer) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[300px] border border-dashed rounded-lg p-8 text-center space-y-4">
        <h2 className="text-xl font-bold text-destructive">Access Denied</h2>
        <p className="text-muted-foreground max-w-md">
          Only staff members are allowed to view products.
        </p>
      </div>
    )
  }

  const formatCategoryName = (cat: Category, allCats: Category[]): string => {
    if (!cat.parent) return cat.name
    const parentCat = allCats.find(c => c.id === cat.parent?.id)
    if (parentCat) {
      return `${formatCategoryName(parentCat, allCats)} > ${cat.name}`
    }
    return `${cat.parent.name} > ${cat.name}`
  }

  const handleCreateCategory = (event: React.FormEvent) => {
    event.preventDefault()
    const name = newCategory.trim()
    if (!name) return
    createCategory.mutate({ name, parentId: newCategoryParentId || undefined })
  }

  const handleCreateTag = (event: React.FormEvent) => {
    event.preventDefault()
    const name = newTag.trim()
    if (!name) return
    createTag.mutate(name)
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('title')}</h1>
          <p className="mt-1 text-muted-foreground">{t('description')}</p>
        </div>
        {canManageProducts && (
          <Link to="/products/new">
            <Button>
              <PackagePlus className="h-4 w-4" />
              {t('newProduct')}
            </Button>
          </Link>
        )}
      </div>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
        <Card>
          <CardHeader>
            <div className="flex flex-col gap-3 md:flex-row md:items-center">
              <div className="relative flex-1">
                <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
                <Input
                  placeholder={t('searchPlaceholder')}
                  className="pl-8"
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                />
              </div>
              <select
                className="flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm shadow-sm md:w-48"
                value={categoryId}
                onChange={(event) => setCategoryId(event.target.value)}
              >
                <option value="">{t('allCategories')}</option>
                {categories.map((category) => (
                  <option key={category.id} value={category.id}>
                    {formatCategoryName(category, categories)}
                  </option>
                ))}
              </select>
              <select
                className="flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm shadow-sm md:w-40"
                value={tagId}
                onChange={(event) => setTagId(event.target.value)}
              >
                <option value="">{t('allTags')}</option>
                {tags.map((tag) => (
                  <option key={tag.id} value={tag.id}>#{tag.name}</option>
                ))}
              </select>
            </div>
          </CardHeader>
          <CardContent>
            {productsQuery.isLoading ? (
              <p className="py-8 text-center text-sm text-muted-foreground">{t('common:loading')}</p>
            ) : products.length === 0 ? (
              <p className="py-8 text-center text-sm text-muted-foreground">{t('noProducts')}</p>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('product')}</TableHead>
                    <TableHead>{t('sku')}</TableHead>
                    <TableHead>{t('category')}</TableHead>
                    <TableHead>{t('basePriceMinor')}</TableHead>
                    <TableHead>{t('salePriceMinor')}</TableHead>
                    <TableHead>{t('stockQuantity')}</TableHead>
                    <TableHead>{t('status')}</TableHead>
                    {canManageProducts && <TableHead></TableHead>}
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {products.map((product) => (
                    <TableRow key={product.id}>
                      <TableCell>
                        <div className="flex items-center gap-3">
                          {product.imageUrl ? (
                            <img
                              src={product.imageUrl}
                              alt=""
                              className="h-10 w-10 rounded-md border object-cover"
                            />
                          ) : (
                            <div className="flex h-10 w-10 items-center justify-center rounded-md border bg-muted">
                              <Tags className="h-4 w-4 text-muted-foreground" />
                            </div>
                          )}
                          <div>
                            <p className="font-medium">{product.name}</p>
                            {product.description && (
                              <p className="line-clamp-1 max-w-xs text-xs text-muted-foreground">
                                {product.description}
                              </p>
                            )}
                            {product.options && product.options.length > 0 && (
                              <p className="mt-1 text-xs text-muted-foreground">
                                <span className="font-medium">{t('productOptions')}:</span>{' '}
                                {product.options.map((o) => o.name).join(', ')}
                              </p>
                            )}
                            {product.tags && product.tags.length > 0 && (
                              <div className="mt-1 flex flex-wrap gap-1">
                                {product.tags.map((t) => (
                                  <span
                                    key={t.id}
                                    className="inline-flex items-center rounded bg-secondary/70 px-1.5 py-0.5 text-[10px] font-semibold text-muted-foreground"
                                  >
                                    #{t.name}
                                  </span>
                                ))}
                              </div>
                            )}
                          </div>
                        </div>
                      </TableCell>
                      <TableCell>
                        <div>
                          <p>{product.sku}</p>
                          {product.variants && product.variants.length > 0 && (
                            <span className="inline-flex items-center rounded-full bg-blue-50 px-1.5 py-0.5 text-[10px] font-semibold text-blue-700 ring-1 ring-inset ring-blue-700/10 dark:bg-blue-900/30 dark:text-blue-400 dark:ring-blue-500/30">
                              {t('variantCount', { count: product.variants.length })}
                            </span>
                          )}
                        </div>
                      </TableCell>
                      <TableCell>
                        {product.category
                          ? formatCategoryName(product.category as Category, categories)
                          : '-'}
                      </TableCell>
                      <TableCell>
                        {(() => {
                          const prices = product.variants && product.variants.length > 0
                            ? product.variants.map((v) => v.priceMinor).filter((p) => p !== undefined && p !== null)
                            : []
                          const hasMultiplePrices = prices.length > 0 && new Set(prices).size > 1
                          if (hasMultiplePrices) {
                            const minPrice = Math.min(...prices)
                            return t('priceFrom', { priceMinor: formatMoney(minPrice, i18n.language) })
                          }
                          return formatMoney(product.basePriceMinor, i18n.language)
                        })()}
                      </TableCell>
                      <TableCell>{formatMoney(product.salePriceMinor, i18n.language)}</TableCell>
                      <TableCell>
                        {(() => {
                          const hasVariants = Boolean(product.variants && product.variants.length > 0)
                          const totalStock = hasVariants
                            ? product.variants!.reduce((sum, v) => sum + (v.stockQuantity || 0), 0)
                            : (product.stockQuantity || 0)
                          const reservedStock = hasVariants
                            ? product.variants!.reduce((sum, v) => sum + (v.reservedQuantity || 0), 0)
                            : (product.reservedQuantity || 0)
                          const availableStock = Math.max(0, totalStock - reservedStock)
                          const isLow = hasVariants
                            ? product.variants!.some(
                                (v) => (v.lowStockThreshold ?? 0) > 0
                                  && Math.max(0, (v.stockQuantity || 0) - (v.reservedQuantity || 0)) > 0
                                  && Math.max(0, (v.stockQuantity || 0) - (v.reservedQuantity || 0)) <= (v.lowStockThreshold ?? 0),
                              )
                            : (product.lowStockThreshold ?? 0) > 0
                              && availableStock > 0
                              && availableStock <= (product.lowStockThreshold ?? 0)
                          return (
                            <div className="space-y-1">
                              <div className="flex items-center gap-2">
                                <span
                                  className={
                                    availableStock === 0
                                      ? 'text-destructive font-semibold'
                                      : isLow
                                        ? 'font-semibold text-amber-600 dark:text-amber-500'
                                        : 'font-medium'
                                  }
                                >
                                  {availableStock}
                                </span>
                                {availableStock === 0 ? (
                                  <Badge variant="destructive" className="text-[10px]">{t('outOfStock')}</Badge>
                                ) : isLow ? (
                                  <span className="inline-flex items-center rounded-full bg-amber-100 px-1.5 py-0.5 text-[10px] font-semibold text-amber-700 ring-1 ring-inset ring-amber-600/20 dark:bg-amber-900/30 dark:text-amber-400 dark:ring-amber-500/30">
                                    {t('lowStock')}
                                  </span>
                                ) : null}
                              </div>
                              {reservedStock > 0 && (
                                <p className="text-[11px] text-muted-foreground">
                                  {t('totalStock')}: {totalStock} · {t('reservedStock')}: {reservedStock}
                                </p>
                              )}
                            </div>
                          )
                        })()}
                      </TableCell>
                      <TableCell>
                        <Badge variant={product.active ? 'default' : 'secondary'}>
                          {product.active ? t('active') : t('inactive')}
                        </Badge>
                      </TableCell>
                      {canManageProducts && (
                        <TableCell>
                          <Link to="/products/$id" params={{ id: product.id! }}>
                            <Button variant="ghost" size="sm">{t('common:edit')}</Button>
                          </Link>
                        </TableCell>
                      )}
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>

        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">{t('categories')}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              {canManageTaxonomy && (
                <form className="space-y-3" onSubmit={handleCreateCategory}>
                  <div className="space-y-1">
                    <Label htmlFor="new-category-parent" className="text-xs text-muted-foreground">{t('parentCategory')}</Label>
                    <select
                      id="new-category-parent"
                      className="flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm shadow-sm"
                      value={newCategoryParentId}
                      onChange={(event) => setNewCategoryParentId(event.target.value)}
                    >
                      <option value="">{t('none')}</option>
                      {categories.map((cat) => (
                        <option key={cat.id} value={cat.id}>
                          {formatCategoryName(cat, categories)}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div className="flex gap-2">
                    <div className="flex-1 space-y-1">
                      <Label htmlFor="new-category" className="sr-only">{t('categoryName')}</Label>
                      <Input
                        id="new-category"
                        placeholder={t('newCategory')}
                        value={newCategory}
                        onChange={(event) => setNewCategory(event.target.value)}
                      />
                    </div>
                    <Button type="submit" size="icon" disabled={createCategory.isPending}>
                      <Plus className="h-4 w-4" />
                    </Button>
                  </div>
                </form>
              )}

              <div className="space-y-2 max-h-[200px] overflow-y-auto pr-1">
                {categoriesQuery.isLoading ? (
                  <p className="text-sm text-muted-foreground">{t('common:loading')}</p>
                ) : categories.length === 0 ? (
                  <p className="text-sm text-muted-foreground">{t('noCategories')}</p>
                ) : (
                  categories.map((category: Category) => (
                    <div key={category.id} className="flex items-center justify-between rounded-md border px-3 py-2">
                      <span className="text-sm font-medium">{formatCategoryName(category, categories)}</span>
                      <Badge variant={category.active ? 'default' : 'secondary'}>
                        {category.active ? t('active') : t('inactive')}
                      </Badge>
                    </div>
                  ))
                )}
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">{t('tags')}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              {canManageTaxonomy && (
                <form className="flex gap-2" onSubmit={handleCreateTag}>
                  <div className="flex-1 space-y-1">
                    <Label htmlFor="new-tag" className="sr-only">{t('tagName')}</Label>
                    <Input
                      id="new-tag"
                      placeholder={t('newTag')}
                      value={newTag}
                      onChange={(event) => setNewTag(event.target.value)}
                    />
                  </div>
                  <Button type="submit" size="icon" disabled={createTag.isPending}>
                    <Plus className="h-4 w-4" />
                  </Button>
                </form>
              )}

              <div className="space-y-2 max-h-[200px] overflow-y-auto pr-1">
                {tagsQuery.isLoading ? (
                  <p className="text-sm text-muted-foreground">{t('common:loading')}</p>
                ) : tags.length === 0 ? (
                  <p className="text-sm text-muted-foreground">{t('noTags')}</p>
                ) : (
                  tags.map((tag) => (
                    <div key={tag.id} className="flex items-center justify-between rounded-md border px-3 py-2">
                      <span className="text-sm font-medium">#{tag.name}</span>
                      {canManageTaxonomy && (
                        <Button
                          variant="ghost"
                          size="sm"
                          className="h-6 w-6 p-0 text-muted-foreground hover:text-destructive"
                          onClick={() => deleteTag.mutate(tag.id!)}
                          disabled={deleteTag.isPending}
                        >
                          <Plus className="h-4 w-4 rotate-45" />
                        </Button>
                      )}
                    </div>
                  ))
                )}
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  )
}
