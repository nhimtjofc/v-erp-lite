export interface Province {
  code: string | number
  name: string
  division_type?: string
  codename?: string
  phone_code?: number
}

export interface District {
  code: string | number
  name: string
  division_type?: string
  codename?: string
  province_code?: string | number
}

export interface Ward {
  code: string | number
  name: string
  division_type?: string
  codename?: string
  district_code?: string | number
}

// In-memory / sessionStorage cache
const provinceCache: { data: Province[] | null } = { data: null }
const districtCache: Record<string, District[]> = {}
const wardCache: Record<string, Ward[]> = {}

// Fallback top provinces in Vietnam
const FALLBACK_PROVINCES: Province[] = [
  { code: '01', name: 'Thành phố Hà Nội' },
  { code: '79', name: 'Thành phố Hồ Chí Minh' },
  { code: '48', name: 'Thành phố Đà Nẵng' },
  { code: '31', name: 'Thành phố Hải Phòng' },
  { code: '92', name: 'Thành phố Cần Thơ' },
  { code: '46', name: 'Tỉnh Thừa Thiên Huế' },
  { code: '74', name: 'Tỉnh Bình Dương' },
  { code: '75', name: 'Tỉnh Đồng Nai' },
  { code: '77', name: 'Tỉnh Bà Rịa - Vũng Tàu' },
  { code: '56', name: 'Tỉnh Khánh Hòa' },
  { code: '49', name: 'Tỉnh Quảng Nam' },
  { code: '68', name: 'Tỉnh Lâm Đồng' },
  { code: '60', name: 'Tỉnh Đắk Lắk' },
  { code: '80', name: 'Tỉnh Long An' },
  { code: '82', name: 'Tỉnh Tiền Giang' },
  { code: '89', name: 'Tỉnh An Giang' },
  { code: '91', name: 'Tỉnh Kiên Giang' },
  { code: '26', name: 'Tỉnh Vĩnh Phúc' },
  { code: '27', name: 'Tỉnh Bắc Ninh' },
  { code: '30', name: 'Tỉnh Hải Dương' },
  { code: '33', name: 'Tỉnh Hưng Yên' },
  { code: '34', name: 'Tỉnh Thái Bình' },
  { code: '35', name: 'Tỉnh Hà Nam' },
  { code: '36', name: 'Tỉnh Nam Định' },
  { code: '37', name: 'Tỉnh Ninh Bình' },
  { code: '38', name: 'Tỉnh Thanh Hóa' },
  { code: '40', name: 'Tỉnh Nghệ An' },
  { code: '42', name: 'Tỉnh Hà Tĩnh' },
  { code: '44', name: 'Tỉnh Quảng Bình' },
  { code: '45', name: 'Tỉnh Quảng Trị' },
  { code: '51', name: 'Tỉnh Quảng Ngãi' },
  { code: '52', name: 'Tỉnh Bình Định' },
  { code: '54', name: 'Tỉnh Phú Yên' },
  { code: '58', name: 'Tỉnh Ninh Thuận' },
  { code: '62', name: 'Tỉnh Kon Tum' },
  { code: '64', name: 'Tỉnh Gia Lai' },
  { code: '66', name: 'Tỉnh Đắk Nông' },
  { code: '70', name: 'Tỉnh Bình Phước' },
  { code: '72', name: 'Tỉnh Tây Ninh' },
  { code: '83', name: 'Tỉnh Bến Tre' },
  { code: '84', name: 'Tỉnh Trà Vinh' },
  { code: '86', name: 'Tỉnh Vĩnh Long' },
  { code: '87', name: 'Tỉnh Đồng Tháp' },
  { code: '93', name: 'Tỉnh Hậu Giang' },
  { code: '94', name: 'Tỉnh Sóc Trăng' },
  { code: '95', name: 'Tỉnh Bạc Liêu' },
  { code: '96', name: 'Tỉnh Cà Mau' },
  { code: '02', name: 'Tỉnh Hà Giang' },
  { code: '04', name: 'Tỉnh Cao Bằng' },
  { code: '06', name: 'Tỉnh Bắc Kạn' },
  { code: '08', name: 'Tỉnh Tuyên Quang' },
  { code: '10', name: 'Tỉnh Lào Cai' },
  { code: '11', name: 'Tỉnh Điện Biên' },
  { code: '12', name: 'Tỉnh Lai Châu' },
  { code: '14', name: 'Tỉnh Sơn La' },
  { code: '15', name: 'Tỉnh Yên Bái' },
  { code: '17', name: 'Tỉnh Hòa Bình' },
  { code: '19', name: 'Tỉnh Thái Nguyên' },
  { code: '20', name: 'Tỉnh Lạng Sơn' },
  { code: '22', name: 'Tỉnh Quảng Ninh' },
  { code: '24', name: 'Tỉnh Bắc Giang' },
  { code: '25', name: 'Tỉnh Phú Thọ' },
]

export async function fetchProvinces(): Promise<Province[]> {
  if (provinceCache.data && provinceCache.data.length > 0) {
    return provinceCache.data
  }

  try {
    const res = await fetch('https://provinces.open-api.vn/api/p/')
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    const data = await res.json()
    if (Array.isArray(data) && data.length > 0) {
      const normalized: Province[] = data.map((p: any) => ({
        code: String(p.code),
        name: p.name,
        division_type: p.division_type,
        codename: p.codename,
        phone_code: p.phone_code,
      }))
      provinceCache.data = normalized
      return normalized
    }
  } catch (err) {
    console.warn('Could not fetch provinces from Open API, using fallback list', err)
  }

  provinceCache.data = FALLBACK_PROVINCES
  return FALLBACK_PROVINCES
}

export async function fetchDistricts(provinceCode: string | number): Promise<District[]> {
  if (!provinceCode) return []
  const codeStr = String(provinceCode)

  if (districtCache[codeStr]) {
    return districtCache[codeStr]
  }

  try {
    const res = await fetch(`https://provinces.open-api.vn/api/p/${codeStr}?depth=2`)
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    const data = await res.json()
    if (data && Array.isArray(data.districts)) {
      const normalized: District[] = data.districts.map((d: any) => ({
        code: String(d.code),
        name: d.name,
        division_type: d.division_type,
        codename: d.codename,
        province_code: codeStr,
      }))
      districtCache[codeStr] = normalized
      return normalized
    }
  } catch (err) {
    console.warn(`Could not fetch districts for province ${codeStr}`, err)
  }

  return []
}

export async function fetchWards(districtCode: string | number): Promise<Ward[]> {
  if (!districtCode) return []
  const codeStr = String(districtCode)

  if (wardCache[codeStr]) {
    return wardCache[codeStr]
  }

  try {
    const res = await fetch(`https://provinces.open-api.vn/api/d/${codeStr}?depth=2`)
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    const data = await res.json()
    if (data && Array.isArray(data.wards)) {
      const normalized: Ward[] = data.wards.map((w: any) => ({
        code: String(w.code),
        name: w.name,
        division_type: w.division_type,
        codename: w.codename,
        district_code: codeStr,
      }))
      wardCache[codeStr] = normalized
      return normalized
    }
  } catch (err) {
    console.warn(`Could not fetch wards for district ${codeStr}`, err)
  }

  return []
}
