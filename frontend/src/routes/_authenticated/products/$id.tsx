import { createFileRoute, useRouter } from '@tanstack/react-router'
import { ProductFormPage } from '@/pages/product-form-page'
import { useMeQuery } from '@/shared/api/gen/@tanstack/react-query.gen'

export const Route = createFileRoute('/_authenticated/products/$id')({
  component: () => {
    const router = useRouter()
    const { data: me } = useMeQuery()
    const role = me?.role?.name
    const { id } = Route.useParams()

    if (!role) return null

    if (role !== 'admin' && role !== 'businessOwner') {
      router.navigate({ to: '/products', replace: true })
      return null
    }

    return <ProductFormPage productId={id} />
  },
})
