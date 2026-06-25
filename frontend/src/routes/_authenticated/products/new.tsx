import { createFileRoute } from '@tanstack/react-router'
import { ProductFormPage } from '@/pages/product-form-page'

export const Route = createFileRoute('/_authenticated/products/new')({
  component: ProductFormPage,
})
