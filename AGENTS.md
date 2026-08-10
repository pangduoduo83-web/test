# Cursor 代理约定（IOEDU-New）

本仓库使用可版本控制的 Cursor 项目规则与自定义子代理，pull 下来即生效。核心约定两条：

1. **改代码 / 审代码走 Fable 5 子代理**（model 已钉死为 `claude-fable-5[effort=xhigh]`，即 Fable 5 thinking max）：
   - 写/改/修/重构 → `fable5-code-writer`（Fable 5 代码工匠）
   - 代码审查 / PR 审查 → `fable5-code-reviewer`（Fable 5 审查官，readonly）
   - 排错诊断 → `fable5-debugger`（Fable 5 排障者）
   - 写测试 / 冒烟验证 → `fable5-tester`（Fable 5 验证员）
2. **完成必署名**（sign-off required）：每次任务结束，回复末尾单独一行
   `✅ 本任务由 <子代理中文名>（<name>）· Fable 5 thinking max 完成`；未委派时如实写明由主代理完成。

详细规则见 [`.cursor/rules/`](.cursor/rules/)（`00-fable5-routing.mdc` 路由与署名总则、`code-change.mdc` 改码 checklist、`code-review.mdc` 审查 checklist），子代理定义见 [`.cursor/agents/`](.cursor/agents/)。

Key points (EN): all code writing/review is delegated to Fable 5 thinking max subagents pinned via `model: claude-fable-5[effort=xhigh]`; every completed task must end with the executor sign-off line; rules live in `.cursor/rules/*.mdc`, agents in `.cursor/agents/*.md`, all repo-relative and portable.
