import { BASE_URL } from '@/config'

/**
 * 富文本描述处理:
 * - 相对路径图片/视频补全为服务器地址
 * - img 注入自适应样式(rich-text 内图片默认会溢出)
 * - video 标签 rich-text 不支持,拆分成独立段落由 <video> 组件渲染
 * 返回 null 表示纯文本(调用方按普通文本展示)。
 */
export function prepareRich(html) {
  if (!html || !html.includes('<')) return null
  let h = html
    .replace(/src="\/uploads/g, `src="${BASE_URL}/uploads`)
    .replace(/<img([^>]*?)>/gi, (m, attrs) =>
      `<img${attrs.replace(/style="[^"]*"/gi, '')} style="max-width:100%;height:auto;border-radius:12rpx;display:block;margin:12rpx 0;">`)

  const segments = []
  const videoRe = /<video[^>]*?src="([^"]+)"[^>]*>(?:[\s\S]*?<\/video>)?/gi
  let last = 0
  let match
  while ((match = videoRe.exec(h))) {
    if (match.index > last) {
      segments.push({ type: 'html', content: h.slice(last, match.index) })
    }
    segments.push({ type: 'video', src: match[1] })
    last = match.index + match[0].length
  }
  if (last < h.length) {
    segments.push({ type: 'html', content: h.slice(last) })
  }
  return segments
}

/** 富文本转纯文本摘要(列表卡片用) */
export function stripHtml(v) {
  if (!v || !v.includes('<')) return v
  return v.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim()
}
