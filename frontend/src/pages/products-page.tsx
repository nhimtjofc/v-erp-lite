import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { PackagePlus, Plus, Search, Tags, Edit3 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { catalogApi } from '@/shared/api/catalog'
import type { Category, Product } from '@/shared/api/catalog'
import { useMeQuery, useGetCatalogSyncSummaryQuery } from '@/shared/api/gen/@tanstack/react-query.gen'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/shared/ui/dialog'

function formatMoney(value?: number | null, language?: string) {
  if (value === null || value === undefined) return '-'
  return new Intl.NumberFormat(language, { style: 'currency', currency: 'USD' }).format(value)
}

export function ProductsPage() {
  const { t, i18n } = useTranslation(['catalog', 'common'])
  const { data: me, isLoading: isMeLoading } = useMeQuery()
  const { data: wooSummary } = useGetCatalogSyncSummaryQuery()
  const roleName = me?.role?.name
  const isCustomer = roleName === 'customer'

  const queryClient = useQueryClient()
  const canManageProducts = roleName === 'admin' || roleName === 'businessOwner' || roleName === 'operator'
  const canManageTaxonomy = roleName === 'admin'
  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [tagId, setTagId] = useState('')
  const [selectedProduct, setSelectedProduct] = useState<Product | null>(null)
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

      {wooSummary && (
        <Card className="bg-blue-50/50 dark:bg-blue-950/20 border-blue-200 dark:border-blue-900">
          <CardHeader className="py-4">
            <CardTitle className="text-sm flex justify-between items-center text-blue-900 dark:text-blue-100">
              <span>Ecommerce Sync Status</span>
              <Badge variant="outline" className="bg-white dark:bg-black">
                {wooSummary.syncedProducts} / {wooSummary.totalProducts} Synced
              </Badge>
            </CardTitle>
          </CardHeader>
          <CardContent className="py-0 pb-4 text-xs text-blue-700 dark:text-blue-300">
            Last synced: {wooSummary.lastSyncTime ? new Date(wooSummary.lastSyncTime).toLocaleString(i18n.language) : 'Never'}
          </CardContent>
        </Card>
      )}

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
                    <TableHead>{t('status')}</TableHead>
                    <TableHead>{t('stockQuantity')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {products.map((product) => (
                    <TableRow
                      key={product.id}
                      className="cursor-pointer hover:bg-muted/50 transition-colors"
                      onClick={() => setSelectedProduct(product)}
                    >
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
                            <p className="font-semibold text-sm">{product.name}</p>
                          </div>
                        </div>
                      </TableCell>
                      <TableCell className="font-mono text-xs text-muted-foreground">
                        {product.sku}
                      </TableCell>
                      <TableCell>
                        {product.approvalStatus === 'APPROVED' ? (
                          <Badge className="bg-emerald-500/15 text-emerald-700 hover:bg-emerald-500/15 border-transparent dark:bg-emerald-500/10 dark:text-emerald-400">
                            {t('approved')}
                          </Badge>
                        ) : (
                          <Badge className="bg-amber-500/15 text-amber-700 hover:bg-amber-500/15 border-transparent dark:bg-amber-500/10 dark:text-amber-400 animate-pulse">
                            {t('pending')}
                          </Badge>
                        )}
                      </TableCell>
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
                            <div className="flex items-center gap-2">
                              <span
                                className={
                                  availableStock === 0
                                    ? 'text-destructive font-bold'
                                    : isLow
                                      ? 'font-semibold text-amber-600 dark:text-amber-500'
                                      : 'font-medium'
                                }
                              >
                                {availableStock}
                              </span>
                              {availableStock === 0 ? (
                                <Badge variant="destructive" className="text-[10px] py-0 px-1.5 h-4">{t('outOfStock')}</Badge>
                              ) : isLow ? (
                                <span className="inline-flex items-center rounded-full bg-amber-100 px-1.5 py-0.5 text-[10px] font-semibold text-amber-700 ring-1 ring-inset ring-amber-600/20 dark:bg-amber-900/30 dark:text-amber-400 dark:ring-amber-500/30">
                                  {t('lowStock')}
                                </span>
                              ) : null}
                            </div>
                          )
                        })()}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>

        {/* Detailed Product Dialog */}
        <Dialog open={!!selectedProduct} onOpenChange={(open) => { if (!open) setSelectedProduct(null) }}>
          <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
            {selectedProduct && (
              <>
                <DialogHeader>
                  <div className="flex items-center justify-between gap-4 mr-6">
                    <div>
                      <DialogTitle className="text-xl font-bold text-foreground">
                        {selectedProduct.name}
                      </DialogTitle>
                      <DialogDescription className="font-mono text-xs text-muted-foreground mt-0.5">
                        SKU: {selectedProduct.sku}
                      </DialogDescription>
                    </div>
                    <div className="flex gap-1.5 shrink-0">
                      <Badge variant={selectedProduct.active ? 'default' : 'secondary'}>
                        {selectedProduct.active ? t('active') : t('inactive')}
                      </Badge>
                      <Badge className={selectedProduct.approvalStatus === 'APPROVED' ? 'bg-emerald-500/15 text-emerald-700 hover:bg-emerald-500/15 border-transparent dark:bg-emerald-500/10 dark:text-emerald-400' : 'bg-amber-500/15 text-amber-700 hover:bg-amber-500/15 border-transparent dark:bg-amber-500/10 dark:text-amber-400 animate-pulse'}>
                        {selectedProduct.approvalStatus === 'APPROVED' ? t('approved') : t('pending')}
                      </Badge>
                    </div>
                  </div>
                </DialogHeader>

                <div className="grid gap-6 md:grid-cols-2 mt-4">
                  {/* Left Column: Image and Description */}
                  <div className="space-y-4">
                    <div className="relative aspect-video rounded-lg overflow-hidden border bg-muted flex items-center justify-center">
                      {selectedProduct.imageUrl ? (
                        <img
                          src={selectedProduct.imageUrl}
                          alt={selectedProduct.name}
                          className="w-full h-full object-cover"
                        />
                      ) : (
                        <Tags className="h-12 w-12 text-muted-foreground/30" />
                      )}
                    </div>

                    {selectedProduct.description && (
                      <div className="space-y-1">
                        <h4 className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                          {t('descriptionField')}
                        </h4>
                        <p className="text-sm text-foreground bg-muted/20 p-3 rounded-lg border border-border/50 whitespace-pre-wrap">
                          {selectedProduct.description}
                        </p>
                      </div>
                    )}

                    <div className="flex flex-wrap gap-4">
                      {selectedProduct.category && (
                        <div className="space-y-1">
                          <h4 className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                            {t('category')}
                          </h4>
                          <span className="inline-flex items-center rounded-md bg-primary/5 border border-primary/10 px-2.5 py-1 text-xs font-medium text-primary">
                            {formatCategoryName(selectedProduct.category as Category, categories)}
                          </span>
                        </div>
                      )}

                      {selectedProduct.tags && selectedProduct.tags.length > 0 && (
                        <div className="space-y-1">
                          <h4 className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                            {t('tags')}
                          </h4>
                          <div className="flex flex-wrap gap-1">
                            {selectedProduct.tags.map((tg: any) => (
                              <span
                                key={tg.id}
                                className="inline-flex items-center rounded-full bg-secondary/80 px-2.5 py-0.5 text-xs font-semibold text-muted-foreground border border-border"
                              >
                                #{tg.name}
                              </span>
                            ))}
                          </div>
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Right Column: Inventory & Pricing */}
                  <div className="space-y-5">
                    {/* Inventory Section */}
                    <div className="border rounded-xl p-4 bg-muted/15 space-y-3">
                      <h4 className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                        Stock Status
                      </h4>
                      {(() => {
                        const hasVariants = Boolean(selectedProduct.variants && selectedProduct.variants.length > 0)
                        const totalStock = hasVariants
                          ? selectedProduct.variants!.reduce((sum, v) => sum + (v.stockQuantity || 0), 0)
                          : (selectedProduct.stockQuantity || 0)
                        const reservedStock = hasVariants
                          ? selectedProduct.variants!.reduce((sum, v) => sum + (v.reservedQuantity || 0), 0)
                          : (selectedProduct.reservedQuantity || 0)
                        const availableStock = Math.max(0, totalStock - reservedStock)
                        const isLow = hasVariants
                          ? selectedProduct.variants!.some(
                              (v) => (v.lowStockThreshold ?? 0) > 0
                                && Math.max(0, (v.stockQuantity || 0) - (v.reservedQuantity || 0)) > 0
                                && Math.max(0, (v.stockQuantity || 0) - (v.reservedQuantity || 0)) <= (v.lowStockThreshold ?? 0),
                            )
                          : (selectedProduct.lowStockThreshold ?? 0) > 0
                            && availableStock > 0
                            && availableStock <= (selectedProduct.lowStockThreshold ?? 0)

                        return (
                          <div className="grid grid-cols-3 gap-2 text-center">
                            <div className="bg-background rounded-lg p-2 border">
                              <span className="block text-[9px] text-muted-foreground uppercase font-bold">
                                {t('stockQuantity')}
                              </span>
                              <span className={`block text-lg font-extrabold mt-1 ${
                                availableStock === 0
                                  ? 'text-destructive'
                                  : isLow
                                  ? 'text-amber-500'
                                  : 'text-foreground'
                              }`}>
                                {availableStock}
                              </span>
                              {availableStock === 0 ? (
                                <span className="text-[9px] text-destructive font-bold">{t('outOfStock')}</span>
                              ) : isLow ? (
                                <span className="text-[9px] text-amber-500 font-bold">{t('lowStock')}</span>
                              ) : (
                                <span className="text-[9px] text-emerald-500 font-bold">In Stock</span>
                              )}
                            </div>
                            <div className="bg-background rounded-lg p-2 border">
                              <span className="block text-[9px] text-muted-foreground uppercase font-bold">
                                Total
                              </span>
                              <span className="block text-lg font-extrabold text-foreground mt-1">
                                {totalStock}
                              </span>
                              <span className="text-[9px] text-muted-foreground">physical</span>
                            </div>
                            <div className="bg-background rounded-lg p-2 border">
                              <span className="block text-[9px] text-muted-foreground uppercase font-bold">
                                Reserved
                              </span>
                              <span className="block text-lg font-extrabold text-amber-600 mt-1">
                                {reservedStock}
                              </span>
                              <span className="text-[9px] text-muted-foreground">committed</span>
                            </div>
                          </div>
                        )
                      })()}
                    </div>

                    {/* Pricing Section */}
                    <div className="border rounded-xl p-4 bg-muted/15 space-y-3">
                      <h4 className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                        Pricing
                      </h4>
                      <div className="flex gap-4">
                        <div className="flex-1 bg-background rounded-lg p-3 border">
                          <span className="text-[9px] text-muted-foreground uppercase font-bold block">
                            {t('basePrice')}
                          </span>
                          <span className="text-base font-bold block mt-0.5">
                            {(() => {
                              const prices = selectedProduct.variants && selectedProduct.variants.length > 0
                                ? selectedProduct.variants.map((v) => v.priceMinor).filter((p) => p !== undefined && p !== null)
                                : []
                              const hasMultiplePrices = prices.length > 0 && new Set(prices).size > 1
                              if (hasMultiplePrices) {
                                const minPrice = Math.min(...prices)
                                return t('priceFrom', { priceMinor: formatMoney(minPrice, i18n.language) })
                              }
                              return formatMoney(selectedProduct.basePriceMinor, i18n.language)
                            })()}
                          </span>
                        </div>
                        {selectedProduct.salePriceMinor !== undefined && selectedProduct.salePriceMinor !== null && (
                          <div className="flex-1 bg-background rounded-lg p-3 border border-red-200/60 dark:border-red-900/30">
                            <span className="text-[9px] text-red-500 uppercase font-bold block">
                              {t('salePrice')}
                            </span>
                            <span className="text-base font-bold block text-red-500 mt-0.5">
                              {formatMoney(selectedProduct.salePriceMinor, i18n.language)}
                            </span>
                          </div>
                        )}
                      </div>
                    </div>

                    {/* Variants Section */}
                    {selectedProduct.variants && selectedProduct.variants.length > 0 && (
                      <div className="border rounded-xl p-4 bg-muted/15 space-y-3">
                        <h4 className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                          {t('variantsList')} ({selectedProduct.variants.length})
                        </h4>
                        <div className="space-y-2 max-h-48 overflow-y-auto pr-1">
                          {selectedProduct.variants.map((v: any) => {
                            const optionString = Object.entries(v.optionValues || {})
                              .map(([key, value]) => `${key}: ${value}`)
                              .join(', ')
                            const vAvail = Math.max(0, (v.stockQuantity || 0) - (v.reservedQuantity || 0))
                            return (
                              <div key={v.id} className="flex justify-between items-center text-xs p-2 rounded-lg border bg-background">
                                <div className="min-w-0">
                                  <p className="font-semibold truncate">{optionString || 'Default option'}</p>
                                  <p className="text-[9px] text-muted-foreground font-mono truncate">SKU: {v.sku}</p>
                                </div>
                                <div className="text-right shrink-0 pl-2">
                                  <p className="font-bold text-primary">{formatMoney(v.priceMinor, i18n.language)}</p>
                                  <p className="text-[9px] text-muted-foreground">
                                    {vAvail} left
                                  </p>
                                </div>
                              </div>
                            )
                          })}
                        </div>
                      </div>
                    )}

                    {/* Options Section */}
                    {selectedProduct.options && selectedProduct.options.length > 0 && !selectedProduct.variants?.length && (
                      <div className="border rounded-xl p-4 bg-muted/15 space-y-2">
                        <h4 className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                          {t('productOptions')}
                        </h4>
                        <div className="flex flex-wrap gap-2">
                          {selectedProduct.options.map((o: any) => (
                            <div key={o.name} className="text-xs bg-background border px-2.5 py-1 rounded-md">
                              <span className="font-bold text-muted-foreground">{o.name}: </span>
                              <span className="font-medium text-foreground">{(o.values || []).join(', ')}</span>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                </div>

                <DialogFooter className="mt-6 border-t pt-4 flex items-center justify-between sm:justify-between">
                  <div>
                    {canManageProducts && (
                      <Link to="/products/$id" params={{ id: selectedProduct.id! }}>
                        <Button variant="outline" size="sm" className="gap-1.5">
                          <Edit3 className="h-3.5 w-3.5" />
                          {t('common:edit')}
                        </Button>
                      </Link>
                    )}
                  </div>
                  <Button onClick={() => setSelectedProduct(null)} size="sm">
                    Close
                  </Button>
                </DialogFooter>
              </>
            )}
          </DialogContent>
        </Dialog>

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
