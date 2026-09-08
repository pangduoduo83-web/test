/**
 * 本地模拟大模型服务(OpenAI 兼容 /chat/completions),用于 AI 功能联调,零依赖。
 * 支持:JSON 模式回复(学习规划师 / 成果预评审)、流式 SSE(stream:true)、工具调用(tools)。
 * 用法:node scripts/mock-ai-server.mjs
 * 后端配置:IOEDU_AI_BASE_URL=http://localhost:9281  IOEDU_AI_API_KEY=mock-key
 */
import http from 'node:http'

const PORT = 9281

const server = http.createServer((req, res) => {
  if (req.method !== 'POST' || !req.url.includes('/chat/completions')) {
    res.writeHead(404)
    res.end()
    return
  }
  let body = ''
  req.on('data', (c) => (body += c))
  req.on('end', () => {
    let payload = {}
    try { payload = JSON.parse(body) } catch (e) { /* ignore */ }
    const reply = decide(payload)
    if (payload.stream) streamReply(res, reply)
    else jsonReply(res, reply)
  })
})

/** 决定回复:{ content } 或 { toolCalls: [{id,name,arguments}] } */
function decide(payload) {
  const messages = payload.messages || []
  const system = messages[0]?.content || ''
  const last = messages[messages.length - 1] || {}
  const tools = (payload.tools || []).map((t) => t.function?.name).filter(Boolean)

  // 旧场景:JSON 模式的规划师 / 助教
  if (tools.length === 0 && payload.response_format?.type === 'json_object') {
    try {
      const user = JSON.parse(messages[1]?.content || '{}')
      if (system.includes('规划师')) return { content: planReply(user) }
      if (system.includes('助教')) return { content: reviewReply() }
    } catch (e) { /* fallthrough */ }
    if (system.includes('BOM')) {
      return { content: JSON.stringify({ score: 72, missing: ['去耦电容 100nF'], issues: [{ item: 'R1', problem: '封装未标注', suggestion: '补充 0603/0805' }], alternatives: [{ item: 'CH340C', alternative: 'CP2102' }], summary: '(模拟AI)BOM 基本完整,缺少去耦与封装信息。' }) }
    }
    return { content: JSON.stringify({ pong: true }) }
  }

  // 工具场景:用户消息后先发起一次工具调用,拿到工具结果后再给最终回答
  if (last.role === 'user' && tools.length > 0) {
    const text = String(last.content || '')
    if (text.includes('借') && tools.includes('borrow.apply')) {
      return { toolCalls: [{ id: 'call_apply_1', name: 'borrow.apply', arguments: JSON.stringify({ equipmentId: 1, quantity: 1, purpose: '课程实验', durationDays: 3 }) }] }
    }
    const preferred = ['skill.my_scores', 'equipment.search', 'project.search', 'borrow.my_list'].find((t) => tools.includes(t)) || tools[0]
    const args = preferred === 'equipment.search' || preferred === 'project.search' ? { keyword: '' } : {}
    return { toolCalls: [{ id: 'call_1', name: preferred, arguments: JSON.stringify(args) }] }
  }
  if (last.role === 'tool') {
    let count = ''
    try {
      const parsed = JSON.parse(last.content)
      count = Array.isArray(parsed) ? `共 ${parsed.length} 条记录` : parsed.error ? `工具返回错误:${parsed.error}` : `返回对象 ${Object.keys(parsed).slice(0, 4).join('/')}`
    } catch (e) { count = '' }
    return { content: `(模拟AI)我已经调用工具 **${last.name || ''}** 查询了平台数据(${count})。\n\n基于这些数据,建议你:\n- 先从匹配度最高的项目开始\n- 借用设备前确认库存与到期日\n\n还有什么想了解的吗?` }
  }
  const text = String(last.content || '')
  return { content: `(模拟AI)你刚才说:「${text.slice(0, 60)}」。这是一段模拟的流式回复,用于验证前后端链路是否正常工作。` }
}

function jsonReply(res, reply) {
  const message = reply.toolCalls
    ? { role: 'assistant', content: null, tool_calls: reply.toolCalls.map((c) => ({ id: c.id, type: 'function', function: { name: c.name, arguments: c.arguments } })) }
    : { role: 'assistant', content: reply.content }
  res.writeHead(200, { 'Content-Type': 'application/json' })
  res.end(JSON.stringify({
    choices: [{ index: 0, message, finish_reason: reply.toolCalls ? 'tool_calls' : 'stop' }],
    usage: { prompt_tokens: 120, completion_tokens: 40 }
  }))
}

function streamReply(res, reply) {
  res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache', Connection: 'keep-alive' })
  const send = (obj) => res.write(`data: ${JSON.stringify(obj)}\n\n`)
  const chunk = (delta, finish = null) => ({ id: 'mock', object: 'chat.completion.chunk', choices: [{ index: 0, delta, finish_reason: finish }] })
  if (reply.toolCalls) {
    reply.toolCalls.forEach((c, i) => {
      send(chunk({ role: 'assistant', tool_calls: [{ index: i, id: c.id, type: 'function', function: { name: c.name, arguments: '' } }] }))
      const half = Math.ceil(c.arguments.length / 2)
      send(chunk({ tool_calls: [{ index: i, function: { arguments: c.arguments.slice(0, half) } }] }))
      send(chunk({ tool_calls: [{ index: i, function: { arguments: c.arguments.slice(half) } }] }))
    })
    send(chunk({}, 'tool_calls'))
    send({ id: 'mock', object: 'chat.completion.chunk', choices: [], usage: { prompt_tokens: 120, completion_tokens: 20 } })
    res.write('data: [DONE]\n\n')
    res.end()
    return
  }
  const pieces = reply.content.match(/[\s\S]{1,6}/g) || []
  let i = 0
  const timer = setInterval(() => {
    if (i < pieces.length) {
      send(chunk({ content: pieces[i++] }))
      return
    }
    clearInterval(timer)
    send(chunk({}, 'stop'))
    send({ id: 'mock', object: 'chat.completion.chunk', choices: [], usage: { prompt_tokens: 120, completion_tokens: pieces.length } })
    res.write('data: [DONE]\n\n')
    res.end()
  }, 20)
}

function planReply(input) {
  const candidates = input.candidates || []
  const picked = candidates.slice(0, 3)
  const skills = Object.entries(input.student?.skills || {}).sort((a, b) => a[1] - b[1])
  return JSON.stringify({
    summary:
      '(模拟AI)你的技能画像整体均衡,建议优先补强薄弱维度,再通过综合项目串联能力,最后挑战高阶项目。',
    focusSkills: skills.slice(0, 2).map(([name, score]) => ({
      name,
      targetScore: Math.min(100, score + 15),
      reason: `当前 ${score} 分,是候选项目普遍要求的基础维度`
    })),
    recommendedProjects: picked.map((c, i) => ({
      projectId: c.projectId,
      stage: i + 1,
      reasons: [`匹配度 ${c.matchScore}%,与技能画像契合`, `${c.difficulty}难度,阶段递进合理`],
      skillGaps: i === 0 ? ['薄弱维度需在本项目中重点练习'] : [],
      nextAction: '阅读教学大纲第一阶段,准备所需设备后开工'
    }))
  })
}

function reviewReply() {
  return JSON.stringify({
    suggestedScore: 78,
    summary: '(模拟AI)成果完成度较好,实现思路和问题解决过程描述具体,表述有条理。',
    strengths: ['实现思路完整', '问题定位与解决过程具体'],
    weaknesses: ['缺少测试数据与验证说明'],
    feedbackDraft:
      '完成度良好,思路清晰;建议补充测试数据或波形截图说明,验证系统稳定性,期待你的下一次迭代。',
    skillEvidence: [{ name: '嵌入式开发', level: 68, basis: '完成了外设驱动与中断处理' }]
  })
}

server.listen(PORT, () => console.log(`mock-ai listening on http://localhost:${PORT}`))
