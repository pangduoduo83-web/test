/**
 * 全局配置
 *
 * BASE_URL:后端服务地址(不带 /api 后缀)
 * - 演示环境(默认):连云服务器,nginx 已代理 /api 与 /uploads;
 *   开发者工具「详情 → 本地设置」勾选「不校验合法域名」即可,
 *   手机预览需在小程序右上角胶囊菜单打开「开发调试」
 * - 本机联调:改回 http://localhost:8080(后端本地启动时)
 * - 线上发布:改成已备案的 HTTPS 域名,并在微信公众平台配置 request 合法域名
 */
export const BASE_URL = 'http://139.224.189.163:8093'
// export const BASE_URL = 'http://localhost:8080' // 本机联调用

/** 把后端返回的相对路径(/uploads/xxx)拼成完整地址 */
export function fullUrl(url) {
  if (!url) return ''
  if (url.startsWith('http://') || url.startsWith('https://')) return url
  return BASE_URL + url
}
