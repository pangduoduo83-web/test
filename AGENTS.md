# Cursor 代理约定（IOEDU-New）

本仓库携带可版本控制的 Cursor 项目规则（`.cursor/rules/`）与自定义子代理（`.cursor/agents/`）。**委派与署名约定只在云端代理会话中生效**，本地会话不受约束。

## 先判断会话环境

| 环境 | 判定依据 | 行为 |
|---|---|---|
| **云端代理**（Cursor Cloud Agent / Background Agent） | Linux 容器，工作区路径形如 `/workspace/...` | 读取并遵守 `.cursor/rules/00-model-routing.mdc`：改代码 / 审代码 / 问答咨询委派给 GPT 5.6 Sol 子代理，完成后署名 |
| **本地 Cursor 会话** | 开发者工作站（如 Windows，工作区在 `C:\Users\...` 下） | **不委派、不署名**，主代理直接实现、审查、作答；`code-change.mdc` / `code-review.mdc` 里的 checklist 仍可作为自查清单参考 |

有疑问时按本地处理。

## 云端会话的两条核心约定

1. **改代码 / 审代码 / 问答咨询都走 GPT 5.6 Sol 子代理**（agent frontmatter 的 model 已钉死为 `gpt-5.6-sol[effort=xhigh,fast=true]`，父代理通过 Task 工具调用时使用 slug `gpt-5.6-sol-xhigh-fast`，即 GPT 5.6 Sol max thinking fast）：
   - 写/改/修/重构 → `sol-code-writer`（Sol 代码工匠）
   - 代码审查 / PR 审查 → `sol-code-reviewer`（Sol 审查官，readonly）
   - 排错诊断 → `sol-debugger`（Sol 排障者）
   - 写测试 / 冒烟验证 → `sol-tester`（Sol 验证员）
   - 问答 / 咨询 / 方案建议 / 如何部署 → `sol-advisor`（Sol 顾问）；实质性技术问答父代理也不得自答，必须委派
2. **完成必署名**（sign-off required）：每次任务结束，回复末尾单独一行
   `✅ 本任务由 <子代理中文名>（<name>）· GPT 5.6 Sol max thinking fast 完成`；未委派时如实写明由主代理完成。

详细规则见 [`.cursor/rules/`](.cursor/rules/)（`00-model-routing.mdc` 路由与署名总则、`code-change.mdc` 改码 checklist、`code-review.mdc` 审查 checklist），子代理定义见 [`.cursor/agents/`](.cursor/agents/)。

Key points (EN): the GPT 5.6 Sol delegation + sign-off policy applies **only to Cursor Cloud Agent sessions** (Linux container, `/workspace/...`). In local Cursor sessions on the developer workstation (e.g. Windows, `C:\Users\...`) the main agent works directly with no delegation and no sign-off. Cloud sessions: delegate to the subagents pinned via `model: gpt-5.6-sol[effort=xhigh,fast=true]` (Task slug `gpt-5.6-sol-xhigh-fast`) and end every completed task with the executor sign-off line.
