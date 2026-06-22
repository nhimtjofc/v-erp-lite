import { create } from 'zustand'
import type { User } from 'oidc-client-ts'

interface AuthState {
  user: User | null
  setUser: (user: User | null) => void
  isAuthenticated: boolean
  isInitialized: boolean
  setInitialized: (initialized: boolean) => void
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  isAuthenticated: false,
  isInitialized: false,
  setUser: (user) => set({ user, isAuthenticated: !!user }),
  setInitialized: (initialized) => set({ isInitialized: initialized }),
}))
