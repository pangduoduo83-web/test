import { getToken } from '../utils/authStorage'

/**
 * AI 对话的流式客户端:axios 不支持浏览器端流式读取,这里用 fetch + ReadableStream 解析 SSE。
 * handlers: { onDelta(text), onToolCall({name,args}), onToolResult({name,ok,summary}),
 *             onConfirmRequired({tool,args,description}), onDone(result), onError(message) }
 * 返回 AbortController,可用于取消。
 */
export function streamChat(body, handlers) {
  const controller = new AbortController()
  const run = async () => {
    let resp
    try {
      resp = await fetch('/api/ai/chat/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${getToken() || ''}` },
        body: JSON.stringify(body),
        signal: controller.signal
      })
    } catch (e) {
      if (e.name !== 'AbortError') handlers.onError?.('网络连接失败')
      return
    }
    if (!resp.ok || !resp.body) {
      let message = `请求失败(${resp.status})`
      try { message = (await resp.json()).message || message } catch (e) { /* ignore */ }
      handlers.onError?.(message)
      return
    }
    const reader = resp.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    let event = 'message'
    let data = ''
    let finished = false
    const dispatch = () => {
      if (!data) { event = 'message'; return }
      let payload = null
      try { payload = JSON.parse(data) } catch (e) { payload = { text: data } }
      switch (event) {
        case 'delta': handlers.onDelta?.(payload.text ?? ''); break
        case 'tool_call': handlers.onToolCall?.(payload); break
        case 'tool_result': handlers.onToolResult?.(payload); break
        case 'confirm_required': handlers.onConfirmRequired?.(payload); break
        case 'done': finished = true; handlers.onDone?.(payload); break
        case 'error': finished = true; handlers.onError?.(payload.message || '运行失败'); break
        default: break
      }
      event = 'message'
      data = ''
    }
    try {
      while (!finished) {
        const { value, done } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        let idx
        while ((idx = buffer.indexOf('\n')) >= 0) {
          const line = buffer.slice(0, idx).replace(/\r$/, '')
          buffer = buffer.slice(idx + 1)
          if (line === '') { dispatch(); continue }
          if (line.startsWith(':')) continue
          if (line.startsWith('event:')) event = line.slice(6).trim()
          else if (line.startsWith('data:')) data += line.slice(5).trimStart()
        }
      }
      if (!finished) dispatch()
      if (finished) reader.cancel().catch(() => {})
    } catch (e) {
      if (!finished && e.name !== 'AbortError') handlers.onError?.('连接中断')
    }
  }
  run()
  return controller
}
