/**
 * 全局配置
 *
 * BASE_URL:后端服务地址(不带 /api 后缀)
 * - 微信开发者工具本机联调:保持 http://localhost:8080,并在
 *   开发者工具「详情 → 本地设置」勾选「不校验合法域名」
 * - 真机预览:改成电脑局域网 IP,如 http://192.168.1.100:8080
 * - 线上发布:改成已备案的 HTTPS 域名,并在微信公众平台配置 request 合法域名
 */
export const BASE_URL = 'http://localhost:8080'

/** 把后端返回的相对路径(/uploads/xxx)拼成完整地址 */
export function fullUrl(url) {
  if (!url) return ''
  if (url.startsWith('http://') || url.startsWith('https://')) return url
  return BASE_URL + url
}
