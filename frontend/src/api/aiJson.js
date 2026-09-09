import { aiChat } from './index'

/**
 * 调用输出 JSON 的内嵌 SKILL(行动清单、今日建议、周报、项目起草),把模型返回解析成对象。
 * 模型偶尔会带 ```json 围栏或前后说明文字,这里做宽松提取。
 */
export async function runJsonSkill(skillKey, input, context) {
  const r = await aiChat({ skillKey, input, context })
  return parseJson(r?.content)
}

export function parseJson(text) {
  if (!text) throw new Error('AI 没有返回内容')
  let s = String(text).trim()
  const fence = s.match(/```(?:json)?\s*([\s\S]*?)```/i)
  if (fence) s = fence[1].trim()
  const start = s.indexOf('{')
  const end = s.lastIndexOf('}')
  if (start < 0 || end <= start) throw new Error('AI 返回的不是 JSON')
  return JSON.parse(s.slice(start, end + 1))
}

/** 带本地缓存:同一 key 当天(或指定 ttl 毫秒)内复用,避免每次打开页面都消耗额度 */
export async function runJsonSkillCached(cacheKey, ttlMs, skillKey, input, context, force = false) {
  const raw = force ? null : localStorage.getItem(cacheKey)
  if (raw) {
    try {
      const c = JSON.parse(raw)
      if (Date.now() - c.at < ttlMs) return { data: c.data, cached: true, at: c.at }
    } catch (e) { /* 缓存损坏则重算 */ }
  }
  const data = await runJsonSkill(skillKey, input, context)
  localStorage.setItem(cacheKey, JSON.stringify({ at: Date.now(), data }))
  return { data, cached: false, at: Date.now() }
}
