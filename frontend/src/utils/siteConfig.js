import { reactive } from 'vue'
import { fetchSiteConfig } from '../api'

/**
 * 站点配置(标题/LOGO/底部信息/注册开关/每页数量/分类),
 * 全局共享一份,首次使用时从后端加载,失败保持默认值。
 */
export const siteConfig = reactive({
  title: 'AI未来实践中心',
  logoUrl: '',
  footerText: '',
  allowRegister: true,
  projectPageSize: 9,
  equipmentPageSize: 9,
  projectCategories: [],
  equipmentCategories: []
})

let loaded = false

export async function loadSiteConfig(force = false) {
  if (loaded && !force) return siteConfig
  try {
    Object.assign(siteConfig, await fetchSiteConfig())
    loaded = true
    if (siteConfig.title) document.title = siteConfig.title
  } catch (e) { /* 加载失败用默认值 */ }
  return siteConfig
}
