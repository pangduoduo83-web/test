/** 后端时间格式:yyyy-MM-dd HH:mm:ss */
function parseDate(str) {
  if (!str) return null
  if (str instanceof Date) return str
  return new Date(String(str).replace(/-/g, '/'))
}

/** 相对时间:刚刚 / n分钟前 / n小时前 / n天前 / 日期 */
export function relativeTime(str) {
  const d = parseDate(str)
  if (!d) return ''
  const diff = Date.now() - d.getTime()
  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour
  if (diff < minute) return '刚刚'
  if (diff < hour) return `${Math.floor(diff / minute)}分钟前`
  if (diff < day) return `${Math.floor(diff / hour)}小时前`
  if (diff < 7 * day) return `${Math.floor(diff / day)}天前`
  return formatDate(d)
}

/** yyyy-MM-dd */
export function formatDate(str) {
  const d = parseDate(str)
  if (!d) return ''
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

/** 今天的 yyyy-MM-dd */
export function today() {
  return formatDate(new Date())
}

/** 大数字缩写:45100 → 4.5w */
export function shortNum(n) {
  const v = Number(n || 0)
  if (v >= 10000) return `${(v / 10000).toFixed(1)}w`
  if (v >= 1000) return `${(v / 1000).toFixed(1)}k`
  return String(v)
}

/** 解析 @JsonRawValue 字段:后端返回的已是数组,但兜底处理字符串情况 */
export function asList(v) {
  if (Array.isArray(v)) return v
  if (!v) return []
  try {
    const parsed = JSON.parse(v)
    return Array.isArray(parsed) ? parsed : []
  } catch (e) {
    return []
  }
}
