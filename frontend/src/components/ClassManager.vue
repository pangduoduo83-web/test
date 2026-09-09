<template>
  <div>
    <div class="stat-grid">
      <div class="ref-stat-card">
        <div class="ref-stat-icon" style="background:linear-gradient(135deg,#60a5fa,#2563eb)"><Users :size="22" color="#fff" /></div>
        <div><div class="ref-stat-value">{{ classes.length }}</div><div class="ref-stat-label">{{ mode === 'admin' ? '全站班级' : '我的班级' }}</div></div>
      </div>
      <div class="ref-stat-card">
        <div class="ref-stat-icon" style="background:linear-gradient(135deg,#4ade80,#16a34a)"><GraduationCap :size="22" color="#fff" /></div>
        <div><div class="ref-stat-value">{{ classes.reduce((s, c) => s + (c.memberCount || 0), 0) }}</div><div class="ref-stat-label">班级学生(含重复)</div></div>
      </div>
      <div class="ref-stat-card">
        <div class="ref-stat-icon" style="background:linear-gradient(135deg,#c084fc,#9333ea)"><ClipboardList :size="22" color="#fff" /></div>
        <div><div class="ref-stat-value">{{ classes.reduce((s, c) => s + (c.assignmentCount || 0), 0) }}</div><div class="ref-stat-label">已布置项目</div></div>
      </div>
    </div>

    <div class="card">
      <div class="card-head">
        <div>
          <h3>{{ mode === 'admin' ? '班级管理' : '我的班级' }}</h3>
          <p class="sub">建班后把加入码发给学生自助加入,或按学号 / 邮箱批量拉入;给班级布置项目会自动为全班报名并统一截止日期。</p>
        </div>
        <el-button type="primary" @click="openCreate">+ 新建班级</el-button>
      </div>

      <el-empty v-if="classes.length === 0" description="还没有班级,点右上角新建一个" />
      <div v-else class="class-grid">
        <div v-for="c in classes" :key="c.id" class="class-card" :class="{ archived: c.status === 'ARCHIVED' }" @click="openDetail(c)">
          <div class="cc-top">
            <span class="cc-avatar">{{ c.name[0] }}</span>
            <div class="cc-title">
              <b>{{ c.name }}</b>
              <div class="cc-sub">{{ c.teacherName || '未指定教师' }}<span v-if="c.status === 'ARCHIVED'"> · 已归档</span></div>
            </div>
          </div>
          <div class="cc-desc">{{ c.description || '暂无简介' }}</div>
          <div class="cc-meta">
            <span><Users :size="13" /> {{ c.memberCount }} 人</span>
            <span><ClipboardList :size="13" /> {{ c.assignmentCount }} 个项目</span>
            <span class="cc-code" @click.stop="copy(c.joinCode)" title="点击复制加入码"><KeyRound :size="13" /> {{ c.joinCode }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 新建 -->
    <el-dialog v-model="createVisible" title="新建班级" width="480px">
      <el-form label-position="top">
        <el-form-item label="班级名称" required><el-input v-model="createForm.name" maxlength="60" placeholder="如: 2024 级电子信息 2 班 · 嵌入式实践" /></el-form-item>
        <el-form-item label="简介"><el-input v-model="createForm.description" type="textarea" :rows="2" maxlength="300" placeholder="课程说明、上课时间等(选填)" /></el-form-item>
        <el-form-item v-if="mode === 'admin'" label="授课教师">
          <el-select v-model="createForm.teacherId" filterable placeholder="不选则为当前管理员" style="width:100%">
            <el-option v-for="t in teachers" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="create">创建</el-button>
      </template>
    </el-dialog>

    <!-- 班级详情抽屉 -->
    <el-drawer v-model="detailVisible" size="900px" :with-header="false">
      <div v-if="detail" class="detail">
        <div class="d-head">
          <span class="cc-avatar big">{{ detail.name[0] }}</span>
          <div class="grow">
            <div class="d-title">{{ detail.name }} <span class="badge" :class="detail.status === 'ACTIVE' ? 'badge-green' : 'badge-gray'">{{ detail.status === 'ACTIVE' ? '进行中' : '已归档' }}</span></div>
            <div class="d-sub">{{ detail.teacherName }} · {{ detail.members.length }} 名学生 · {{ detail.assignments.length }} 个项目</div>
          </div>
          <div class="join-box">
            <div class="join-label">加入码 <el-switch v-model="detail.joinEnabled" size="small" inline-prompt active-text="开" inactive-text="关" @change="(v) => patch({ joinEnabled: v })" /></div>
            <div class="join-code" @click="copy(detail.joinCode)" title="点击复制">{{ detail.joinCode }}</div>
            <div class="join-tip">学生在「我的班级」输入即可加入 · <a @click="regenerate">换一个</a></div>
          </div>
          <el-dropdown @command="onCommand">
            <el-button text>更多 ▾</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="edit">编辑名称 / 简介</el-dropdown-item>
                <el-dropdown-item command="archive">{{ detail.status === 'ACTIVE' ? '归档班级' : '恢复班级' }}</el-dropdown-item>
                <el-dropdown-item command="delete" divided>删除班级</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <el-tabs v-model="tab">
          <el-tab-pane :label="`学生 (${detail.members.length})`" name="members">
            <div class="tab-tools">
              <el-input v-model="identifiers" type="textarea" :autosize="{ minRows: 1, maxRows: 4 }" placeholder="按学号 / 邮箱 / 手机号批量加入,逗号、空格或换行分隔" class="grow" />
              <el-button type="primary" :loading="saving" @click="addMembers">加入学生</el-button>
              <el-button :disabled="!detail.members.length" @click="exportRoster">导出名单与进度</el-button>
            </div>
            <el-empty v-if="detail.members.length === 0" description="还没有学生。把加入码发给学生,或在上方按学号批量加入" />
            <el-table v-else :data="detail.members" size="small" max-height="460">
              <el-table-column label="学生" min-width="150">
                <template #default="{ row }"><b>{{ row.name }}</b><div class="muted">{{ row.studentNo || '–' }} · {{ row.major || '–' }}</div></template>
              </el-table-column>
              <el-table-column v-for="a in detail.assignments" :key="a.id" :label="a.projectTitle" min-width="150">
                <template #default="{ row }">
                  <template v-if="cell(row, a.id)">
                    <span v-if="cell(row, a.id).status === 'COMPLETED'" class="badge badge-green">已完成</span>
                    <span v-else-if="cell(row, a.id).status === 'NOT_ENROLLED'" class="badge badge-gray">未报名</span>
                    <div v-else class="cell-progress">
                      <el-progress :percentage="cell(row, a.id).progress || 0" :stroke-width="6" :color="cell(row, a.id).overdue ? '#dc2626' : '#3b82f6'" />
                      <span v-if="cell(row, a.id).overdue" class="overdue">已过截止</span>
                    </div>
                  </template>
                </template>
              </el-table-column>
              <el-table-column label="最近活动" width="120">
                <template #default="{ row }"><span class="muted">{{ row.lastActiveAt ? fmt(row.lastActiveAt) : '无' }}</span></template>
              </el-table-column>
              <el-table-column width="70">
                <template #default="{ row }"><el-button size="small" text type="danger" @click="removeMember(row)">移出</el-button></template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane :label="`布置的项目 (${detail.assignments.length})`" name="assignments">
            <div class="tab-tools">
              <el-select v-model="assignForm.projectId" filterable placeholder="选择要布置的项目" style="width:280px">
                <el-option v-for="p in projects" :key="p.id" :label="p.title" :value="p.id" />
              </el-select>
              <el-date-picker v-model="assignForm.deadline" type="date" value-format="YYYY-MM-DD" placeholder="截止日期(选填)" style="width:170px" />
              <el-input v-model="assignForm.note" placeholder="要求说明(选填)" class="grow" maxlength="300" />
              <el-button type="primary" :loading="saving" @click="assign">布置给全班</el-button>
            </div>
            <el-empty v-if="detail.assignments.length === 0" description="还没有布置项目。布置后全班学生自动报名,统一按这里的截止日期" />
            <div v-for="a in detail.assignments" :key="a.id" class="assign-row">
              <div class="grow">
                <b>{{ a.projectTitle }}</b>
                <div class="muted">截止 {{ a.deadline || '未设置' }} · 布置于 {{ fmt(a.createdAt) }}<span v-if="a.note"> · {{ a.note }}</span></div>
                <div class="assign-stat">{{ assignStat(a.id) }}</div>
              </div>
              <el-button size="small" @click="editAssignment(a)">改截止/说明</el-button>
              <el-button size="small" text @click="$router.push(`/app/projects/${a.projectId}`)">看项目</el-button>
              <el-button size="small" text type="danger" @click="removeAssignment(a)">撤销</el-button>
            </div>
          </el-tab-pane>

          <el-tab-pane :label="`公告 (${detail.announcements.length})`" name="announcements">
            <div class="announce-form">
              <el-input v-model="announceForm.title" placeholder="公告标题,如: 本周五前完成第 2 阶段" maxlength="100" />
              <el-input v-model="announceForm.content" type="textarea" :rows="3" placeholder="正文(选填)。发布后全班每人收到一条站内通知" maxlength="1000" />
              <div style="text-align:right"><el-button type="primary" :loading="saving" @click="announce">发布公告</el-button></div>
            </div>
            <el-empty v-if="detail.announcements.length === 0" description="还没有公告" />
            <div v-for="an in detail.announcements" :key="an.id" class="announce-item">
              <div class="an-head"><b>{{ an.title }}</b><span class="muted">{{ an.authorName }} · {{ fmt(an.createdAt) }}</span></div>
              <div v-if="an.content" class="an-body">{{ an.content }}</div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>

    <!-- 编辑名称 -->
    <el-dialog v-model="editVisible" title="编辑班级" width="480px">
      <el-form label-position="top">
        <el-form-item label="班级名称" required><el-input v-model="editForm.name" maxlength="60" /></el-form-item>
        <el-form-item label="简介"><el-input v-model="editForm.description" type="textarea" :rows="2" maxlength="300" /></el-form-item>
        <el-form-item v-if="mode === 'admin'" label="授课教师">
          <el-select v-model="editForm.teacherId" filterable style="width:100%">
            <el-option v-for="t in teachers" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 改截止 -->
    <el-dialog v-model="assignEditVisible" title="修改作业" width="440px">
      <el-form label-position="top">
        <el-form-item label="截止日期"><el-date-picker v-model="assignEdit.deadline" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="assignEdit.note" maxlength="300" /></el-form-item>
      </el-form>
      <p class="muted">修改截止日期会同步到班里每个仍在进行的报名。</p>
      <template #footer>
        <el-button @click="assignEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveAssignment">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ClipboardList, GraduationCap, KeyRound, Users } from 'lucide-vue-next'
import {
  teacherAddClassMembers, teacherAssignClass, teacherClassAnnounce, teacherClassDetail, teacherClasses, teacherCreateClass,
  teacherDeleteClass, teacherProjects, teacherRemoveAssignment, teacherRemoveClassMember, teacherUpdateAssignment, teacherUpdateClass
} from '../api'
import { downloadCsv } from '../utils/csv'

const props = defineProps({
  /** admin | teacher */
  mode: { type: String, default: 'teacher' },
  /** 管理员模式下可选的授课教师列表 [{id,name}] */
  teachers: { type: Array, default: () => [] }
})

const classes = ref([])
const projects = ref([])
const saving = ref(false)
const createVisible = ref(false)
const createForm = reactive({ name: '', description: '', teacherId: null })
const detailVisible = ref(false)
const detail = ref(null)
const tab = ref('members')
const identifiers = ref('')
const assignForm = reactive({ projectId: null, deadline: '', note: '' })
const announceForm = reactive({ title: '', content: '' })
const editVisible = ref(false)
const editForm = reactive({ name: '', description: '', teacherId: null })
const assignEditVisible = ref(false)
const assignEdit = reactive({ id: null, deadline: '', note: '' })

const fmt = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '')
const cell = (row, assignmentId) => (row.progress || []).find((p) => p.assignmentId === assignmentId)
const assignStat = (assignmentId) => {
  const cells = detail.value.members.map((m) => cell(m, assignmentId)).filter(Boolean)
  const done = cells.filter((c) => c.status === 'COMPLETED').length
  const overdue = cells.filter((c) => c.overdue).length
  const avg = cells.length ? Math.round(cells.reduce((s, c) => s + (c.progress || 0), 0) / cells.length) : 0
  return `${done}/${cells.length} 人完成 · 平均进度 ${avg}%${overdue ? ` · ${overdue} 人已过截止` : ''}`
}

const load = async () => { classes.value = await teacherClasses() }

const openCreate = () => {
  Object.assign(createForm, { name: '', description: '', teacherId: null })
  createVisible.value = true
}
const create = async () => {
  if (!createForm.name.trim()) { ElMessage.warning('请填写班级名称'); return }
  saving.value = true
  try {
    const c = await teacherCreateClass({ ...createForm })
    createVisible.value = false
    ElMessage.success(`班级已创建,加入码 ${c.joinCode}`)
    await load()
    await openDetail(c)
  } finally {
    saving.value = false
  }
}

const openDetail = async (c) => {
  detail.value = await teacherClassDetail(c.id)
  tab.value = 'members'
  identifiers.value = ''
  Object.assign(assignForm, { projectId: null, deadline: '', note: '' })
  Object.assign(announceForm, { title: '', content: '' })
  detailVisible.value = true
  if (projects.value.length === 0) projects.value = await teacherProjects()
}
const refresh = async () => {
  detail.value = await teacherClassDetail(detail.value.id)
  await load()
}
const patch = async (body) => {
  await teacherUpdateClass(detail.value.id, body)
  await refresh()
}
const regenerate = async () => {
  try { await ElMessageBox.confirm('换新加入码后,旧码立即失效。', '更换加入码', { type: 'warning' }) } catch (e) { return }
  await patch({ regenerateCode: true })
  ElMessage.success('加入码已更换')
}
const onCommand = async (cmd) => {
  if (cmd === 'edit') {
    Object.assign(editForm, { name: detail.value.name, description: detail.value.description || '', teacherId: detail.value.teacherId })
    editVisible.value = true
  } else if (cmd === 'archive') {
    await patch({ status: detail.value.status === 'ACTIVE' ? 'ARCHIVED' : 'ACTIVE' })
    ElMessage.success(detail.value.status === 'ACTIVE' ? '班级已恢复' : '班级已归档')
  } else if (cmd === 'delete') {
    try { await ElMessageBox.confirm(`删除班级「${detail.value.name}」?学生已有的报名与成果不会被删除。`, '删除班级', { type: 'warning', confirmButtonText: '删除' }) } catch (e) { return }
    await teacherDeleteClass(detail.value.id)
    detailVisible.value = false
    ElMessage.success('已删除')
    await load()
  }
}
const saveEdit = async () => {
  if (!editForm.name.trim()) { ElMessage.warning('请填写班级名称'); return }
  saving.value = true
  try {
    await patch({ name: editForm.name, description: editForm.description, teacherId: props.mode === 'admin' ? editForm.teacherId : undefined })
    editVisible.value = false
    ElMessage.success('已保存')
  } finally {
    saving.value = false
  }
}

const addMembers = async () => {
  const ids = identifiers.value.split(/[\s,，;；]+/).map((s) => s.trim()).filter(Boolean)
  if (!ids.length) { ElMessage.warning('请输入学号、邮箱或手机号'); return }
  saving.value = true
  try {
    const r = await teacherAddClassMembers(detail.value.id, ids)
    const parts = [`加入 ${r.added.length} 人`]
    if (r.existed.length) parts.push(`${r.existed.length} 人已在班`)
    if (r.notFound.length) parts.push(`未找到:${r.notFound.join('、')}`)
    if (r.notStudent.length) parts.push(`非学生账号:${r.notStudent.join('、')}`)
    ElMessage[r.notFound.length || r.notStudent.length ? 'warning' : 'success'](parts.join(';'))
    identifiers.value = ''
    await refresh()
  } finally {
    saving.value = false
  }
}
const removeMember = async (row) => {
  try { await ElMessageBox.confirm(`把 ${row.name} 移出班级?其报名与成果保留。`, '移出学生', { type: 'warning' }) } catch (e) { return }
  await teacherRemoveClassMember(detail.value.id, row.userId)
  await refresh()
}

const assign = async () => {
  if (!assignForm.projectId) { ElMessage.warning('请选择项目'); return }
  saving.value = true
  try {
    const r = await teacherAssignClass(detail.value.id, { ...assignForm })
    ElMessage.success(`已布置《${r.projectTitle}》,新报名 ${r.newlyEnrolled} 人`)
    Object.assign(assignForm, { projectId: null, deadline: '', note: '' })
    await refresh()
  } finally {
    saving.value = false
  }
}
const editAssignment = (a) => {
  Object.assign(assignEdit, { id: a.id, deadline: a.deadline || '', note: a.note || '' })
  assignEditVisible.value = true
}
const saveAssignment = async () => {
  saving.value = true
  try {
    await teacherUpdateAssignment(detail.value.id, assignEdit.id, { deadline: assignEdit.deadline || null, note: assignEdit.note })
    assignEditVisible.value = false
    ElMessage.success('已更新,截止日期已同步到学生')
    await refresh()
  } finally {
    saving.value = false
  }
}
const removeAssignment = async (a) => {
  try { await ElMessageBox.confirm(`撤销布置《${a.projectTitle}》?学生已有的报名与成果保留。`, '撤销作业', { type: 'warning' }) } catch (e) { return }
  await teacherRemoveAssignment(detail.value.id, a.id)
  await refresh()
}

const announce = async () => {
  if (!announceForm.title.trim()) { ElMessage.warning('请填写公告标题'); return }
  saving.value = true
  try {
    await teacherClassAnnounce(detail.value.id, { ...announceForm })
    ElMessage.success(`公告已发布,${detail.value.members.length} 名学生已收到通知`)
    Object.assign(announceForm, { title: '', content: '' })
    await refresh()
  } finally {
    saving.value = false
  }
}

const exportRoster = () => {
  const d = detail.value
  const statusText = (c) => (!c ? '' : c.status === 'COMPLETED' ? '已完成' : c.status === 'NOT_ENROLLED' ? '未报名' : (c.overdue ? '已过截止 ' : '进行中 ') + (c.progress || 0) + '%')
  downloadCsv(`${d.name}-名单与进度`,
    ['姓名', '学号', '专业', '加入时间', '最近活动', ...d.assignments.map((a) => `${a.projectTitle}(截止 ${a.deadline || '未设'})`)],
    d.members.map((m) => [m.name, m.studentNo || '', m.major || '', fmt(m.joinedAt), m.lastActiveAt ? fmt(m.lastActiveAt) : '无',
      ...d.assignments.map((a) => statusText(cell(m, a.id)))]))
}

const copy = async (text) => {
  try { await navigator.clipboard.writeText(text); ElMessage.success(`加入码 ${text} 已复制`) } catch (e) { ElMessage.info(text) }
}

onMounted(load)
</script>

<style scoped>
.stat-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-bottom: 20px; }
.card-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; margin-bottom: 16px; }
.card-head h3 { margin: 0 0 4px; font-size: 16px; }
.sub { margin: 0; font-size: 13px; color: var(--text-secondary); line-height: 1.6; }
.muted { font-size: 12px; color: var(--text-secondary); }
.grow { flex: 1; min-width: 0; }

.class-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 14px; }
.class-card { border: 1px solid var(--border); border-radius: 14px; padding: 16px; cursor: pointer; transition: box-shadow .15s, transform .15s; background: #fff; }
.class-card:hover { box-shadow: var(--shadow-lg); transform: translateY(-2px); }
.class-card.archived { opacity: .65; }
.cc-top { display: flex; align-items: center; gap: 12px; }
.cc-avatar { width: 40px; height: 40px; border-radius: 12px; background: var(--brand-gradient-br); color: #fff; font-weight: 800; display: grid; place-items: center; flex-shrink: 0; }
.cc-avatar.big { width: 52px; height: 52px; font-size: 20px; }
.cc-title b { font-size: 15px; }
.cc-sub { font-size: 12px; color: var(--text-secondary); margin-top: 2px; }
.cc-desc { font-size: 13px; color: var(--text-secondary); margin: 12px 0; min-height: 20px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cc-meta { display: flex; gap: 14px; font-size: 12px; color: var(--text-secondary); align-items: center; }
.cc-meta span { display: inline-flex; align-items: center; gap: 4px; }
.cc-code { margin-left: auto; font-family: ui-monospace, Menlo, Consolas, monospace; background: #f3f4f6; padding: 2px 8px; border-radius: 6px; color: #374151; }

.d-head { display: flex; align-items: center; gap: 14px; }
.d-title { font-size: 18px; font-weight: 800; display: flex; align-items: center; gap: 8px; }
.d-sub { font-size: 13px; color: var(--text-secondary); margin-top: 4px; }
.join-box { background: #eef2ff; border-radius: 12px; padding: 10px 14px; text-align: center; min-width: 170px; }
.join-label { font-size: 11px; color: #4338ca; display: flex; align-items: center; justify-content: center; gap: 8px; }
.join-code { font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 22px; font-weight: 800; letter-spacing: 3px; color: #312e81; cursor: pointer; margin: 4px 0; }
.join-tip { font-size: 11px; color: #6366f1; }
.join-tip a { cursor: pointer; text-decoration: underline; }
.tab-tools { display: flex; gap: 10px; align-items: flex-start; margin-bottom: 14px; flex-wrap: wrap; }
.cell-progress { display: flex; align-items: center; gap: 8px; }
.cell-progress :deep(.el-progress) { flex: 1; }
.cell-progress :deep(.el-progress__text) { font-size: 12px !important; }
.overdue { font-size: 11px; color: #dc2626; white-space: nowrap; }
.assign-row { display: flex; align-items: center; gap: 10px; padding: 12px 14px; border: 1px solid var(--border); border-radius: 12px; margin-bottom: 10px; }
.assign-stat { font-size: 12px; color: #2563eb; margin-top: 4px; }
.announce-form { display: flex; flex-direction: column; gap: 10px; margin-bottom: 16px; padding: 14px; background: #f9fafb; border-radius: 12px; }
.announce-item { padding: 12px 14px; border: 1px solid var(--border); border-radius: 12px; margin-bottom: 10px; }
.an-head { display: flex; justify-content: space-between; align-items: center; gap: 10px; font-size: 14px; }
.an-body { font-size: 13px; color: #374151; margin-top: 6px; white-space: pre-wrap; line-height: 1.6; }
</style>
