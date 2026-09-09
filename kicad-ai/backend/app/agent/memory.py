"""Long-term memory helpers (deepagents built-in file memory).

Memory lives at ``/memories/AGENTS.md`` inside the agent's virtual filesystem.
The ``/memories/`` route is backed by a LangGraph store namespaced per user,
so every user has private, cross-conversation memory.
"""

from __future__ import annotations

from deepagents.backends.utils import create_file_data
from langgraph.store.base import BaseStore

MEMORY_ROUTE = "/memories/"
MEMORY_FILE = "/memories/AGENTS.md"
# CompositeBackend strips the route prefix before handing the key to StoreBackend.
STORE_KEY = "/AGENTS.md"
# The memory file is injected into every system prompt; keep it a summary, not a log.
MEMORY_MAX_CHARS = 6_000


class MemoryTooLarge(ValueError):
    def __init__(self, size: int) -> None:
        super().__init__(
            f"记忆文件超出上限（{size} / {MEMORY_MAX_CHARS} 字符）。"
            "请精简内容：合并重复条目、删除过时经验，只保留长期有效的偏好与约定。"
        )
        self.size = size


def check_memory_size(content: str) -> None:
    if len(content) > MEMORY_MAX_CHARS:
        raise MemoryTooLarge(len(content))


def memory_namespace(user_id: str) -> tuple[str, ...]:
    return ("memories", user_id)


DEFAULT_MEMORY = """# 用户记忆 (AGENTS.md)

此文件由 KiCad AI 助手维护，用于记录当前用户的长期偏好与项目约定。
当用户表达明确偏好时，请用 edit_file 更新对应小节；不要写入密钥或敏感信息。

## 用户偏好
- 回复语言：中文
- 单位：毫米 (mm)
- PCB 栅格：0.1 mm (SMD) / 1.27 mm (通孔)

## 项目约定
- （暂无）

## 历史经验
- （暂无）
"""


async def ensure_user_memory(store: BaseStore, user_id: str) -> None:
    ns = memory_namespace(user_id)
    existing = await store.aget(ns, STORE_KEY)
    if existing is None:
        await store.aput(ns, STORE_KEY, create_file_data(DEFAULT_MEMORY))


async def read_user_memory(store: BaseStore, user_id: str) -> str:
    item = await store.aget(memory_namespace(user_id), STORE_KEY)
    if item is None:
        return ""
    value = item.value if hasattr(item, "value") else item
    if isinstance(value, dict):
        content = value.get("content", "")
        if isinstance(content, list):
            return "\n".join(str(c) for c in content)
        return str(content)
    return str(value)


async def write_user_memory(store: BaseStore, user_id: str, content: str) -> None:
    check_memory_size(content)
    await store.aput(memory_namespace(user_id), STORE_KEY, create_file_data(content))
