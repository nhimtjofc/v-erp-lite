export interface Category {
  id?: string
  tenantId?: string
  name: string
  description?: string
  parent?: Pick<Category, 'id' | 'name'> | null
  active?: boolean
  createdAt?: string
  updatedAt?: string
  version?: number
}

export interface Tag {
  id?: string
  tenantId?: string
  name: string
  active?: boolean
  createdAt?: string
  updatedAt?: string
  version?: number
}

export interface ProductOption {
  name: string
  values: string[]
}

export interface ProductVariant {
  id?: string
  tenantId?: string
  sku: string
  priceMinor: number
  costPriceMinor?: number | null
  stockQuantity: number
  reservedQuantity?: number
  lowStockThreshold?: number
  optionValues: Record<string, string>
}

export interface Product {
  id?: string
  tenantId?: string
  category?: Pick<Category, 'id' | 'name'> | null
  sku: string
  name: string
  description?: string
  imageUrl?: string
  basePriceMinor: number
  salePriceMinor?: number | null
  costPriceMinor?: number
  saleStartsAt?: string | null
  saleEndsAt?: string | null
  active?: boolean
  createdAt?: string
  updatedAt?: string
  version?: number
  stockQuantity?: number
  reservedQuantity?: number
  lowStockThreshold?: number
  options?: ProductOption[]
  variants?: ProductVariant[]
  tags?: Tag[]
}

interface ProductFilters {
  search?: string
  categoryId?: string
  tagId?: string
}

export const catalogApi = {
  listCategories: async (search?: string) => {
    const response = await listCategoriesSdk({ query: { search }, throwOnError: true })
    return (response.data ?? []) as Category[]
  },
  createCategory: async (category: Partial<Category>) => {
    const response = await createCategorySdk({
      body: category as GeneratedCategory,
      throwOnError: true,
    })
    return response.data as Category
  },
  listProducts: async (filters: ProductFilters = {}) => {
    const response = await listProductsSdk({ query: filters, throwOnError: true })
    return (response.data ?? []) as Product[]
  },
  getProduct: async (id: string) => {
    const response = await getProductSdk({ path: { id }, throwOnError: true })
    return response.data as Product
  },
  createProduct: async (product: Partial<Product>) => {
    const response = await createProductSdk({
      body: product as GeneratedProduct,
      throwOnError: true,
    })
    return response.data as Product
  },
  updateProduct: async (id: string, product: Partial<Product>) => {
    const response = await updateProductSdk({
      path: { id },
      body: product as GeneratedProduct,
      throwOnError: true,
    })
    return response.data as Product
  },
  deleteProduct: async (id: string) => {
    await deleteProductSdk({ path: { id }, throwOnError: true })
  },
  listTags: async (search?: string) => {
    const response = await listTagsSdk({ query: { search }, throwOnError: true })
    return (response.data ?? []) as Tag[]
  },
  createTag: async (tag: Partial<Tag>) => {
    const response = await createTagSdk({ body: tag as GeneratedTag, throwOnError: true })
    return response.data as Tag
  },
  deleteTag: async (id: string) => {
    await deleteTagSdk({ path: { id }, throwOnError: true })
  },
}

import {
  createCategory as createCategorySdk,
  createProduct as createProductSdk,
  createTag as createTagSdk,
  deleteProduct as deleteProductSdk,
  deleteTag as deleteTagSdk,
  getProduct as getProductSdk,
  listCategories as listCategoriesSdk,
  listProducts as listProductsSdk,
  listTags as listTagsSdk,
  updateProduct as updateProductSdk,
} from './gen/sdk.gen'
import type {
  Category as GeneratedCategory,
  Product as GeneratedProduct,
  Tag as GeneratedTag,
} from './gen/types.gen'
