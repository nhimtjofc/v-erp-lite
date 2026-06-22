import { useEffect } from 'react'
import { useThemeStore } from '../store/theme-store'

export function useTheme() {
  const { isDark, setIsDark } = useThemeStore()

  useEffect(() => {
    const mq = window.matchMedia('(prefers-color-scheme: dark)')
    const handler = (e: MediaQueryListEvent) => {
      if (!localStorage.getItem('theme')) {
        setIsDark(e.matches)
      }
    }
    mq.addEventListener('change', handler)
    return () => mq.removeEventListener('change', handler)
  }, [setIsDark])

  return { isDark, setIsDark }
}
