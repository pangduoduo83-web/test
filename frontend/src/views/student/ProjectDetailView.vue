<template>
  <div v-if="project">
    <a class="back-link" @click="$router.push('/app/projects')"><ArrowLeft :size="14" /> 返回项目列表</a>

    <TutorPanel v-model="tutorVisible" :project-id="route.params.id" :project="project" :enrollment="detail.enrollment"
                @enroll="doEnroll" @refresh="reloadDetail" @go-submit="goSubmit" />

    <!-- 大图 Hero -->
    <div class="hero">
      <img v-if="project.coverUrl && !coverFailed" :src="project.coverUrl" :alt="project.title"
           class="hero-img" @error="coverFailed = true" />
      <div v-else class="hero-fallback"></div>
      <div class="hero-mask"></div>
      <div class="hero-content">
        <div class="hero-badges">
          <span class="cover-badge" :class="diffColor(project.difficulty)" style="position:static">{{ project.difficulty }}</span>
          <span v-if="project.verified" class="cover-badge purple" style="position:static">
            <Zap :size="12" /> 硬件已验证
          </span>
          <span v-if="project.license" class="cover-badge green" style="position:static">{{ project.license }}</span>
          <span v-if="project.category" class="cover-badge rating" style="position:static">{{ project.category }}</span>
        </div>
        <h1 class="hero-title">{{ project.title }}</h1>
        <p v-if="project.summary" class="hero-summary">{{ project.summary }}</p>
      </div>
    </div>

    <!-- 元信息操作条 -->
    <div class="card meta-bar">
      <div class="meta-left">
        <span><Clock :size="14" /> {{ project.duration }}</span>
        <span><Users :size="14" /> 团队规模: {{ project.teamSize }}</span>
        <span><Eye :size="14" /> {{ fmtNum(project.views) }} 浏览</span>
        <span><Clock :size="14" /> 更新 {{ (project.updatedAt || '').slice(0, 10) }}</span>
      </div>
      <div class="meta-actions">
        <button class="circle-btn" @click="doShare" title="复制链接"><Share2 :size="15" color="#6b7280" /></button>
        <el-button plain @click="doFavorite">
          <Heart :size="14" :fill="detail.favorited ? '#ef4444' : 'none'"
                 :color="detail.favorited ? '#ef4444' : 'currentColor'" style="margin-right:5px" />
          {{ detail.favorited ? '已收藏' : '收藏项目' }}
        </el-button>
        <button class="tutor-btn" @click="openTutor">
          <Bot :size="15" /> AI 导师
        </button>
        <button class="enroll-btn" :disabled="detail.enrolled || enrolling" @click="doEnroll">
          {{ detail.enrolled ? '✓ 已报名' : enrolling ? '处理中...' : '立即报名' }}
        </button>
      </div>
    </div>

    <!-- 统计卡:全部来自真实记录 -->
    <div class="stat-grid">
      <div class="ref-stat-card">
        <div class="ref-stat-icon" style="background:#dbeafe">
          <Users :size="22" color="#2563eb" />
        </div>
        <div>
          <div class="ref-stat-value">{{ fmtNum(project.enrolledCount) }}</div>
          <div class="ref-stat-label">参与人数</div>
        </div>
      </div>
      <div class="ref-stat-card">
        <div class="ref-stat-icon" style="background:#dcfce7">
          <Check :size="22" color="#16a34a" />
        </div>
        <div>
          <div class="ref-stat-value">{{ project.enrolledCount > 0 ? project.completionRate + '%' : '–' }}</div>
          <div class="ref-stat-label">参与者完成率</div>
        </div>
      </div>
      <div class="ref-stat-card">
        <div class="ref-stat-icon" style="background:#fce7f3">
          <Heart :size="22" color="#db2777" />
        </div>
        <div>
          <div class="ref-stat-value">{{ fmtNum(project.favoriteCount) }}</div>
          <div class="ref-stat-label">收藏数</div>
        </div>
      </div>
      <div class="ref-stat-card">
        <div class="ref-stat-icon" style="background:#fef3c7">
          <DollarSign :size="22" color="#d97706" />
        </div>
        <div>
          <div class="ref-stat-value">{{ project.cost ? '¥' + project.cost : '–' }}</div>
          <div class="ref-stat-label">预估成本</div>
        </div>
      </div>
    </div>

    <!-- 讲师信息 -->
    <div class="card mentor-bar">
      <div class="mentor-left">
        <span class="avatar big">{{ (project.mentor || '师')[0] }}</span>
        <div>
          <div class="mentor-name-row">
            <b>{{ project.mentor || '暂未指派' }}</b>
            <span v-if="project.mentor" class="badge badge-blue">指导教师</span>
          </div>
          <div class="mentor-role">{{ project.mentor ? '负责本项目的成果评审与答疑,可在「项目讨论」里提问' : '有问题可在「项目讨论」里留言,管理员会跟进' }}</div>
        </div>
      </div>
      <div class="mentor-meta">
        <span v-if="project.author"><User :size="14" /> 作者: {{ project.author }}</span>
        <span v-if="project.license"><FileText :size="14" /> 协议: {{ project.license }}</span>
        <span v-if="pcbText"><Zap :size="14" /> {{ pcbText }}</span>
      </div>
    </div>

    <!-- 我的进度(已报名时):进度是学习位置,完成由评审判定 -->
    <div v-if="detail.enrollment" class="card progress-bar-card">
      <div class="pg-head">
        <b>我的学习进度
          <span class="badge" :class="detail.enrollment.status === 'COMPLETED' ? 'badge-green' : 'badge-blue'" style="margin-left:8px">
            {{ detail.enrollment.status === 'COMPLETED' ? '已通过评审完成' : '进行中' }}
          </span>
        </b>
        <span class="pg-meta">当前任务: {{ detail.enrollment.currentTask || '-' }} · 截止: {{ detail.enrollment.deadline || '-' }}</span>
      </div>
      <el-progress :percentage="detail.enrollment.progress" :stroke-width="10" color="#3b82f6" />

      <!-- 有教学大纲:按阶段打勾,进度自动折算 -->
      <div v-if="detail.enrollment.status !== 'COMPLETED' && phases.length" class="phase-list">
        <label v-for="(ph, i) in phases" :key="i" class="phase-item" :class="{ done: donePhases.has(i + 1), next: nextPhase === i + 1 }">
          <el-checkbox :model-value="donePhases.has(i + 1)" :disabled="savingProgress" @change="(v) => togglePhase(i + 1, v)" />
          <div class="phase-text">
            <div class="phase-title"><span class="phase-no">{{ ph.phase || `第 ${i + 1} 阶段` }}</span>{{ ph.title }}<small v-if="ph.hours"> · {{ ph.hours }} 学时</small></div>
            <div v-if="nextPhase === i + 1 && ph.content" class="phase-content">{{ ph.content }}</div>
          </div>
          <span v-if="nextPhase === i + 1" class="badge badge-blue">当前阶段</span>
        </label>
        <div class="pg-edit">
          <el-button size="small" text @click="openTutor"><Bot :size="13" style="margin-right:4px" /> 让 AI 导师带我做当前阶段</el-button>
          <span class="pg-meta">全部阶段打勾后,请在下方「项目成果」提交,评审通过才算完成</span>
        </div>
      </div>

      <!-- 没有大纲:手动填百分比 -->
      <div v-else-if="detail.enrollment.status !== 'COMPLETED'" class="pg-edit">
        <template v-if="!editingProgress">
          <el-button size="small" plain @click="startEditProgress">更新进度</el-button>
          <el-button size="small" text @click="openTutor"><Bot :size="13" style="margin-right:4px" /> 让 AI 导师告诉我下一步</el-button>
          <span class="pg-meta">进度到 100% 后,请在下方「项目成果」提交,评审通过才算完成</span>
        </template>
        <template v-else>
          <el-slider v-model="progressForm.progress" :step="5" show-stops style="flex:1;max-width:320px" />
          <b class="pg-pct">{{ progressForm.progress }}%</b>
          <el-input v-model="progressForm.currentTask" size="small" placeholder="接下来要做的任务" style="width:240px" maxlength="60" />
          <el-button size="small" type="primary" :loading="savingProgress" @click="saveProgress">保存</el-button>
          <el-button size="small" text @click="editingProgress = false">取消</el-button>
        </template>
      </div>
    </div>


    <!-- 内容 tabs -->
    <div class="card">
      <el-tabs v-model="tab">
        <el-tab-pane label="项目概览" name="overview">
          <h4 class="sec-head"><ClipboardList :size="16" color="#2563eb" /> 项目描述</h4>
          <div v-if="isRich(project.description)" class="intro-box rich-content" v-html="project.description"></div>
          <div v-else class="intro-box intro-plain">{{ project.description || project.summary || '暂无详细描述' }}</div>

          <h4 class="sec-head"><Zap :size="16" color="#f59e0b" /> 项目特性</h4>
          <div class="tag-wrap">
            <span v-for="f in arr(project.features)" :key="f" class="chip">{{ f }}</span>
          </div>

          <h4 class="sec-head"><Target :size="16" color="#16a34a" /> 学习目标</h4>
          <div class="goal-grid">
            <div v-for="g in arr(project.learningGoals)" :key="g" class="goal-item">
              <span class="goal-check"><Check :size="11" :stroke-width="3" /></span>{{ g }}
            </div>
          </div>

          <h4 class="sec-head"><Pin :size="16" color="#9333ea" /> 前置知识要求</h4>
          <div class="goal-grid">
            <div v-for="g in arr(project.prerequisites)" :key="g" class="goal-item">
              <span class="goal-dot">•</span>{{ g }}
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="技能要求" name="skills">
          <p class="tab-tip">完成本项目需要具备以下技能，我们将根据你的技能评估结果提供个性化学习建议。</p>
          <el-empty v-if="arr(project.skillRequirements).length === 0" description="暂无技能要求数据" />
          <div v-for="s in arr(project.skillRequirements)" :key="s.name" class="skill-row">
            <div class="skill-head">
              <span>{{ s.name }}</span>
              <span class="skill-nums">当前: {{ mySkill(s.name) }} / 需要: {{ s.required }}</span>
            </div>
            <el-progress :percentage="Math.min(100, mySkill(s.name))" :stroke-width="10"
                         :color="mySkill(s.name) >= s.required ? '#16a34a' : '#f97316'" :show-text="false" />
            <div class="skill-tip" :class="mySkill(s.name) >= s.required ? 'ok' : 'warn'">
              {{ mySkill(s.name) >= s.required
                ? '技能已满足要求，可以开始学习'
                : `该技能还需提升 ${s.required - mySkill(s.name)}%，建议先完成相关课程学习` }}
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="所需设备" name="equipment">
          <el-empty v-if="arr(project.equipmentNames).length === 0" description="暂无设备信息" />
          <div v-for="name in arr(project.equipmentNames)" :key="name" class="equip-row">
            <span class="equip-name"><Wrench :size="14" /> {{ name }}</span>
            <el-button size="small" type="primary" plain
                       @click="$router.push({ path: '/app/equipment', query: { keyword: name } })">
              去借阅
            </el-button>
          </div>
          <div class="notice-box">
            <div class="notice-title">设备使用提示</div>
            <ul>
              <li>开发板类设备标准借用期限为 2 周,到期前 3 天可续借一次</li>
              <li>精密仪器需在指导老师监督下使用</li>
              <li>使用前请仔细阅读设备操作手册</li>
              <li>如遇设备故障请及时联系实验室管理员</li>
            </ul>
          </div>
        </el-tab-pane>

        <el-tab-pane label="教学大纲" name="syllabus">
          <el-empty v-if="arr(project.syllabus).length === 0" description="暂无教学大纲" />
          <el-timeline v-else>
            <el-timeline-item v-for="s in arr(project.syllabus)" :key="s.phase"
                              :timestamp="s.phase" placement="top" color="#3b82f6">
              <div class="syllabus-item">
                <div class="syl-title">{{ s.title }} <span class="syl-hours">预计{{ s.hours }}小时 · 包含实践任务</span></div>
                <div class="syl-content">{{ s.content }}</div>
              </div>
            </el-timeline-item>
          </el-timeline>
          <div class="notice-box">
            <div class="notice-title">考核方式</div>
            <template v-if="assessments.length">
              <div v-for="a in assessments" :key="a.name" class="assess-row">
                <span>{{ a.name }}<small v-if="a.desc" class="assess-desc-inline"> · {{ a.desc }}</small></span>
                <span>{{ a.weight }}%</span>
              </div>
              <div class="assess-foot">每项在「项目成果」页签单独提交并评分,全部评完按权重计算综合分,≥60 分判定项目完成。</div>
            </template>
            <div v-else class="assess-foot">本项目按整体成果一次性提交评审,评分 ≥60 分判定完成。</div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="BOM清单" name="bom">
          <div class="bom-head">
            <span>BOM物料清单 · 预估成本: {{ project.cost ? '¥' + project.cost : '未填写' }}</span>
            <div>
              <a v-for="f in designFiles" :key="f.name" :href="f.url" target="_blank" class="design-file">
                <Download :size="13" /> {{ f.type }}: {{ f.name }}
              </a>
              <el-button size="small" @click="exportBom">导出BOM表</el-button>
            </div>
          </div>
          <el-table :data="arr(project.bom)" stripe size="small">
            <el-table-column prop="ref" label="位号" width="80" />
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="qty" label="数量" width="70" />
            <el-table-column prop="footprint" label="封装" width="110" />
            <el-table-column label="参考价格" width="100">
              <template #default="{ row }">¥{{ row.price }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="学习资源" name="resources">
          <el-empty v-if="arr(project.resources).length === 0" description="教师尚未上传教学资料" />
          <div v-for="r in arr(project.resources)" :key="r.name" class="res-row">
            <span class="res-type badge badge-blue">{{ r.type }}</span>
            <span class="res-name">{{ r.name }}</span>
            <a v-if="r.url" :href="r.url" :download="r.name" target="_blank" class="res-download">
              <Download :size="13" /> 下载
            </a>
            <el-button v-else size="small" text type="info" @click="downloadResource(r)">待上传</el-button>
          </div>
        </el-tab-pane>

        <el-tab-pane :label="`项目讨论(${topics.length})`" name="discussions">
          <div class="disc-post">
            <el-input v-model="newTopic" type="textarea" :rows="4" placeholder="发起讨论,分享你的经验或问题..." />
            <el-button type="primary" :loading="posting" @click="submitTopic">发起讨论</el-button>
          </div>
          <el-empty v-if="topics.length === 0" description="还没有讨论,来发第一帖吧!" />
          <div v-for="t in topics" :key="t.item.id" class="disc-topic">
            <div class="disc-head">
              <span class="avatar small">{{ (t.item.userName || '?')[0] }}</span>
              <b>{{ t.item.userName }}</b>
              <span class="disc-time">{{ fmtTime(t.item.createdAt) }}</span>
            </div>
            <div class="disc-content">{{ t.item.content }}</div>
            <div class="disc-reply-line">
              <span class="disc-count">{{ t.replies.length }} 条回复</span>
              <el-button size="small" text type="primary" @click="replyTarget = replyTarget === t.item.id ? null : t.item.id">
                回复
              </el-button>
            </div>
            <div v-for="r in t.replies" :key="r.id" class="disc-reply">
              <b>{{ r.userName }}</b>: {{ r.content }}
              <span class="disc-time">{{ fmtTime(r.createdAt) }}</span>
            </div>
            <div v-if="replyTarget === t.item.id" class="disc-reply-box">
              <el-input v-model="replyContent" size="small" placeholder="写下你的回复..."
                        @keyup.enter="submitReply(t.item.id)" />
              <el-button size="small" type="primary" @click="submitReply(t.item.id)">发送</el-button>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="项目成果" name="submission">
          <el-empty v-if="!detail.enrollment" description="报名参与项目后,可在这里提交成果并查看评审结果" />

          <!-- 分阶段考核模式 -->
          <template v-else-if="assessments.length">
            <div class="assess-summary">
              <b>分阶段考核</b>
              <span class="pg-meta">共 {{ assessments.length }} 项,每项单独评分,全部评完自动按权重计算综合分(≥60 判定项目完成)</span>
              <span v-if="overallScore !== null" class="assess-overall" :class="{ pass: overallScore >= 60 }">
                综合 {{ overallScore }} 分 {{ overallScore >= 60 ? '· 已达标' : '· 未达标' }}
              </span>
            </div>

            <div v-for="a in assessments" :key="a.name" class="assess-item">
              <div class="assess-head">
                <b>{{ a.name }}</b>
                <span class="chip">权重 {{ a.weight }}%</span>
                <span v-if="latestFor(a.name)" class="badge"
                      :class="latestFor(a.name).status === 'GRADED'
                        ? (latestFor(a.name).score >= 60 ? 'badge-green' : 'badge-red')
                        : latestFor(a.name).status === 'RETURNED' ? 'badge-red' : 'badge-yellow'">
                  {{ latestFor(a.name).status === 'GRADED' ? `已评 ${latestFor(a.name).score} 分` : latestFor(a.name).status === 'RETURNED' ? '已退回,请修改' : '评审中' }}
                </span>
                <span v-else class="badge badge-gray">未提交</span>
                <el-button v-if="canSubmitFor(a.name)" size="small" type="primary" plain class="assess-btn"
                           @click="toggleForm(a.name)">
                  {{ activeAssessment === a.name ? '收起' : latestFor(a.name) ? '再次提交' : '提交成果' }}
                </el-button>
              </div>
              <div v-if="a.desc" class="assess-desc">{{ a.desc }}</div>

              <div v-if="latestFor(a.name)" class="sub-last">
                <div class="sub-content">{{ latestFor(a.name).content }}</div>
                <img v-if="latestFor(a.name).attachmentUrl" :src="latestFor(a.name).attachmentUrl"
                     class="sub-shot" alt="成果截图" />
                <div v-if="latestFor(a.name).status !== 'SUBMITTED' && latestFor(a.name).feedback"
                     class="sub-grade" :class="{ pass: latestFor(a.name).status === 'GRADED' && latestFor(a.name).score >= 60 }">
                  {{ latestFor(a.name).status === 'RETURNED' ? '老师退回修改意见' : '评语' }}: {{ latestFor(a.name).feedback }}
                  <span class="sub-meta">{{ latestFor(a.name).graderName }} · {{ fmtTime(latestFor(a.name).gradedAt) }}</span>
                </div>
              </div>

              <div v-if="activeAssessment === a.name" class="sub-form">
                <el-input v-model="workContent" type="textarea" :rows="3" maxlength="1000" show-word-limit
                          :placeholder="`描述「${a.name}」的完成情况与实现细节...`" />
                <div class="sub-form-row">
                  <div class="sub-uploader">
                    <ImageUploader v-model="workShot" />
                  </div>
                  <el-button type="primary" :loading="submittingWork" class="sub-submit"
                             @click="doSubmitWork(a.name)">
                    提交「{{ a.name }}」成果
                  </el-button>
                </div>
              </div>
            </div>
          </template>

          <template v-else>
            <div class="pg-head">
              <b>我的成果</b>
              <span v-if="mySubmission" class="badge"
                    :class="mySubmission.status === 'GRADED' ? (mySubmission.score >= 60 ? 'badge-green' : 'badge-red') : mySubmission.status === 'RETURNED' ? 'badge-red' : 'badge-yellow'">
                {{ mySubmission.status === 'GRADED' ? `已评分 ${mySubmission.score} 分` : mySubmission.status === 'RETURNED' ? '已退回,请修改后重新提交' : '评审中' }}
              </span>
              <span v-else class="pg-meta">完成项目后提交成果,评分 ≥60 分自动判定项目完成并获得经验值</span>
            </div>

            <div v-if="mySubmission" class="sub-last">
              <div class="sub-content">{{ mySubmission.content }}</div>
              <img v-if="mySubmission.attachmentUrl" :src="mySubmission.attachmentUrl" class="sub-shot" alt="成果截图" />
              <div v-if="mySubmission.status === 'GRADED'" class="sub-grade" :class="{ pass: mySubmission.score >= 60 }">
                <b>{{ mySubmission.score >= 60 ? '评审通过,项目判定完成!' : '未达标,可修改后再次提交。' }}</b>
                <template v-if="mySubmission.feedback">评语: {{ mySubmission.feedback }}</template>
                <span class="sub-meta">{{ mySubmission.graderName }} 评于 {{ fmtTime(mySubmission.gradedAt) }}</span>
              </div>
              <div v-else-if="mySubmission.status === 'RETURNED'" class="sub-grade">
                <b>老师退回了这份成果,请按意见修改后重新提交。</b>
                <template v-if="mySubmission.feedback">修改意见: {{ mySubmission.feedback }}</template>
                <span class="sub-meta">{{ mySubmission.graderName }} · {{ fmtTime(mySubmission.gradedAt) }}</span>
              </div>
              <div v-else class="sub-waiting">已提交,等待老师评审 · {{ fmtTime(mySubmission.submittedAt) }}</div>
            </div>

            <div v-if="canSubmitWork" class="sub-form">
              <el-input v-model="workContent" type="textarea" :rows="3" maxlength="1000" show-word-limit
                        :placeholder="mySubmission ? '修改完善后可再次提交...' : '描述你的实现思路、完成情况与心得...'" />
              <div class="sub-form-row">
                <div class="sub-uploader">
                  <ImageUploader v-model="workShot" />
                </div>
                <el-button type="primary" :loading="submittingWork" class="sub-submit" @click="doSubmitWork('')">
                  {{ mySubmission ? '再次提交成果' : '提交成果' }}
                </el-button>
              </div>
            </div>
          </template>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft, Bot, Check, ClipboardList, Clock, DollarSign, Download, Eye, FileText,
  Heart, Pin, Share2, Target, User, Users, Wrench, Zap
} from 'lucide-vue-next'
import {
  enrollProject, fetchDiscussions, fetchMySubmission, fetchMySubmissions, fetchProjectDetail,
  fetchSkills, postDiscussion, submitWork, toggleFavorite, updateProgress
} from '../../api'
import ImageUploader from '../../components/ImageUploader.vue'
import TutorPanel from '../../components/TutorPanel.vue'

const route = useRoute()
const detail = ref({ project: null, enrolled: false, favorited: false, enrollment: null })
const editingProgress = ref(false)
const savingProgress = ref(false)
const progressForm = reactive({ progress: 0, currentTask: '' })
const skills = ref([])
const tab = ref('overview')
const enrolling = ref(false)
const coverFailed = ref(false)
const topics = ref([])
const newTopic = ref('')
const replyTarget = ref(null)
const replyContent = ref('')
const posting = ref(false)
const mySubmission = ref(null)
const mySubs = ref([])
const activeAssessment = ref('')
const workContent = ref('')
const workShot = ref('')
const submittingWork = ref(false)

const fmtTime = (t) => (t || '').replace('T', ' ').slice(0, 16)
const fmtNum = (n) => {
  const v = n || 0
  return v >= 1000 ? (v / 1000).toFixed(1) + 'k' : String(v)
}
const diffColor = (d) => d === '入门' ? 'green' : d === '进阶' ? 'blue' : 'red'

const project = computed(() => detail.value.project)
const arr = (v) => Array.isArray(v) ? v : []
const pcbText = computed(() => {
  const parts = []
  if (project.value?.layers) parts.push(project.value.layers + ' 层板')
  if (project.value?.pcbSize) parts.push(project.value.pcbSize)
  return parts.join(' · ')
})
// 教师在资源里上传的原理图 / LAYOUT / 3D 文件直接作为设计文件下载入口
const designFiles = computed(() =>
  arr(project.value?.resources).filter((r) => r.url && ['原理图', 'LAYOUT', '3D图'].includes(r.type)))

// AI 导师是项目页内的面板,不跳走;带 ?tutor=1 进来时自动展开
const tutorVisible = ref(route.query.tutor === '1' || route.query.tutor === 1)
const openTutor = () => { tutorVisible.value = true }
const reloadDetail = async () => { detail.value = await fetchProjectDetail(route.params.id) }
const goSubmit = () => { tutorVisible.value = false; tab.value = 'submission' }

// ---------- 按大纲阶段打勾 ----------
const phases = computed(() => arr(project.value?.syllabus))
const donePhases = computed(() => {
  try { return new Set(JSON.parse(detail.value.enrollment?.completedPhases || '[]')) } catch (e) { return new Set() }
})
const nextPhase = computed(() => {
  for (let i = 1; i <= phases.value.length; i++) if (!donePhases.value.has(i)) return i
  return null
})
const togglePhase = async (no, checked) => {
  const set = new Set(donePhases.value)
  if (checked) set.add(no); else set.delete(no)
  savingProgress.value = true
  try {
    await updateProgress(route.params.id, { progress: 0, completedPhases: [...set] })
    detail.value = await fetchProjectDetail(route.params.id)
    if (set.size === phases.value.length) ElMessage.success('全部阶段已完成,去「项目成果」提交等待评审吧')
  } catch (e) { /* 已提示 */ } finally {
    savingProgress.value = false
  }
}

const startEditProgress = () => {
  progressForm.progress = detail.value.enrollment?.progress || 0
  progressForm.currentTask = detail.value.enrollment?.currentTask || ''
  editingProgress.value = true
}

const saveProgress = async () => {
  savingProgress.value = true
  try {
    await updateProgress(route.params.id, { progress: progressForm.progress, currentTask: progressForm.currentTask })
    ElMessage.success(progressForm.progress >= 100 ? '进度已到 100%,记得在「项目成果」提交等待评审' : '进度已更新')
    editingProgress.value = false
    detail.value = await fetchProjectDetail(route.params.id)
  } catch (e) { /* 已提示 */ } finally {
    savingProgress.value = false
  }
}
// 富文本描述(后端已消毒);旧数据为纯文本。空编辑器占位 <p><br></p> 不当作有内容。
const isRich = (v) => {
  if (typeof v !== 'string' || !v.includes('<')) return false
  const t = v.replace(/<p><br\s*\/?><\/p>/gi, '').replace(/<[^>]+>/g, '').trim()
  return t.length > 0 || /<(img|video|table|ul|ol)\b/i.test(v)
}

const mySkill = (name) => {
  const s = skills.value.find((x) => x.skillName === name)
  return s ? s.score : 0
}

const canSubmitWork = computed(() =>
  detail.value.enrollment && (!mySubmission.value || mySubmission.value.status !== 'SUBMITTED'))

// ---------- 分阶段考核 ----------
const assessments = computed(() => arr(project.value?.assessments))

const latestFor = (name) =>
  mySubs.value.find((s) => (s.assessmentName || '') === name) || null

const canSubmitFor = (name) => {
  const latest = latestFor(name)
  return !latest || latest.status !== 'SUBMITTED'
}

const overallScore = computed(() => {
  if (!assessments.value.length) return null
  let total = 0
  for (const a of assessments.value) {
    const graded = mySubs.value.find(
      (s) => (s.assessmentName || '') === a.name && s.status === 'GRADED')
    if (!graded) return null
    total += graded.score * a.weight / 100
  }
  return Math.round(total)
})

const toggleForm = (name) => {
  activeAssessment.value = activeAssessment.value === name ? '' : name
  workContent.value = ''
  workShot.value = ''
}

const loadSubmission = async () => {
  if (!detail.value.enrolled) return
  try {
    mySubmission.value = await fetchMySubmission(route.params.id)
    mySubs.value = await fetchMySubmissions(route.params.id)
  } catch (e) { /* 成果加载失败不阻塞详情 */ }
}

const doSubmitWork = async (assessName) => {
  if (!workContent.value.trim()) {
    ElMessage.warning('请填写成果说明')
    return
  }
  submittingWork.value = true
  try {
    await submitWork(route.params.id, {
      content: workContent.value.trim(),
      attachmentUrl: workShot.value || undefined,
      assessmentName: assessName || undefined
    })
    ElMessage.success('提交成功,等待老师评审')
    workContent.value = ''
    workShot.value = ''
    activeAssessment.value = ''
    await loadSubmission()
  } catch (e) { /* 已提示 */ } finally {
    submittingWork.value = false
  }
}

const load = async () => {
  detail.value = await fetchProjectDetail(route.params.id)
  try {
    const s = await fetchSkills()
    skills.value = s.skills
  } catch (e) { /* 技能数据加载失败不阻塞详情 */ }
  try {
    topics.value = await fetchDiscussions(route.params.id)
  } catch (e) { /* 讨论加载失败不阻塞详情 */ }
  loadSubmission()
}

const submitTopic = async () => {
  if (!newTopic.value.trim()) {
    ElMessage.warning('请输入讨论内容')
    return
  }
  posting.value = true
  try {
    await postDiscussion(route.params.id, { content: newTopic.value })
    newTopic.value = ''
    topics.value = await fetchDiscussions(route.params.id)
    ElMessage.success('发布成功')
  } catch (e) { /* 已提示 */ } finally {
    posting.value = false
  }
}

const submitReply = async (parentId) => {
  if (!replyContent.value.trim()) return
  await postDiscussion(route.params.id, { content: replyContent.value, parentId })
  replyContent.value = ''
  replyTarget.value = null
  topics.value = await fetchDiscussions(route.params.id)
}

const doEnroll = async () => {
  enrolling.value = true
  try {
    await enrollProject(route.params.id)
    ElMessage.success('报名成功,项目已加入个人中心!')
    await load()
  } catch (e) { /* 提示已由拦截器处理 */ } finally {
    enrolling.value = false
  }
}

const doFavorite = async () => {
  const { favorited } = await toggleFavorite(route.params.id)
  detail.value.favorited = favorited
  ElMessage.success(favorited ? '已收藏' : '已取消收藏')
}

const doShare = async () => {
  try {
    await navigator.clipboard.writeText(location.href)
    ElMessage.success('项目链接已复制到剪贴板')
  } catch (e) {
    ElMessage.info(location.href)
  }
}

/** 导出 BOM 为 CSV(带 BOM 头,Excel 打开中文不乱码) */
const exportBom = () => {
  const rows = arr(project.value.bom)
  if (rows.length === 0) {
    ElMessage.info('该项目暂无 BOM 数据')
    return
  }
  const esc = (v) => `"${String(v ?? '').replace(/"/g, '""')}"`
  const lines = [
    ['位号', '名称', '数量', '封装', '参考价格(元)'].map(esc).join(','),
    ...rows.map((r) => [r.ref, r.name, r.qty, r.footprint, r.price].map(esc).join(','))
  ]
  const blob = new Blob(['\uFEFF' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `${project.value.title}-BOM.csv`
  a.click()
  URL.revokeObjectURL(a.href)
  ElMessage.success('BOM 表已导出')
}

const downloadResource = (r) => {
  ElMessage.info(`《${r.name}》暂未上传附件,教师上传后这里即可直接下载`)
}

onMounted(load)
</script>

<style scoped>
.back-link {
  color: var(--text-secondary); font-size: 13px; cursor: pointer;
  display: inline-flex; align-items: center; gap: 4px; margin-bottom: 12px;
}
.back-link:hover { color: var(--brand-blue); }

/* Hero 大图 */
.hero {
  position: relative;
  height: 260px;
  border-radius: 16px;
  overflow: hidden;
  margin-bottom: 16px;
  box-shadow: var(--shadow-lg);
}
.hero-img { width: 100%; height: 100%; object-fit: cover; display: block; }
.hero-fallback { width: 100%; height: 100%; background: linear-gradient(135deg, #1e3a8a, #6d28d9); }
.hero-mask {
  position: absolute; inset: 0;
  background: linear-gradient(to top, rgba(17,24,39,.85), rgba(17,24,39,.25) 60%, rgba(17,24,39,.1));
}
.hero-content { position: absolute; left: 28px; right: 28px; bottom: 22px; color: #fff; }
.hero-badges { display: flex; gap: 8px; margin-bottom: 10px; }
.hero-title { margin: 0 0 8px; font-size: 30px; font-weight: 800; text-shadow: 0 2px 8px rgba(0,0,0,.4); }
.hero-summary {
  margin: 0; font-size: 14px; color: rgba(255,255,255,.85); max-width: 720px;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}

/* 元信息条 */
.meta-bar {
  display: flex; justify-content: space-between; align-items: center; gap: 16px;
  padding: 14px 20px; margin-bottom: 16px; flex-wrap: wrap;
}
.meta-left { display: flex; gap: 20px; font-size: 13px; color: var(--text-secondary); flex-wrap: wrap; }
.meta-left span { display: inline-flex; align-items: center; gap: 5px; }
.meta-actions { display: flex; align-items: center; gap: 10px; }
.circle-btn {
  width: 36px; height: 36px; border-radius: 50%;
  border: 1px solid var(--border); background: #fff;
  cursor: pointer; font-size: 15px;
  display: flex; align-items: center; justify-content: center;
  transition: all .15s;
}
.circle-btn:hover { border-color: #93c5fd; }
.enroll-btn {
  background: var(--brand-blue); color: #fff;
  border: none; border-radius: 10px;
  padding: 10px 26px; font-size: 14px; font-weight: 600; cursor: pointer;
  box-shadow: 0 6px 12px -2px rgba(37,99,235,.35);
  transition: background .15s;
}
.enroll-btn:hover { background: #1d4ed8; }
.enroll-btn:disabled { background: #93c5fd; cursor: not-allowed; box-shadow: none; }
.tutor-btn {
  display: inline-flex; align-items: center; gap: 6px;
  background: linear-gradient(135deg, #7c3aed, #9333ea); color: #fff;
  border: none; border-radius: 10px; padding: 10px 18px; font-size: 14px; font-weight: 600; cursor: pointer;
  box-shadow: 0 6px 12px -2px rgba(147,51,234,.35); transition: opacity .15s;
}
.tutor-btn:hover { opacity: .9; }
.pg-edit { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-top: 12px; }
.phase-list { margin-top: 14px; display: flex; flex-direction: column; gap: 6px; }
.phase-item { display: flex; align-items: flex-start; gap: 10px; padding: 8px 12px; border-radius: 10px; border: 1px solid transparent; cursor: pointer; }
.phase-item.next { background: #eff6ff; border-color: #bfdbfe; }
.phase-item.done .phase-title { color: #9ca3af; text-decoration: line-through; }
.phase-text { flex: 1; min-width: 0; }
.phase-title { font-size: 14px; color: #111827; }
.phase-no { font-size: 12px; color: var(--brand-blue); font-weight: 600; margin-right: 8px; }
.phase-title small { color: var(--text-secondary); font-weight: 400; }
.phase-content { font-size: 12px; color: var(--text-secondary); margin-top: 4px; line-height: 1.6; white-space: pre-wrap; }
.pg-pct { font-size: 13px; width: 40px; }
.assess-desc-inline { color: var(--text-secondary); font-weight: 400; }
.assess-foot { font-size: 12px; color: var(--text-secondary); margin-top: 8px; line-height: 1.6; }
.design-file { display: inline-flex; align-items: center; gap: 4px; font-size: 12px; color: var(--brand-blue); margin-right: 10px; }

.stat-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 16px; }
@media (max-width: 900px) { .stat-grid { grid-template-columns: repeat(2, 1fr); } }

/* 讲师条 */
.mentor-bar {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16px 20px; margin-bottom: 16px; flex-wrap: wrap; gap: 12px;
}
.mentor-left { display: flex; align-items: center; gap: 12px; }
.mentor-name-row { display: flex; align-items: center; gap: 8px; font-size: 15px; }
.mentor-role { font-size: 12px; color: var(--text-secondary); margin-top: 2px; }
.mentor-meta { display: flex; gap: 18px; font-size: 13px; color: var(--text-secondary); flex-wrap: wrap; }
.mentor-meta span { display: inline-flex; align-items: center; gap: 5px; }

.progress-bar-card { padding: 16px 20px; margin-bottom: 16px; }
.pg-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; font-size: 14px; }
.pg-meta { font-size: 12px; color: var(--text-secondary); }

.submission-card { padding: 16px 20px; margin-bottom: 16px; }
.assess-summary {
  display: flex; align-items: center; gap: 12px; flex-wrap: wrap;
  background: #f9fafb; border: 1px solid var(--border); border-radius: 12px;
  padding: 12px 16px; margin-bottom: 14px; font-size: 14px;
}
.assess-overall { font-weight: 700; color: #dc2626; margin-left: auto; }
.assess-overall.pass { color: #16a34a; }
.assess-item {
  border: 1px solid var(--border); border-radius: 12px;
  padding: 14px 16px; margin-bottom: 12px;
}
.assess-head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.assess-btn { margin-left: auto; }
.assess-desc { font-size: 12px; color: var(--text-secondary); margin-top: 6px; }
.assess-item .sub-last { margin-top: 10px; }
.assess-item .sub-form { margin-top: 12px; }

.rich-content { line-height: 1.8; }
.rich-content :deep(img) { max-width: 100%; border-radius: 10px; margin: 8px 0; }
.rich-content :deep(video) { max-width: 100%; border-radius: 10px; margin: 8px 0; }
.rich-content :deep(p) { margin: 6px 0; }
.rich-content :deep(table) { border-collapse: collapse; }
.rich-content :deep(td), .rich-content :deep(th) { border: 1px solid #e5e7eb; padding: 4px 10px; }
.sub-last { margin-bottom: 14px; }
.sub-content {
  background: #f9fafb; border-radius: 10px; padding: 12px 14px;
  font-size: 13px; line-height: 1.7; white-space: pre-wrap;
}
.sub-shot { max-width: 320px; border-radius: 10px; margin-top: 10px; display: block; }
.sub-grade {
  margin-top: 10px; padding: 10px 14px; border-radius: 10px;
  background: #fee2e2; font-size: 13px; display: flex; flex-direction: column; gap: 4px;
}
.sub-grade.pass { background: #dcfce7; }
.sub-meta { font-size: 12px; color: var(--text-secondary); }
.sub-waiting { margin-top: 10px; font-size: 13px; color: #ca8a04; }
.sub-form { display: flex; flex-direction: column; gap: 12px; }
.sub-form-row { display: flex; gap: 14px; align-items: flex-start; }
.sub-uploader { width: 220px; flex-shrink: 0; }
.sub-submit { margin-left: auto; }

h4 { margin: 20px 0 10px; font-size: 15px; }
h4:first-child { margin-top: 6px; }
.sec-head { display: flex; align-items: center; gap: 7px; }

.intro-box {
  background: linear-gradient(to right, #eff6ff, #eef2ff);
  border: 1px solid #dbeafe;
  border-radius: 12px;
  padding: 16px 18px;
  color: #1e3a5f; line-height: 1.9; font-size: 14px;
}
.intro-plain { white-space: pre-wrap; word-break: break-word; }

.tag-wrap { display: flex; flex-wrap: wrap; }

.goal-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px 24px; }
@media (max-width: 900px) { .goal-grid { grid-template-columns: 1fr; } }
.goal-item { display: flex; align-items: center; gap: 8px; font-size: 14px; color: #374151; padding: 4px 0; }
.goal-check {
  width: 18px; height: 18px; border-radius: 50%; flex-shrink: 0;
  background: #dcfce7; color: #16a34a;
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 11px; font-weight: 700;
}
.goal-dot { color: var(--brand-blue); font-weight: 700; }

.tab-tip {
  background: #eff6ff; border-radius: 10px; padding: 12px 14px;
  font-size: 13px; color: #1d4ed8;
}
.skill-row { margin-bottom: 18px; }
.skill-head { display: flex; justify-content: space-between; font-size: 14px; margin-bottom: 6px; }
.skill-nums { color: var(--text-secondary); font-size: 12px; }
.skill-tip { font-size: 12px; margin-top: 4px; }
.skill-tip.ok { color: #16a34a; }
.skill-tip.warn { color: #f97316; }

.equip-row {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 4px; border-bottom: 1px solid var(--border); font-size: 14px;
}
.equip-name { display: inline-flex; align-items: center; gap: 8px; color: #374151; }
.notice-box {
  background: #fefce8; border-radius: 12px; padding: 14px 16px; margin-top: 16px;
  font-size: 13px; color: #713f12;
}
.notice-box .notice-title { font-weight: 600; margin-bottom: 8px; }
.notice-box ul { margin: 0; padding-left: 18px; }
.notice-box li { padding: 2px 0; }
.assess-row { display: flex; justify-content: space-between; padding: 4px 0; }

.syllabus-item { padding-bottom: 6px; }
.syl-title { font-weight: 600; font-size: 14px; }
.syl-hours { font-weight: 400; font-size: 12px; color: var(--text-secondary); margin-left: 8px; }
.syl-content { font-size: 13px; color: var(--text-secondary); margin-top: 4px; }

.bom-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; font-size: 14px; font-weight: 600; }

.res-row { display: flex; align-items: center; gap: 12px; padding: 10px 4px; border-bottom: 1px solid var(--border); }
.res-name { flex: 1; font-size: 14px; }
.res-download {
  display: inline-flex; align-items: center; gap: 4px;
  color: var(--brand-blue); font-size: 13px; font-weight: 500;
}
.res-download:hover { text-decoration: underline; }
.book-row { padding: 8px 4px; font-size: 14px; color: #374151; display: flex; gap: 10px; align-items: center; }

.avatar {
  width: 40px; height: 40px; border-radius: 50%;
  background: var(--brand-gradient); color: #fff;
  display: flex; align-items: center; justify-content: center;
}
.avatar.big { width: 48px; height: 48px; font-size: 17px; }

/* 项目讨论 */
.disc-post { display: flex; gap: 10px; align-items: flex-end; margin-bottom: 18px; }
.disc-post :deep(.el-textarea) { flex: 1; }
.disc-topic { border-bottom: 1px solid var(--border); padding: 14px 0; }
.disc-head { display: flex; align-items: center; gap: 8px; font-size: 14px; }
.avatar.small { width: 28px; height: 28px; font-size: 12px; }
.disc-time { color: #9ca3af; font-size: 12px; margin-left: auto; }
.disc-content { margin: 8px 0; font-size: 14px; color: #374151; }
.disc-reply-line { display: flex; align-items: center; gap: 10px; }
.disc-count { font-size: 12px; color: var(--text-secondary); }
.disc-reply {
  background: #f9fafb; border-radius: 8px; padding: 8px 12px;
  font-size: 13px; margin-top: 8px; display: flex; gap: 6px; align-items: center;
}
.disc-reply-box { display: flex; gap: 8px; margin-top: 10px; }
</style>
