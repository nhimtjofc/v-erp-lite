import { create } from 'zustand'

interface ThemeState {
  isDark: boolean
  setIsDark: (isDark: boolean) => void
}

export const useThemeStore = create<ThemeState>((set) => {
  // Check default theme settings safely for server/client environment
  const getInitialTheme = () => {
    if (typeof window === 'undefined') return false
    const savedTheme = localStorage.getItem('theme')
    if (savedTheme) {
      return savedTheme === 'dark'
    }
    return window.matchMedia('(prefers-color-scheme: dark)').matches
  }

  const initialDark = getInitialTheme()

  // Apply class initial load
  if (typeof window !== 'undefined') {
    if (initialDark) {
      document.documentElement.classList.add('dark')
    } else {
      document.documentElement.classList.remove('dark')
    }
  }

  return {
    isDark: initialDark,
    setIsDark: (isDark: boolean) => {
      set({ isDark })
      if (typeof window !== 'undefined') {
        if (isDark) {
          document.documentElement.classList.add('dark')
          localStorage.setItem('theme', 'dark')
        } else {
          document.documentElement.classList.remove('dark')
          localStorage.setItem('theme', 'light')
        }
      }
    },
  }
})
