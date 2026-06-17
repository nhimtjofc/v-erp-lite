import { defineConfig } from '@hey-api/openapi-ts'

export default defineConfig({
  input: {
    path: 'openapi-spec.yaml',
  },
  output: {
    path: 'src/shared/api/gen',
  },
  plugins: [
    {
      name: '@hey-api/client-fetch',
      runtimeConfigPath: './src/shared/api/client-config.ts',
    },
    {
      name: '@hey-api/sdk',
      auth: true,
    },
    {
      name: '@hey-api/typescript',
      // Emit string-literal union types, not TS `enum`s — the app's tsconfig has
      // `erasableSyntaxOnly` enabled, which disallows `enum` declarations.
      enums: false,
    },
    {
      name: '@tanstack/react-query',
      queryOptions: true,
      queryKeys: { tags: true },
      useMutation: true,
      useQuery: true,
      infiniteQueryOptions: true,
    },
  ],
})
