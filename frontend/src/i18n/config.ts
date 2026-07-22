import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import HttpBackend from 'i18next-http-backend'

const API_BASE = import.meta.env.VITE_API_BASE_URL || ''

i18n
  .use(HttpBackend)
  .use(initReactI18next)
  .init({
    lng: (typeof window !== 'undefined' ? localStorage.getItem('language') : null) || 'vi',
    fallbackLng: 'vi',
    ns: ['common', 'nav', 'dashboard', 'profile', 'settings', 'admin', 'catalog', 'notifications', 'revenue'],
    defaultNS: 'common',
    backend: {
      loadPath: `${API_BASE}/api/i18n/{{lng}}/{{ns}}`,
    },
    interpolation: {
      escapeValue: false,
    },
    returnObjects: true,
  })

export default i18n
