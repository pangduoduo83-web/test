/**
 * 本地模拟大模型服务(OpenAI 兼容 /chat/completions),用于 AI 功能联调,零依赖。
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
    let content
    try {
      const payload = JSON.parse(body)
      const system = payload.messages[0].content
      const user = JSON.parse(payload.messages[1].content)
      content = system.includes('规划师') ? planReply(user) : reviewReply()
    } catch (e) {
      content = '{}'
    }
    res.writeHead(200, { 'Content-Type': 'application/json' })
    res.end(
      JSON.stringify({
        choices: [{ index: 0, message: { role: 'assistant', content }, finish_reason: 'stop' }]
      })
    )
  })
})

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
      '完成度良好,思路清晰;建议补充测试数据或波形截图说明,验证系统稳定性,期待你的下一次迭代。'
  })
}

server.listen(PORT, () => console.log(`mock-ai listening on http://localhost:${PORT}`))
