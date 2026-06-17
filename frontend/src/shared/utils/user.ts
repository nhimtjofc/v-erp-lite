import type { User } from 'oidc-client-ts'

export function getInitials(user: User | null | undefined): string {
  if (user?.profile?.name) {
    return user.profile.name
      .split(' ')
      .map((n) => n[0])
      .join('')
      .toUpperCase()
      .slice(0, 2)
  }
  return user?.profile?.email?.[0]?.toUpperCase() ?? '?'
}
