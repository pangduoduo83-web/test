# Cursor 代理约定（IOEDU-New）

本仓库使用可版本控制的 Cursor 项目规则与自定义子代理，pull 下来即生效。核心约定两条：

1. **改代码 / 审代码 / 问答咨询都走 GPT 5.6 Sol 子代理**（agent frontmatter 的 model 已钉死为 `gpt-5.6-sol[effort=xhigh,fast=true]`，父代理通过 Task 工具调用时使用 slug `gpt-5.6-sol-xhigh-fast`，即 GPT 5.6 Sol max thinking fast）：
   - 写/改/修/重构 → `sol-code-writer`（Sol 代码工匠）
   - 代码审查 / PR 审查 → `sol-code-reviewer`（Sol 审查官，readonly）
   - 排错诊断 → `sol-debugger`（Sol 排障者）
   - 写测试 / 冒烟验证 → `sol-tester`（Sol 验证员）
   - 问答 / 咨询 / 方案建议 / 如何部署 → `sol-advisor`（Sol 顾问）；实质性技术问答父代理也不得自答，必须委派
2. **完成必署名**（sign-off required）：每次任务结束，回复末尾单独一行
   `✅ 本任务由 <子代理中文名>（<name>）· GPT 5.6 Sol max thinking fast 完成`；未委派时如实写明由主代理完成。

详细规则见 [`.cursor/rules/`](.cursor/rules/)（`00-model-routing.mdc` 路由与署名总则、`code-change.mdc` 改码 checklist、`code-review.mdc` 审查 checklist），子代理定义见 [`.cursor/agents/`](.cursor/agents/)。

Key points (EN): all code writing/review AND substantive Q&A/consulting are delegated to GPT 5.6 Sol max thinking fast subagents pinned via `model: gpt-5.6-sol[effort=xhigh,fast=true]`; Task calls use `gpt-5.6-sol-xhigh-fast`; every completed task must end with the executor sign-off line; rules live in `.cursor/rules/*.mdc`, agents in `.cursor/agents/*.md`, all repo-relative and portable.
