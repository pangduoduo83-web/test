<template>
  <div class="site-settings">
    <div class="layout">
      <!-- 主栏 -->
      <div class="main">
        <div class="card">
          <div class="card-head">
            <div>
              <h3><Palette :size="17" color="#9333ea" /> 品牌与外观</h3>
              <p class="sub">标题和 LOGO 出现在浏览器标签、登录页和学生端顶部;右侧可实时预览。</p>
            </div>
          </div>
          <div class="brand-grid">
            <div class="field">
              <label>站点 LOGO</label>
              <ImageUploader v-model="form.logoUrl" class="logo-uploader" />
              <div class="hint">建议正方形、透明底 PNG;不上传则显示默认图标</div>
            </div>
            <div class="brand-fields">
              <div class="field">
                <label>站点标题 <b>*</b></label>
                <el-input v-model="form.title" maxlength="40" show-word-limit placeholder="AI未来实践中心" size="large" />
              </div>
              <div class="field">
                <label>副标题</label>
                <el-input v-model="form.slogan" maxlength="40" show-word-limit placeholder="项目驱动教学实验平台" />
                <div class="hint">显示在标题下方的一行小字(登录页与学生端顶部)</div>
              </div>
              <div class="field">
                <label>底部信息</label>
                <el-input v-model="form.footerText" type="textarea" :rows="3" maxlength="200" show-word-limit
                          placeholder="如: © 2026 AI未来实践中心 · 电子信息创新实验室 · 沪ICP备xxxxxx号" />
                <div class="hint">显示在登录页与学生端页面底部,留空则不显示</div>
              </div>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-head">
            <div>
              <h3><ShieldCheck :size="17" color="#2563eb" /> 访问与注册</h3>
              <p class="sub">控制谁能自己注册进来。</p>
            </div>
          </div>
          <div class="toggle-row" :class="{ on: form.allowRegister }">
            <div class="toggle-icon"><UserPlus :size="20" /></div>
            <div class="toggle-text">
              <div class="toggle-title">开放注册</div>
              <div class="toggle-desc">{{ form.allowRegister ? '登录页显示注册入口,学生可自行注册账号。' : '登录页隐藏注册入口,后端同步拒绝注册请求;账号由管理员在「用户管理」创建或批量导入。' }}</div>
            </div>
            <el-switch v-model="form.allowRegister" size="large" inline-prompt active-text="开" inactive-text="关" />
          </div>
          <div class="form-grid" style="margin-top:18px">
            <div class="field">
              <label>管理员联系邮箱</label>
              <el-input v-model="form.contactEmail" placeholder="如 lab@school.edu.cn" />
              <div class="hint">显示在学生端「帮助支持」与登录页「忘记密码」提示里,留空则只提示到管理处咨询</div>
            </div>
            <div class="field">
              <label>服务时间</label>
              <el-input v-model="form.serviceHours" maxlength="60" placeholder="如 工作日 9:00-17:30" />
              <div class="hint">实验室值班 / 设备领还时间,同样显示在帮助支持里</div>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-head">
            <div>
              <h3><LayoutList :size="17" color="#16a34a" /> 列表与分类</h3>
              <p class="sub">学生端列表的分页大小,以及项目 / 设备编辑表单里的分类选项。</p>
            </div>
          </div>
          <div class="form-grid">
            <div class="field">
              <label>项目每页数量</label>
              <el-input-number v-model="form.projectPageSize" :min="3" :max="50" style="width:100%" />
              <div class="hint">学生端「项目中心」默认分页大小</div>
            </div>
            <div class="field">
              <label>设备每页数量</label>
              <el-input-number v-model="form.equipmentPageSize" :min="3" :max="50" style="width:100%" />
              <div class="hint">学生端「设备图书馆」默认分页大小</div>
            </div>
            <div class="field span-2">
              <label>项目分类 <span class="count">{{ form.projectCategories.length }} 个</span></label>
              <div class="tag-editor">
                <el-tag v-for="(c, i) in form.projectCategories" :key="c" closable size="large" @close="form.projectCategories.splice(i, 1)">{{ c }}</el-tag>
                <el-input v-model="newProjectCat" size="default" class="tag-input" placeholder="+ 新分类,回车添加" @keyup.enter="addCat('projectCategories', 'newProjectCat')" />
              </div>
              <div class="hint">项目编辑表单的分类下拉选项;学生端筛选按实际项目数据展示</div>
            </div>
            <div class="field span-2">
              <label>设备分类 <span class="count">{{ form.equipmentCategories.length }} 个</span></label>
              <div class="tag-editor">
                <el-tag v-for="(c, i) in form.equipmentCategories" :key="c" closable size="large" type="success" @close="form.equipmentCategories.splice(i, 1)">{{ c }}</el-tag>
                <el-input v-model="newEquipCat" size="default" class="tag-input" placeholder="+ 新分类,回车添加" @keyup.enter="addCat('equipmentCategories', 'newEquipCat')" />
              </div>
              <div class="hint">设备编辑表单的分类下拉选项</div>
            </div>
          </div>
        </div>

        <div class="actions card">
          <el-button type="primary" size="large" :loading="saving" @click="save"><Save :size="15" style="margin-right:6px" />保存配置</el-button>
          <el-button size="large" @click="load">还原为已保存</el-button>
          <span class="muted">保存后学生端、登录页立即生效,无需重启</span>
        </div>
      </div>

      <!-- 侧栏:预览 -->
      <aside class="side">
        <div class="card preview-card">
          <h4>预览</h4>
          <div class="pv-label">学生端顶部</div>
          <div class="pv-topbar">
            <span class="pv-logo">
              <img v-if="form.logoUrl" :src="form.logoUrl" alt="" />
              <GraduationCap v-else :size="20" color="#fff" />
            </span>
            <div>
              <div class="pv-title">{{ form.title || '站点标题' }}</div>
              <div class="pv-sub">{{ form.slogan || '副标题' }}</div>
            </div>
          </div>
          <div class="pv-label">浏览器标签</div>
          <div class="pv-tab">
            <span class="pv-favicon"><img v-if="form.logoUrl" :src="form.logoUrl" alt="" /><span v-else class="pv-fav-dot"></span></span>
            <span class="pv-tab-text">{{ form.title || '站点标题' }}</span>
          </div>
          <div class="pv-label">登录页底部</div>
          <div class="pv-footer">{{ form.footerText || '(未填写底部信息,不显示)' }}</div>
        </div>

        <div class="card">
          <h4>当前站点</h4>
          <dl class="kv">
            <dt>访问地址</dt><dd class="mono">{{ host }}</dd>
            <dt>注册</dt><dd><span class="badge" :class="form.allowRegister ? 'badge-green' : 'badge-gray'">{{ form.allowRegister ? '开放' : '关闭' }}</span></dd>
            <dt>分类</dt><dd>项目 {{ form.projectCategories.length }} · 设备 {{ form.equipmentCategories.length }}</dd>
          </dl>
          <div class="hint" style="margin-top:10px">这里的设置只影响本站点;其他客户站点各有自己的一套。技能维度、AI 模型、项目商店接入分别在「技能维度」「AI 设置」「项目商店 → 接入设置」里配置。</div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GraduationCap, LayoutList, Palette, Save, ShieldCheck, UserPlus } from 'lucide-vue-next'
import { adminGetSiteSettings, adminUpdateSiteSettings } from '../../api'
import { loadSiteConfig } from '../../utils/siteConfig'
import ImageUploader from '../../components/ImageUploader.vue'

const saving = ref(false)
const newProjectCat = ref('')
const newEquipCat = ref('')
const host = window.location.host

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
    await loadSiteConfig(true)
    ElMessage.success('已保存,立即生效')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.layout { display: grid; grid-template-columns: minmax(0, 1fr) 340px; gap: 16px; align-items: start; }
@media (max-width: 1100px) { .layout { grid-template-columns: 1fr; } }
.main, .side { display: flex; flex-direction: column; gap: 16px; }
.muted { font-size: 12px; color: #9ca3af; }

.card-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 18px; }
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
</style>
