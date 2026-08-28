/**
 * 全局配置
 *
 * BASE_URL:后端服务地址(不带 /api 后缀)
 * - 线上(默认):HTTPS 备案域名,微信公众平台需配置 request/uploadFile/downloadFile
 *   合法域名 = https://www.labcloud.com.cn:8443
 * - HTTP 演示环境:http://139.224.189.163:8093(开发者工具勾选「不校验合法域名」,
 *   手机预览需在小程序右上角胶囊菜单打开「开发调试」)
 * - 本机联调:改回 http://localhost:8080(后端本地启动时)
 */
export const BASE_URL = 'https://www.labcloud.com.cn:8443'
// export const BASE_URL = 'http://139.224.189.163:8093' // HTTP 演示环境
// export const BASE_URL = 'http://localhost:8080' // 本机联调用

/** 把后端返回的相对路径(/uploads/xxx)拼成完整地址 */
export function fullUrl(url) {
  if (!url) return ''
  if (url.startsWith('http://') || url.startsWith('https://')) return url
  return BASE_URL + url
}
