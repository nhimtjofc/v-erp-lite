export const DEFAULT_CURRENCY = 'VND'

/** Format an amount stored in VND minor units. VND has no fractional minor unit. */
export function formatVND(amountMinor: number): string {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: DEFAULT_CURRENCY,
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(amountMinor)
}

/** V-ERP Lite supports VND only, whose currency exponent is zero. */
export function currencyExponent(currency: string): number {
  if (currency?.toUpperCase() !== DEFAULT_CURRENCY) {
    throw new Error('V-ERP Lite only supports VND')
  }
  return 0
}

/** Format an integer VND amount. The currency argument is validated for API compatibility. */
export function formatMoneyMinor(amountMinor: number, currency: string = DEFAULT_CURRENCY): string {
  currencyExponent(currency)
  return formatVND(amountMinor)
}

/** Parse a positive user-entered VND amount into an integer number of đồng. */
export function parseMoneyToMinor(input: string, currency: string = DEFAULT_CURRENCY): number | null {
  currencyExponent(currency)
  const normalized = input.trim().replace(/[.,\s]/g, '')
  if (normalized === '' || !/^\d+$/.test(normalized)) return null
  const amountMinor = Number(normalized)
  if (!Number.isSafeInteger(amountMinor) || amountMinor <= 0) return null
  return amountMinor
}
