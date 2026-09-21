<template>
  <div class="settings-page site-settings">
    <header class="settings-hero">
      <div class="hero-copy"><div class="eyebrow"><span class="eyebrow-icon"><Palette :size="14" /></span><span>系统配置</span><ChevronRight :size="13" /><b>站点设置</b></div><h1>站点设置</h1><p>统一管理品牌、访问规则、内容分类和数据大屏。这里的修改只影响当前站点。</p></div>
      <div class="hero-state"><span class="status-pill" :class="form.allowRegister ? 'on' : 'off'"><span class="status-pill-dot"></span>{{ form.allowRegister ? '开放注册' : '注册已关闭' }}</span><span class="hero-state-label">{{ host }}</span></div>
    </header>

    <div class="summary-grid">
      <div class="summary-item summary-primary"><span class="summary-icon"><Palette :size="18" /></span><div><span class="summary-label">当前站点</span><strong>{{ form.title || '未命名站点' }}</strong><small>{{ form.slogan || '尚未填写副标题' }}</small></div></div>
      <div class="summary-item"><span class="summary-icon blue"><Globe2 :size="18" /></span><div><span class="summary-label">访问规则</span><strong>{{ form.allowRegister ? '开放注册' : '邀请制' }}</strong><small>{{ form.contactEmail || '未填写联系邮箱' }}</small></div></div>
      <div class="summary-item"><span class="summary-icon green"><LayoutList :size="18" /></span><div><span class="summary-label">内容分类</span><strong>{{ form.projectCategories.length + form.equipmentCategories.length }} 个</strong><small>项目 {{ form.projectCategories.length }} · 设备 {{ form.equipmentCategories.length }}</small></div></div>
      <div class="summary-item"><span class="summary-icon cyan"><MonitorPlay :size="18" /></span><div><span class="summary-label">数据大屏</span><strong>{{ screen.demo ? '演示模式' : '真实数据' }}</strong><small>{{ screen.title || '使用默认标题' }}</small></div></div>
    </div>

    <div class="settings-shell">
      <aside class="settings-nav">
        <div class="nav-caption">配置目录</div>
        <button v-for="item in sectionItems" :key="item.key" type="button" class="settings-nav-item" :class="{ active: activeSection === item.key }" @click="activeSection = item.key"><span class="nav-item-icon" :class="item.tone"><component :is="item.icon" :size="17" /></span><span class="nav-item-copy"><b>{{ item.title }}</b><small>{{ item.desc }}</small></span><ChevronRight :size="15" class="nav-chevron" /></button>
        <div class="nav-help"><span class="nav-help-icon"><Info :size="15" /></span><div><b>当前站点</b><p>品牌、分类与大屏配置会立即同步到学生端。</p></div></div>
      </aside>

      <main class="settings-content">
        <template v-if="activeSection === 'brand'">
          <div class="section-heading"><div><span class="section-kicker">01 / 品牌与外观</span><h2>让站点保持统一形象</h2><p>标题和 LOGO 会出现在浏览器标签、登录页和学生端顶部，右侧可以实时预览。</p></div></div>
          <div class="brand-workspace">
            <div class="card block-card">
              <div class="block-head"><div><h3>品牌信息</h3><p class="sub">建议上传正方形透明底 LOGO，未上传时使用默认图标。</p></div><span class="soft-badge"><Palette :size="13" /> 即时预览</span></div>
              <div class="brand-grid"><div class="field"><label>站点 LOGO</label><ImageUploader v-model="form.logoUrl" class="logo-uploader" /><div class="hint">推荐 PNG 或 SVG，建议尺寸 256 × 256。</div></div><div class="brand-fields"><div class="field"><label>站点标题 <b>*</b></label><el-input v-model="form.title" maxlength="40" show-word-limit placeholder="AI未来实践中心" size="large" /></div><div class="field"><label>副标题</label><el-input v-model="form.slogan" maxlength="40" show-word-limit placeholder="项目驱动教学实验平台" /><div class="hint">显示在标题下方的一行小字。</div></div><div class="field"><label>底部信息</label><el-input v-model="form.footerText" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="如：© 2026 AI未来实践中心 · 电子信息创新实验室" /><div class="hint">显示在登录页与学生端底部，留空则不显示。</div></div></div></div>
            </div>
            <div class="card preview-card"><div class="preview-head"><div><span class="section-kicker">LIVE PREVIEW</span><h3>页面预览</h3></div><Eye :size="17" color="#2563eb" /></div><div class="pv-label">学生端顶部</div><div class="pv-topbar"><span class="pv-logo"><img v-if="form.logoUrl" :src="form.logoUrl" alt="" /><GraduationCap v-else :size="20" color="#fff" /></span><div><div class="pv-title">{{ form.title || '站点标题' }}</div><div class="pv-sub">{{ form.slogan || '副标题' }}</div></div></div><div class="pv-label">浏览器标签</div><div class="pv-tab"><span class="pv-favicon"><img v-if="form.logoUrl" :src="form.logoUrl" alt="" /><span v-else class="pv-fav-dot"></span></span><span class="pv-tab-text">{{ form.title || '站点标题' }}</span></div><div class="pv-label">登录页底部</div><div class="pv-footer">{{ form.footerText || '未填写底部信息，不显示' }}</div></div>
          </div>
        </template>

        <template v-else-if="activeSection === 'access'">
          <div class="section-heading"><div><span class="section-kicker">02 / 访问与注册</span><h2>控制用户如何进入站点</h2><p>开放注册适合内部公开使用；关闭后，账号由管理员创建或批量导入。</p></div></div>
          <div class="card block-card"><div class="toggle-row" :class="{ on: form.allowRegister }"><div class="toggle-icon"><UserPlus :size="20" /></div><div class="toggle-text"><div class="toggle-title">开放注册</div><div class="toggle-desc">{{ form.allowRegister ? '登录页显示注册入口，学生可以自行创建账号。' : '登录页隐藏注册入口，后端同步拒绝注册请求。' }}</div></div><el-switch v-model="form.allowRegister" size="large" inline-prompt active-text="开" inactive-text="关" /></div><div class="form-grid access-fields"><div class="field"><label>管理员联系邮箱</label><el-input v-model="form.contactEmail" placeholder="如 lab@school.edu.cn" /><div class="hint">显示在帮助支持与忘记密码提示中。</div></div><div class="field"><label>服务时间</label><el-input v-model="form.serviceHours" maxlength="60" placeholder="如 工作日 9:00–17:30" /><div class="hint">实验室值班和设备领还时间。</div></div></div></div><div class="callout"><span class="callout-icon"><ShieldCheck :size="16" /></span><div><b>建议</b><p>如果站点面向校内班级使用，可以关闭注册并由管理员统一导入账号，减少重复账号。</p></div></div>
        </template>

        <template v-else-if="activeSection === 'content'">
          <div class="section-heading"><div><span class="section-kicker">03 / 内容与分类</span><h2>整理学生端内容入口</h2><p>设置列表分页大小，以及项目和设备编辑表单中的分类选项。</p></div></div>
          <div class="card block-card"><div class="block-head"><div><h3>列表显示</h3><p class="sub">分页数量越大，单页信息越多；建议根据学生端设备和屏幕尺寸调整。</p></div><span class="soft-badge green"><LayoutList :size="13" /> 内容管理</span></div><div class="form-grid page-size-grid"><div class="field"><label>项目每页数量</label><el-input-number v-model="form.projectPageSize" :min="3" :max="50" style="width:100%" /><div class="hint">学生端“项目中心”的默认分页大小。</div></div><div class="field"><label>设备每页数量</label><el-input-number v-model="form.equipmentPageSize" :min="3" :max="50" style="width:100%" /><div class="hint">学生端“设备图书馆”的默认分页大小。</div></div></div></div>
          <div class="card block-card"><div class="block-head"><div><h3>分类选项</h3><p class="sub">分类会出现在项目和设备编辑表单中，学生端筛选会按实际数据展示。</p></div></div><div class="category-stack"><div class="field"><label>项目分类 <span class="count">{{ form.projectCategories.length }} 个</span></label><div class="tag-editor"><el-tag v-for="(c, i) in form.projectCategories" :key="c" closable size="large" @close="form.projectCategories.splice(i, 1)">{{ c }}</el-tag><el-input v-model="newProjectCat" size="default" class="tag-input" placeholder="输入后回车添加" @keyup.enter="addCat('projectCategories', 'newProjectCat')" /></div></div><div class="field"><label>设备分类 <span class="count">{{ form.equipmentCategories.length }} 个</span></label><div class="tag-editor"><el-tag v-for="(c, i) in form.equipmentCategories" :key="c" closable size="large" type="success" @close="form.equipmentCategories.splice(i, 1)">{{ c }}</el-tag><el-input v-model="newEquipCat" size="default" class="tag-input" placeholder="输入后回车添加" @keyup.enter="addCat('equipmentCategories', 'newEquipCat')" /></div></div></div></div>
        </template>

        <template v-else>
          <div class="section-heading"><div><span class="section-kicker">04 / 数据大屏</span><h2>配置教学数据展示</h2><p>设置大屏抬头、学期 KPI 目标和演示数据开关，目标为 0 时不显示对应指标。</p></div><a href="/admin/screen" target="_blank" class="open-screen"><MonitorPlay :size="14" /> 打开大屏</a></div>
          <div class="card block-card"><div class="block-head"><div><h3>大屏标题</h3><p class="sub">在大屏顶部显示站点名称和英文副标题。</p></div><span class="soft-badge blue"><MonitorPlay :size="13" /> 实时数据</span></div><div class="form-grid"><div class="field"><label>大屏标题</label><el-input v-model="screen.title" maxlength="40" placeholder="留空则使用站点标题" /></div><div class="field"><label>英文副标题</label><el-input v-model="screen.subtitle" maxlength="60" placeholder="REAL-TIME TEACHING DATA CENTER" /></div></div></div>
          <div class="card block-card"><div class="block-head"><div><h3>学期目标</h3><p class="sub">用于大屏展示完成度，目标为 0 表示隐藏该项。</p></div></div><div class="form-grid"><div class="field"><label>参与学生数</label><el-input-number v-model="screen.targetStudents" :min="0" :max="100000" style="width:100%" /></div><div class="field"><label>完成项目数</label><el-input-number v-model="screen.targetCompleted" :min="0" :max="100000" style="width:100%" /></div><div class="field"><label>设备利用率 %</label><el-input-number v-model="screen.targetUtilization" :min="0" :max="100" style="width:100%" /></div><div class="field"><label>周活跃率 %</label><el-input-number v-model="screen.targetActiveRate" :min="0" :max="100" style="width:100%" /></div></div></div>
          <div class="card block-card"><div class="toggle-row" :class="{ on: screen.demo }"><div class="toggle-icon"><MonitorPlay :size="20" /></div><div class="toggle-text"><div class="toggle-title">演示数据</div><div class="toggle-desc">新站点没有学生时显示样例数据，正式启用后请关闭，关闭即刻恢复真实数据。</div></div><el-switch v-model="screen.demo" /></div></div>
        </template>

        <div class="action-dock"><div class="action-dock-copy"><span class="action-dot"></span><div><b>配置修改后立即生效</b><small>保存后学生端、登录页和数据大屏会同步更新</small></div></div><div class="actions-left"><el-button size="large" @click="load">还原已保存</el-button><el-button size="large" type="primary" :loading="saving" @click="save"><Save :size="15" />保存配置</el-button></div></div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ChevronRight, Eye, Globe2, GraduationCap, Info, LayoutList, MonitorPlay, Palette, Save, ShieldCheck, UserPlus } from 'lucide-vue-next'
import { adminGetSiteSettings, adminScreenSettings, adminUpdateScreenSettings, adminUpdateSiteSettings } from '../../api'
import { loadSiteConfig } from '../../utils/siteConfig'
import ImageUploader from '../../components/ImageUploader.vue'

const saving = ref(false)
const newProjectCat = ref('')
const newEquipCat = ref('')
const host = window.location.host
const activeSection = ref('brand')
const sectionItems = [
  { key: 'brand', title: '品牌与外观', desc: '标题、LOGO 与预览', icon: Palette, tone: 'purple' },
  { key: 'access', title: '访问与注册', desc: '注册入口与联系信息', icon: ShieldCheck, tone: 'blue' },
  { key: 'content', title: '内容与分类', desc: '分页大小与分类选项', icon: LayoutList, tone: 'green' },
  { key: 'screen', title: '数据大屏', desc: '目标与演示数据', icon: MonitorPlay, tone: 'cyan' }
]

const form = reactive({
  title: '', slogan: '', logoUrl: '', footerText: '', contactEmail: '', serviceHours: '',
  allowRegister: true, projectPageSize: 9, equipmentPageSize: 9, projectCategories: [], equipmentCategories: []
})

const refs = { newProjectCat, newEquipCat }

const addCat = (listKey, inputKey) => {
  const v = refs[inputKey].value.trim()
  if (!v) return
  if (form[listKey].includes(v)) { ElMessage.warning('该分类已存在'); return }
  form[listKey].push(v)
  refs[inputKey].value = ''
}

const load = async () => {
  const d = await adminGetSiteSettings()
  Object.assign(form, d)
}

const save = async () => {
  if (!form.title.trim()) { ElMessage.warning('请填写站点标题'); return }
  if (!form.projectCategories.length || !form.equipmentCategories.length) { ElMessage.warning('分类至少保留一项'); return }
  saving.value = true
  try {
    await adminUpdateSiteSettings({ ...form })
    await adminUpdateScreenSettings({ ...screen })
    await loadSiteConfig(true)
    ElMessage.success('已保存,立即生效')
  } finally {
    saving.value = false
  }
}

const screen = reactive({ title: '', subtitle: '', targetStudents: 0, targetCompleted: 0, targetUtilization: 0, targetActiveRate: 0, demo: false })
const loadScreen = async () => { Object.assign(screen, await adminScreenSettings()) }

onMounted(() => { load(); loadScreen() })
</script>

<style scoped>
.layout { display: grid; grid-template-columns: minmax(0, 1fr) 340px; gap: 16px; align-items: start; }
@media (max-width: 1100px) { .layout { grid-template-columns: 1fr; } }
.main, .side { display: flex; flex-direction: column; gap: 16px; }
.muted { font-size: 12px; color: #9ca3af; }

.card-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 18px; }
.open-screen { font-size: 13px; color: #0e7490; text-decoration: none; white-space: nowrap; background: #ecfeff; padding: 6px 12px; border-radius: 8px; }
.open-screen:hover { background: #cffafe; }
.card-head h3 { margin: 0 0 4px; font-size: 16px; display: flex; align-items: center; gap: 8px; }
.sub { color: var(--text-secondary); font-size: 13px; margin: 0; line-height: 1.6; }

.field { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.field label { font-size: 13px; font-weight: 600; color: #374151; display: flex; align-items: center; gap: 6px; }
.field label b { color: #dc2626; }
.field label .count { margin-left: auto; font-size: 12px; color: #9ca3af; font-weight: 500; }
.hint { font-size: 12px; color: #9ca3af; line-height: 1.5; }

.brand-grid { display: grid; grid-template-columns: 220px minmax(0, 1fr); gap: 24px; }
@media (max-width: 900px) { .brand-grid { grid-template-columns: 1fr; } }
.brand-fields { display: flex; flex-direction: column; gap: 16px; }
.logo-uploader :deep(.preview img) { height: 160px; object-fit: contain; background: #f9fafb; }

.toggle-row { display: flex; align-items: center; gap: 14px; padding: 16px 18px; border-radius: 14px; background: #f9fafb; border: 1px solid var(--border); }
.toggle-row.on { background: #f0fdf4; border-color: #bbf7d0; }
.toggle-icon { width: 42px; height: 42px; border-radius: 12px; background: #fff; display: grid; place-items: center; color: #6b7280; flex-shrink: 0; box-shadow: var(--shadow-card); }
.toggle-row.on .toggle-icon { color: #16a34a; }
.toggle-text { flex: 1; }
.toggle-title { font-weight: 700; font-size: 14px; }
.toggle-desc { font-size: 12px; color: #6b7280; margin-top: 3px; line-height: 1.6; }

.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 18px 20px; }
@media (max-width: 900px) { .form-grid { grid-template-columns: 1fr; } }
.span-2 { grid-column: 1 / -1; }
:deep(.el-input-number .el-input__inner) { text-align: left; }
.tag-editor { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; padding: 10px 12px; border: 1px dashed var(--border); border-radius: 12px; min-height: 52px; }
.tag-input { width: 180px; }
.tag-input :deep(.el-input__wrapper) { box-shadow: none; background: #f3f4f6; }

.actions { display: flex; align-items: center; gap: 10px; padding: 16px 24px; flex-wrap: wrap; }
.actions .muted { margin-left: auto; }

.side h4 { margin: 0 0 12px; font-size: 14px; }
.pv-label { font-size: 11px; color: #9ca3af; letter-spacing: .06em; text-transform: uppercase; margin: 14px 0 6px; }
.pv-label:first-of-type { margin-top: 0; }
.pv-topbar { display: flex; align-items: center; gap: 12px; padding: 12px 14px; border: 1px solid var(--border); border-radius: 12px; background: #fff; }
.pv-logo { width: 40px; height: 40px; border-radius: 10px; overflow: hidden; background: linear-gradient(135deg, #3b82f6, #9333ea); display: grid; place-items: center; flex-shrink: 0; }
.pv-logo img { width: 100%; height: 100%; object-fit: cover; }
.pv-title { font-weight: 700; font-size: 15px; color: #111827; }
.pv-sub { font-size: 11px; color: #6b7280; }
.pv-tab { display: inline-flex; align-items: center; gap: 8px; padding: 8px 14px 8px 10px; background: #f3f4f6; border-radius: 10px 10px 0 0; border: 1px solid var(--border); border-bottom: none; font-size: 12px; color: #374151; max-width: 100%; }
.pv-favicon { width: 16px; height: 16px; display: grid; place-items: center; }
.pv-favicon img { width: 16px; height: 16px; object-fit: cover; border-radius: 3px; }
.pv-fav-dot { width: 12px; height: 12px; border-radius: 3px; background: linear-gradient(135deg, #3b82f6, #9333ea); }
.pv-tab-text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pv-footer { font-size: 12px; color: #9ca3af; text-align: center; padding: 12px; border-top: 1px dashed var(--border); white-space: pre-wrap; line-height: 1.6; }

.kv { display: grid; grid-template-columns: 64px 1fr; gap: 8px 10px; margin: 0; font-size: 13px; }
.kv dt { color: #9ca3af; }
.kv dd { margin: 0; color: #111827; min-width: 0; word-break: break-all; }
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 12px; }

/* 分区式设置页 */
.settings-page { max-width: 1320px; margin: 0 auto; color: #111827; }
.settings-hero { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; padding: 4px 2px 22px; }
.hero-copy { min-width: 0; }.eyebrow { display: flex; align-items: center; gap: 7px; color: #7c3aed; font-size: 12px; font-weight: 700; letter-spacing: .03em; }.eyebrow-icon { display: grid; place-items: center; width: 24px; height: 24px; border-radius: 8px; color: #fff; background: linear-gradient(135deg, #6366f1, #a855f7); }.eyebrow b { color: #374151; }
.settings-hero h1 { margin: 10px 0 5px; font-size: 28px; letter-spacing: -.03em; }.settings-hero p { margin: 0; color: #6b7280; font-size: 13px; line-height: 1.6; }.hero-state { display: flex; align-items: center; gap: 12px; flex-shrink: 0; padding-bottom: 2px; }.status-pill { display: inline-flex; align-items: center; gap: 8px; padding: 8px 12px; border-radius: 999px; background: #ecfdf5; color: #047857; font-size: 12px; font-weight: 700; }.status-pill.off { background: #f3f4f6; color: #6b7280; }.status-pill-dot { width: 7px; height: 7px; border-radius: 50%; background: currentColor; box-shadow: 0 0 0 4px color-mix(in srgb, currentColor 14%, transparent); }.hero-state-label { color: #9ca3af; font-size: 12px; }
.summary-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-bottom: 18px; }.summary-item { display: flex; align-items: center; gap: 11px; min-width: 0; padding: 14px 15px; border: 1px solid #e8eaf0; border-radius: 14px; background: rgba(255,255,255,.84); box-shadow: 0 5px 16px rgba(15,23,42,.035); }.summary-icon { display: grid; place-items: center; width: 36px; height: 36px; flex-shrink: 0; border-radius: 11px; color: #9333ea; background: #f5f3ff; }.summary-icon.blue { color: #2563eb; background: #eff6ff; }.summary-icon.green { color: #059669; background: #ecfdf5; }.summary-icon.cyan { color: #0e7490; background: #ecfeff; }.summary-item div { min-width: 0; }.summary-label, .summary-item small { display: block; color: #9ca3af; font-size: 11px; }.summary-item strong { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin: 2px 0; font-size: 15px; }.summary-item small { color: #6b7280; font-size: 11px; }
.settings-shell { display: grid; grid-template-columns: 224px minmax(0, 1fr); gap: 18px; align-items: start; }.settings-nav { position: sticky; top: 0; display: flex; flex-direction: column; gap: 5px; padding: 12px 8px 10px; border: 1px solid #e8eaf0; border-radius: 17px; background: rgba(255,255,255,.76); box-shadow: 0 5px 18px rgba(15,23,42,.035); }.nav-caption { padding: 2px 10px 8px; color: #9ca3af; font-size: 10px; font-weight: 700; letter-spacing: .14em; }.settings-nav-item { display: flex; align-items: center; width: 100%; gap: 10px; padding: 10px; border: 0; border-radius: 12px; background: transparent; color: #4b5563; text-align: left; cursor: pointer; transition: background .16s, color .16s, transform .16s; }.settings-nav-item:hover { background: #f8fafc; transform: translateX(2px); }.settings-nav-item.active { color: #1e40af; background: #eff6ff; box-shadow: inset 3px 0 #2563eb; }.nav-item-icon { display: grid; place-items: center; width: 32px; height: 32px; flex-shrink: 0; border-radius: 10px; color: #2563eb; background: #dbeafe; }.nav-item-icon.purple { color: #7c3aed; background: #ede9fe; }.nav-item-icon.green { color: #059669; background: #d1fae5; }.nav-item-icon.cyan { color: #0e7490; background: #cffafe; }.nav-item-copy { display: grid; gap: 2px; min-width: 0; flex: 1; }.nav-item-copy b { font-size: 13px; }.nav-item-copy small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #9ca3af; font-size: 11px; }.settings-nav-item.active .nav-item-copy small { color: #60a5fa; }.nav-chevron { color: #cbd5e1; }.settings-nav-item.active .nav-chevron { color: #60a5fa; }.nav-help { display: flex; gap: 8px; margin: 14px 4px 0; padding: 11px 10px; border-top: 1px solid #eef0f4; color: #6b7280; }.nav-help-icon { display: grid; place-items: center; color: #2563eb; }.nav-help b { color: #374151; font-size: 11px; }.nav-help p { margin: 4px 0 0; font-size: 11px; line-height: 1.5; }
.settings-content { min-width: 0; }.section-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; min-height: 67px; margin: 2px 2px 13px; }.section-kicker { display: block; margin-bottom: 5px; color: #2563eb; font-size: 11px; font-weight: 800; letter-spacing: .1em; text-transform: uppercase; }.section-heading h2 { margin: 0 0 5px; font-size: 19px; letter-spacing: -.02em; }.section-heading p { margin: 0; color: #6b7280; font-size: 13px; line-height: 1.55; }.open-screen { display: inline-flex; align-items: center; gap: 6px; margin-top: 9px; padding: 8px 11px; border-radius: 9px; background: #ecfeff; color: #0e7490; font-size: 12px; white-space: nowrap; }.open-screen:hover { background: #cffafe; }
.block-card, .preview-card { border: 1px solid #e8eaf0; box-shadow: 0 5px 18px rgba(15,23,42,.035); }.block-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 14px; margin-bottom: 18px; }.block-head h3 { margin: 0 0 4px; font-size: 15px; }.soft-badge { display: inline-flex; align-items: center; gap: 5px; padding: 5px 9px; border-radius: 999px; color: #7c3aed; background: #f5f3ff; font-size: 11px; font-weight: 700; white-space: nowrap; }.soft-badge.blue { color: #2563eb; background: #eff6ff; }.soft-badge.green { color: #059669; background: #ecfdf5; }
.brand-workspace { display: grid; grid-template-columns: minmax(0, 1fr) 280px; gap: 14px; align-items: start; }.brand-grid { gap: 22px; }.logo-uploader :deep(.preview img) { height: 150px; }.preview-card { padding: 20px; }.preview-head { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 17px; }.preview-head h3 { margin: 0; font-size: 15px; }.preview-head .section-kicker { margin-bottom: 3px; color: #60a5fa; font-size: 9px; }.pv-label { margin-top: 14px; }.pv-label:first-of-type { margin-top: 0; }
.form-grid { gap: 20px 18px; }.field label { color: #374151; }.hint { color: #9ca3af; }.access-fields { margin-top: 20px; }.count { margin-left: auto; color: #9ca3af; font-size: 12px; font-weight: 500; }.category-stack { display: grid; gap: 20px; }.page-size-grid { max-width: 680px; }.tag-editor { border-color: #dfe3eb; background: #fafbfc; }.tag-input :deep(.el-input__wrapper) { background: #fff; }.toggle-row { border-color: #e5e7eb; }.toggle-row.on { border-color: #bbf7d0; background: #f0fdf4; }
.callout { display: flex; gap: 10px; align-items: flex-start; margin-top: 12px; padding: 12px 14px; border: 1px solid #dbeafe; border-radius: 13px; background: #f8fbff; color: #475569; }.callout-icon { display: grid; place-items: center; width: 26px; height: 26px; flex-shrink: 0; border-radius: 8px; color: #2563eb; background: #dbeafe; }.callout b { color: #1e3a8a; font-size: 12px; }.callout p { margin: 3px 0 0; font-size: 12px; line-height: 1.6; }
.action-dock { position: sticky; bottom: 10px; z-index: 5; display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-top: 18px; padding: 12px 14px; border: 1px solid #dbe3f0; border-radius: 15px; background: rgba(255,255,255,.94); box-shadow: 0 12px 28px rgba(15,23,42,.10); backdrop-filter: blur(12px); }.action-dock-copy { display: flex; align-items: center; gap: 9px; }.action-dot { width: 8px; height: 8px; border-radius: 50%; background: #22c55e; box-shadow: 0 0 0 4px #dcfce7; }.action-dock-copy b, .action-dock-copy small { display: block; }.action-dock-copy b { font-size: 12px; }.action-dock-copy small { margin-top: 2px; color: #9ca3af; font-size: 11px; }.actions-left { display: flex; gap: 8px; }.actions-left .el-button { display: inline-flex; align-items: center; gap: 6px; }
@media (max-width: 1100px) { .summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }.settings-shell { grid-template-columns: 190px minmax(0, 1fr); }.brand-workspace { grid-template-columns: 1fr; }.preview-card { order: -1; } }
@media (max-width: 760px) { .settings-hero { align-items: flex-start; flex-direction: column; }.hero-state { padding: 0; }.settings-shell { grid-template-columns: 1fr; }.settings-nav { position: static; display: grid; grid-template-columns: 1fr 1fr; padding: 10px; }.nav-caption, .nav-help { grid-column: 1 / -1; }.nav-help { margin-top: 6px; }.section-heading { min-height: 0; }.action-dock { align-items: flex-start; flex-direction: column; }.actions-left { width: 100%; }.actions-left .el-button { flex: 1; } }
@media (max-width: 520px) { .summary-grid { grid-template-columns: 1fr; }.settings-nav { grid-template-columns: 1fr; }.nav-caption, .nav-help { grid-column: auto; }.form-grid { grid-template-columns: 1fr; }.span-2 { grid-column: auto; }.preview-card { padding: 16px; } }
</style>
