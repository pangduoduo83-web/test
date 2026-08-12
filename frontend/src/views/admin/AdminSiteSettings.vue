<template>
  <div class="card site-settings">
    <div class="head">
      <div>
        <h3>站点设置</h3>
        <p class="sub">标题、LOGO、底部信息、注册开关、列表每页数量与分类,保存后立即生效。</p>
      </div>
    </div>

    <el-form label-width="130px" class="form">
      <el-form-item label="站点标题">
        <el-input v-model="form.title" maxlength="40" show-word-limit style="max-width:420px"
                  placeholder="AI未来实践中心" />
        <div class="hint">显示在浏览器标题、登录页与顶部品牌区</div>
      </el-form-item>

      <el-form-item label="站点 LOGO">
        <div class="logo-row">
          <ImageUploader v-model="form.logoUrl" class="logo-uploader" />
        </div>
        <div class="hint">建议正方形图片;不上传则显示默认首字标</div>
      </el-form-item>

      <el-form-item label="底部信息">
        <el-input v-model="form.footerText" type="textarea" :rows="2" maxlength="200" show-word-limit
                  placeholder="如: © 2026 AI未来实践中心 · 电子信息创新实验室 · 沪ICP备xxxxxx号" />
        <div class="hint">显示在登录页与学生端页面底部,留空则不显示</div>
      </el-form-item>

      <el-form-item label="开放注册">
        <el-switch v-model="form.allowRegister" active-text="开启" inactive-text="关闭" />
        <span class="inline-hint">关闭后登录页隐藏注册入口,后端同步拒绝注册请求;账号由管理员在用户管理中创建</span>
      </el-form-item>

      <el-form-item label="项目每页数量">
        <el-input-number v-model="form.projectPageSize" :min="3" :max="50" />
        <span class="inline-hint">学生端项目中心列表的默认分页大小</span>
      </el-form-item>

      <el-form-item label="设备每页数量">
        <el-input-number v-model="form.equipmentPageSize" :min="3" :max="50" />
        <span class="inline-hint">学生端设备图书馆列表的默认分页大小</span>
      </el-form-item>

      <el-form-item label="项目分类">
        <div class="tag-editor">
          <el-tag v-for="(c, i) in form.projectCategories" :key="c" closable
                  @close="form.projectCategories.splice(i, 1)">{{ c }}</el-tag>
          <el-input v-model="newProjectCat" size="small" style="width:140px" placeholder="+ 新分类,回车添加"
                    @keyup.enter="addCat('projectCategories', 'newProjectCat')" />
        </div>
        <div class="hint">项目编辑表单的分类下拉选项(学生端筛选按实际项目数据展示)</div>
      </el-form-item>

      <el-form-item label="设备分类">
        <div class="tag-editor">
          <el-tag v-for="(c, i) in form.equipmentCategories" :key="c" closable type="success"
                  @close="form.equipmentCategories.splice(i, 1)">{{ c }}</el-tag>
          <el-input v-model="newEquipCat" size="small" style="width:140px" placeholder="+ 新分类,回车添加"
                    @keyup.enter="addCat('equipmentCategories', 'newEquipCat')" />
        </div>
        <div class="hint">设备编辑表单的分类下拉选项</div>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminGetSiteSettings, adminUpdateSiteSettings } from '../../api'
import { loadSiteConfig } from '../../utils/siteConfig'
import ImageUploader from '../../components/ImageUploader.vue'

const saving = ref(false)
const newProjectCat = ref('')
const newEquipCat = ref('')

const form = reactive({
  title: '',
  logoUrl: '',
  footerText: '',
  allowRegister: true,
  projectPageSize: 9,
  equipmentPageSize: 9,
  projectCategories: [],
  equipmentCategories: []
})

const refs = { newProjectCat, newEquipCat }

const addCat = (listKey, inputKey) => {
  const v = refs[inputKey].value.trim()
  if (!v) return
  if (form[listKey].includes(v)) {
    ElMessage.warning('该分类已存在')
    return
  }
  form[listKey].push(v)
  refs[inputKey].value = ''
}

const load = async () => {
  const d = await adminGetSiteSettings()
  Object.assign(form, d)
}

const save = async () => {
  if (!form.title.trim()) {
    ElMessage.warning('请填写站点标题')
    return
  }
  if (!form.projectCategories.length || !form.equipmentCategories.length) {
    ElMessage.warning('分类至少保留一项')
    return
  }
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
.site-settings { max-width: 860px; }
.head h3 { margin: 0 0 4px; }
.sub { color: var(--text-secondary); font-size: 13px; margin: 0 0 16px; }
.hint { font-size: 12px; color: #9ca3af; width: 100%; line-height: 1.7; }
.inline-hint { font-size: 12px; color: #9ca3af; margin-left: 12px; }
.logo-row { width: 220px; }
.tag-editor { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
</style>
