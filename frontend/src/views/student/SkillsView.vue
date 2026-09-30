<template>
  <div class="skills-page">
    <header class="page-heading">
      <div class="heading-copy">
        <span class="heading-icon"><ChartNoAxesCombined :size="23" aria-hidden="true" /></span>
        <div><h2>技能评估</h2><p>了解当前水平，找到下一步的学习方向。</p></div>
      </div>
      <button class="primary-button" :disabled="loading || loadError || !data.skills.length" @click="openQuiz(null)">
        <Zap :size="16" aria-hidden="true" /> AI 能力测评
      </button>
    </header>

    <div v-if="loading" class="panel loading-panel" aria-busy="true" aria-label="正在加载技能画像">
      <el-skeleton :rows="7" animated />
    </div>
    <div v-else-if="loadError" class="panel empty-state" role="alert">
      <CircleAlert :size="32" aria-hidden="true" /><h3>技能画像暂时未能加载</h3>
      <p>请重试，加载成功后即可查看能力与学习建议。</p>
      <button class="secondary-button" @click="load">重新加载</button>
    </div>
    <div v-else-if="!data.skills.length" class="panel empty-state">
      <Layers :size="36" aria-hidden="true" /><h3>技能画像即将开启</h3>
      <p>管理员还未配置技能维度，配置完成后即可开始测评。</p>
    </div>
    <template v-else>
      <nav class="section-tabs" role="tablist" aria-label="技能评估分区">
        <button v-for="(tab, index) in tabs" :id="'skill-tab-' + tab.key" :key="tab.key" role="tab"
                :aria-selected="activeTab === tab.key" :aria-controls="'skill-panel-' + tab.key"
                :tabindex="activeTab === tab.key ? 0 : -1" :class="{ active: activeTab === tab.key }"
                @click="activeTab = tab.key" @keydown="onTabKey($event, index)">
          <component :is="tab.icon" :size="17" aria-hidden="true" />{{ tab.label }}
          <span v-if="tab.key === 'skills'" class="tab-count">{{ data.skills.length }}</span>
        </button>
      </nav>

      <div :id="'skill-panel-' + activeTab" class="tab-panel" role="tabpanel" :aria-labelledby="'skill-tab-' + activeTab" tabindex="0">
        <section v-if="activeTab === 'overview'" class="overview">
          <div class="overview-grid">
            <article class="panel profile-panel">
              <div class="section-heading">
                <div><h3>综合能力画像</h3><p>让每一次测评与实践都有迹可循</p></div>
                <span class="status-tag" :class="{ verified: assessedCount > 0 }">
                  <component :is="assessedCount ? BadgeCheck : CircleDashed" :size="13" aria-hidden="true" />
                  {{ assessedCount ? '持续更新中' : '待测评校准' }}
                </span>
              </div>
              <div class="profile-body">
                <div class="profile-score">
                  <span class="metric-label">综合掌握度</span>
                  <div class="score-number">{{ data.overall }}<span>/ 100</span></div>
                  <span class="score-caption">{{ data.evidenceTotal ? '结合测评与项目实证' : '当前分数有待实证校准' }}</span>
                  <div class="profile-metrics">
                    <div><span>已实证维度</span><b>{{ assessedCount }}<small> / {{ data.skills.length }}</small></b></div>
                    <div><span>累计实证</span><b>{{ data.evidenceTotal }}<small> 次</small></b></div>
                  </div>
                </div>
                <div ref="radarRef" class="radar-chart" role="img" :aria-label="radarDescription"></div>
              </div>
              <div class="profile-foot">
                <Info :size="14" aria-hidden="true" />
                <span>{{ data.evidenceTotal ? '项目评审与 AI 测评共同更新画像。' : '完成一次 AI 测评或项目评审后，分数会自动校准。' }}
                  <template v-if="data.skills.length > 8">雷达展示前 8 个维度，全部维度可在技能明细查看。</template>
                </span>
              </div>
            </article>

            <article class="next-panel">
              <div class="next-eyebrow"><Compass :size="17" aria-hidden="true" /> 下一步，往这里走</div>
              <div class="next-main">
                <span class="next-label">{{ nextSkill.evidenceCount ? '继续巩固你的能力' : '从一个小测评开始' }}</span>
                <h3>{{ nextSkill.skillName }}</h3>
                <p>{{ nextSkill.evidenceCount ? '通过一次测评，看看最近的学习带来了哪些进步。' : '先了解这一项的真实水平，让后续推荐更适合你。' }}</p>
                <button class="primary-button" @click="openQuiz(nextSkill.skillName)">
                  {{ nextSkill.evidenceCount ? '测一测当前水平' : '开始首次测评' }}<ArrowRight :size="16" aria-hidden="true" />
                </button>
                <span class="quiz-duration"><Clock3 :size="13" aria-hidden="true" /> 6 道选择题 · 约 1 分钟</span>
              </div>
              <button class="plan-shortcut" @click="activeTab = 'plan'">
                <span class="shortcut-icon"><Route :size="19" aria-hidden="true" /></span>
                <span><b>想知道接下来学什么？</b><small>为自己规划一条学习路线</small></span>
                <ChevronRight :size="17" aria-hidden="true" />
              </button>
            </article>
          </div>

          <section class="priority-section" aria-labelledby="priority-title">
            <div class="section-heading">
              <div><h3 id="priority-title">值得关注的技能</h3><p>先补齐能力画像，再有针对性地提升</p></div>
              <button class="text-button" @click="activeTab = 'skills'">查看全部 <ArrowRight :size="15" aria-hidden="true" /></button>
            </div>
            <div class="priority-grid">
              <article v-for="s in prioritySkills" :key="s.skillName" class="priority-card">
                <div class="skill-card-top">
                  <span class="skill-icon" :style="skillStyle(s)"><component :is="skillMeta(s).icon" :size="19" aria-hidden="true" /></span>
                  <div><h4>{{ s.skillName }}</h4><span class="small-muted">{{ s.evidenceCount ? levelText(s.score) : '尚无实证 · 待校准' }}</span></div>
                  <b class="compact-score">{{ s.score }}<small>%</small></b>
                </div>
                <div class="skill-bar" aria-hidden="true"><span :style="{ width: s.score + '%' }"></span></div>
                <div class="priority-foot"><span>{{ s.evidenceCount ? s.evidenceCount + ' 次实证' : '建立你的能力起点' }}</span>
                  <button class="text-button" :aria-label="'测评' + s.skillName" @click="openQuiz(s.skillName)">去测评 <ArrowUpRight :size="14" aria-hidden="true" /></button>
                </div>
              </article>
            </div>
          </section>
        </section>

        <section v-else-if="activeTab === 'skills'" aria-label="技能明细">
          <div class="section-heading detail-heading"><div><h3>每一项能力，都看得清</h3><p>选择一个维度，查看实证或进行针对性测评。</p></div></div>
          <div class="filter-toolbar">
            <div class="filter-pills" aria-label="按实证情况筛选">
              <button v-for="filter in skillFilters" :key="filter.key" :class="{ selected: skillFilter === filter.key }"
                      :aria-pressed="skillFilter === filter.key" @click="skillFilter = filter.key">{{ filter.label }} <span>{{ filter.count }}</span></button>
            </div>
            <label class="search-field"><Search :size="16" aria-hidden="true" /><input v-model="skillSearch" aria-label="搜索技能" placeholder="搜索技能名称" /><button v-if="skillSearch" aria-label="清除搜索" @click="skillSearch = ''"><X :size="14" /></button></label>
          </div>
          <div v-if="visibleSkills.length" class="skill-grid">
            <article v-for="s in visibleSkills" :key="s.skillName" class="panel skill-card">
              <div class="skill-card-top">
                <span class="skill-icon" :style="skillStyle(s)"><component :is="skillMeta(s).icon" :size="20" aria-hidden="true" /></span>
                <h3>{{ s.skillName }}</h3>
                <span class="status-tag" :class="{ verified: s.evidenceCount > 0 }">{{ s.evidenceCount ? levelText(s.score) : '待校准' }}</span>
              </div>
              <p class="skill-description">{{ s.description || '通过项目实践与 AI 测评，逐步提升这一维度的能力。' }}</p>
              <div class="skill-score-row"><span>{{ s.evidenceCount ? '综合掌握度' : '待校准参考分' }}</span><b>{{ s.score }}<small>%</small></b></div>
              <div class="skill-bar" aria-hidden="true"><span :style="{ width: s.score + '%' }"></span></div>
              <div class="skill-evidence"><BadgeCheck :size="14" aria-hidden="true" /><span>{{ s.evidenceCount ? '已积累 ' + s.evidenceCount + ' 次实证' : '完成测评后，建立能力基线' }}</span></div>
              <details v-if="lastEvent(s.skillName)" class="last-evidence"><summary>最近一次记录</summary><p>{{ lastEvent(s.skillName).note }}</p></details>
              <div class="skill-card-actions">
                <button class="text-button" :disabled="!lastEvent(s.skillName)" @click="showSkillRecords(s.skillName)">查看记录</button>
                <button class="secondary-button" :aria-label="'测评' + s.skillName" @click="openQuiz(s.skillName)">测一测 <ArrowUpRight :size="14" aria-hidden="true" /></button>
              </div>
            </article>
          </div>
          <div v-else class="panel empty-state"><Search :size="30" aria-hidden="true" /><h3>没有匹配的技能</h3><p>试试其他名称，或切换实证筛选。</p><button class="secondary-button" @click="skillSearch = ''; skillFilter = 'all'">重置筛选</button></div>
        </section>

        <section v-else-if="activeTab === 'records'" class="records-section" aria-label="成长记录">
          <article class="panel">
            <div class="section-heading"><div><h3>学习成长曲线</h3><p>每一次测评与项目评审，都是成长的足迹。</p></div><span class="chart-legend"><i></i>综合评分</span></div>
            <div v-if="data.history.length >= 2" ref="lineRef" class="history-chart" role="img" :aria-label="historyDescription"></div>
            <div v-else class="empty-state compact-empty"><ChartNoAxesCombined :size="30" aria-hidden="true" /><h4>你的成长曲线从这里开始</h4><p>积累两次评分记录后，即可看到能力变化趋势。</p></div>
          </article>
          <article class="panel">
            <div class="section-heading records-heading"><div><h3>最近的能力变化</h3><p>查看分数变化的来源与具体反馈</p></div>
              <div class="record-filters">
                <select v-model="eventSkill" aria-label="按技能筛选记录"><option value="">全部技能</option><option v-for="name in eventSkillNames" :key="name" :value="name">{{ name }}</option></select>
                <select v-model="eventSource" aria-label="按来源筛选记录"><option value="">全部来源</option><option v-for="source in eventSources" :key="source" :value="source">{{ sourceText(source) }}</option></select>
              </div>
            </div>
            <div v-if="!filteredEvents.length" class="empty-state compact-empty"><History :size="28" aria-hidden="true" /><h4>{{ data.events.length ? '没有符合筛选条件的记录' : '还没有能力变动记录' }}</h4><p>{{ data.events.length ? '切换技能或来源，查看其他记录。' : '完成测评或项目评审后，分数变化与反馈会保存在这里。' }}</p></div>
            <ol v-else class="event-list">
              <li v-for="(e, i) in pagedEvents" :key="eventPage + '-' + i" class="event-item">
                <span class="event-icon" :class="{ project: e.source === 'PROJECT' }"><component :is="e.source === 'PROJECT' ? BookOpen : ClipboardCheck" :size="17" aria-hidden="true" /></span>
                <div class="event-copy"><div class="event-title"><b>{{ e.skillName }}</b><span>{{ sourceText(e.source) }}</span></div><p>{{ e.note }}</p><time>{{ formatTime(e.createdAt) }}</time></div>
                <div class="event-score" :class="deltaClass(e)"><span>{{ e.beforeScore }}</span><ArrowRight :size="13" aria-hidden="true" /><b>{{ e.afterScore }}</b></div>
              </li>
            </ol>
            <div v-if="filteredEvents.length > eventPageSize" class="pagination"><span>共 {{ filteredEvents.length }} 条记录</span><button class="icon-button" :disabled="eventPage === 1" aria-label="上一页记录" @click="eventPage--"><ChevronLeft :size="17" /></button><span>{{ eventPage }} / {{ eventPageCount }}</span><button class="icon-button" :disabled="eventPage === eventPageCount" aria-label="下一页记录" @click="eventPage++"><ChevronRight :size="17" /></button></div>
          </article>
        </section>

        <section v-else class="plan-layout" aria-label="学习规划">
          <aside class="plan-sidebar">
            <form class="panel plan-form" @submit.prevent="genPlan">
              <span class="plan-icon"><Route :size="22" aria-hidden="true" /></span><h3>规划你的下一程</h3><p>结合能力画像与实战项目，找到适合你的学习节奏。</p>
              <label class="field-label" for="learning-goal">想达成什么目标？<span>选填</span></label>
              <textarea id="learning-goal" v-model="aiGoal" rows="3" maxlength="100" placeholder="例如：做一个能远程监测温度的物联网作品"></textarea>
              <div class="goal-chips"><button v-for="k in goalChips" :key="k" type="button" :class="{ selected: aiGoal.includes(k) }" :aria-pressed="aiGoal.includes(k)" @click="addGoalChip(k)">{{ k }}</button></div>
              <label class="field-label" for="weekly-hours">每周可以投入</label>
              <select id="weekly-hours" v-model="aiHours"><option v-for="h in [4, 6, 8, 10, 15]" :key="h" :value="h">每周 {{ h }} 小时</option></select>
              <button class="primary-button generate-button" type="submit" :disabled="aiLoading || planLoading"><LoaderCircle v-if="aiLoading || planLoading" :size="16" class="spinning" aria-hidden="true" /><Sparkles v-else :size="16" aria-hidden="true" />{{ aiLoading ? '正在规划…' : planLoading ? '读取已有路线…' : plan ? '重新生成学习路线' : '生成学习路线' }}</button>
            </form>
            <details v-if="data.suggestions.length" class="panel suggestions"><summary><Lightbulb :size="17" aria-hidden="true" />个性化学习建议<span>{{ data.suggestions.length }}</span><ChevronDown :size="15" aria-hidden="true" /></summary><ol><li v-for="(suggestion, i) in data.suggestions" :key="i">{{ suggestion }}</li></ol></details>
          </aside>
          <div class="plan-content">
            <article v-if="aiLoading || planLoading" class="panel plan-empty" role="status" aria-live="polite"><LoaderCircle :size="36" class="spinning" aria-hidden="true" /><h3>{{ aiLoading ? '正在为你安排学习路线' : '正在读取已有学习路线' }}</h3><p>{{ aiLoading ? '正在分析能力画像与项目库，通常需要 10–20 秒。' : '稍等片刻，你的规划即将呈现。' }}</p></article>
            <article v-else-if="!plan" class="panel plan-empty">
              <div class="route-illustration" aria-hidden="true"><span><BookOpen :size="25" /></span><i></i><span><Layers :size="25" /></span><i></i><span><Flag :size="25" /></span></div>
              <h3>从现在的你，走向下一个目标</h3><p>填写目标与可用时间，生成一条循序渐进的实战路线。</p>
              <div class="plan-steps"><div><b>01</b><h4>基础补强</h4><span>从适合的难度开始</span></div><div><b>02</b><h4>综合实践</h4><span>在项目里连接知识</span></div><div><b>03</b><h4>挑战提升</h4><span>用作品验证新能力</span></div></div>
              <span class="plan-empty-note">推荐会结合技能差距、项目难度与报名情况</span>
            </article>
            <template v-else>
              <article class="panel plan-summary"><div class="section-heading"><h3><Sparkles :size="18" aria-hidden="true" />你的专属学习路线</h3><span class="status-tag">{{ plan.source === 'AI' ? 'AI 规划' : '智能匹配' }}</span></div><p>{{ plan.summary }}</p>
                <div v-if="plan.focusSkills?.length" class="focus-list"><div v-for="f in plan.focusSkills" :key="f.name"><div><b>{{ f.name }}</b><span>{{ f.currentScore }} <ArrowRight :size="12" aria-hidden="true" /> {{ f.targetScore }}</span></div><p>{{ f.reason }}</p></div></div>
              </article>
              <div class="ai-stages">
                <article v-for="p in plan.recommendedProjects" :key="p.projectId" class="panel ai-project">
                  <div class="project-stage"><span>{{ String(p.stage).padStart(2, '0') }}</span>{{ stageName(p.stage) }}<span class="ai-match">匹配度 {{ p.matchScore }}%</span></div>
                  <div class="project-title"><h3>{{ p.title }}</h3><span class="badge" :class="diffBadge(p.difficulty)">{{ p.difficulty }}</span></div>
                  <ul class="ai-reasons"><li v-for="(r, i) in p.reasons" :key="i">{{ r }}</li></ul>
                  <div v-if="p.skillGaps?.length" class="ai-gaps">待补齐：{{ p.skillGaps.join(' / ') }}</div>
                  <div class="project-next"><p>{{ p.nextAction }}</p><button class="secondary-button" @click="goProject(p.projectId)">查看项目 <ArrowUpRight :size="15" aria-hidden="true" /></button></div>
                </article>
              </div>
              <p v-if="!plan.recommendedProjects?.length" class="panel no-projects">暂时没有匹配的实战项目，可以调整目标后重新生成。</p>
              <p class="ai-meta"><component :is="plan.source === 'AI' ? Sparkles : SlidersHorizontal" :size="13" aria-hidden="true" /><span>{{ plan.source === 'AI' ? '由 AI 结合智能匹配生成' : '已使用智能匹配结果' }} · {{ formatTime(plan.generatedAt) }}{{ plan.cached ? ' · 已保存的路线' : '' }} · 仅供学习参考</span></p>
            </template>
          </div>
        </section>
      </div>
    </template>

    <!-- AI 能力测评 -->
    <el-dialog v-model="quizVisible" :title="quiz.step === 'result' ? '测评结果' : 'AI 能力测评'" width="min(640px, calc(100vw - 32px))" :close-on-click-modal="false" :close-on-press-escape="!submitting" :show-close="!submitting" @closed="resetQuiz">
      <!-- 选维度 -->
      <template v-if="quiz.step === 'pick'">
        <p class="assess-tip">选择一个技能维度，完成 6 道单选题，约需 1 分钟。测评结果将计入能力画像，帮助你了解当前水平。</p>
        <div class="dim-grid">
          <button v-for="(s, i) in data.skills" :key="s.skillName" class="dim" :class="{ on: quiz.skillName === s.skillName }" :aria-pressed="quiz.skillName === s.skillName" @click="quiz.skillName = s.skillName">
            <span class="dim-icon" :style="{ background: meta(s.skillName, i).bg }"><component :is="meta(s.skillName, i).icon" :size="16" :color="meta(s.skillName, i).color" aria-hidden="true" /></span>
            <span class="dim-text"><b>{{ s.skillName }}</b><small>当前 {{ s.score }} · {{ s.evidenceCount }} 次实证</small></span>
          </button>
        </div>
      </template>
      <!-- 出题中 -->
      <div v-else-if="quiz.step === 'loading'" class="quiz-loading">
        <AiLoadingStatus
          :phases="[`正在为「${quiz.skillName}」理解测评目标…`, '正在按当前能力配置题目难度…', '正在组织测评题目与选项…']"
          :hint="`当前能力 ${quiz.currentScore} 分，通常需要约 10 秒`"
        />
      </div>
      <!-- 答题 -->
      <template v-else-if="quiz.step === 'answer'">
        <div class="quiz-progress">
          <span>{{ quiz.skillName }} · 第 {{ quiz.index + 1 }} / {{ quiz.questions.length }} 题</span>
          <span class="badge badge-blue">{{ quiz.questions[quiz.index].difficulty || '基础' }}</span>
        </div>
        <div class="q-text">{{ quiz.questions[quiz.index].q }}</div>
        <div class="q-options">
          <button v-for="(opt, oi) in quiz.questions[quiz.index].options" :key="oi" class="q-opt" :class="{ on: quiz.answers[quiz.index] === oi }" :aria-pressed="quiz.answers[quiz.index] === oi" :disabled="submitting" @click="quiz.answers[quiz.index] = oi">
            <span class="q-letter">{{ 'ABCD'[oi] }}</span>{{ opt }}
          </button>
        </div>
        <div class="q-dots" aria-label="答题进度"><button v-for="(q, qi) in quiz.questions" :key="qi" :class="{ done: quiz.answers[qi] != null, cur: qi === quiz.index }" :aria-label="'第 ' + (qi + 1) + ' 题' + (quiz.answers[qi] != null ? '，已作答' : '，未作答')" :aria-current="qi === quiz.index ? 'step' : undefined" :disabled="submitting" @click="quiz.index = qi">{{ qi + 1 }}</button></div>
      </template>
      <!-- 结果 -->
      <template v-else-if="quiz.step === 'result'">
        <div class="quiz-result">
          <div class="qr-score" :class="quiz.result.score >= 80 ? 'good' : quiz.result.score >= 50 ? 'mid' : 'bad'"><b>{{ quiz.result.score }}</b><span>答对 {{ quiz.result.correct }} / {{ quiz.result.total }}</span></div>
          <div class="qr-text">
            <div class="qr-title">「{{ quiz.result.skillName }}」综合掌握度 {{ quiz.result.before }} → <b>{{ quiz.result.after }}</b>
              <span class="badge" :class="quiz.result.after > quiz.result.before ? 'badge-green' : quiz.result.after < quiz.result.before ? 'badge-red' : 'badge-gray'">{{ quiz.result.after > quiz.result.before ? '+' : '' }}{{ quiz.result.after - quiz.result.before }}</span>
            </div>
            <div class="muted">已作为一次 AI 测评实证计入画像;想再涨就去做相关项目,评审通过后权重更高。</div>
          </div>
        </div>
        <div class="qr-detail">
          <div v-for="d in quiz.result.detail" :key="d.index" class="qr-item" :class="{ ok: d.correct }">
            <div class="qr-q"><component :is="d.correct ? Check : X" class="qr-mark" :size="16" :aria-label="d.correct ? '正确' : '错误'" />{{ quiz.questions[d.index].q }}</div>
            <div class="qr-ans">正确答案:{{ 'ABCD'[d.answer] }}. {{ quiz.questions[d.index].options[d.answer] }}<span v-if="!d.correct && d.given != null" class="qr-given"> · 你选了 {{ 'ABCD'[d.given] }}</span></div>
            <div v-if="d.explain" class="qr-explain">{{ d.explain }}</div>
          </div>
        </div>
      </template>
      <template #footer>
        <template v-if="quiz.step === 'pick'">
          <el-button @click="quizVisible = false">取消</el-button>
          <el-button type="primary" :disabled="!quiz.skillName" @click="startQuiz">开始出题</el-button>
        </template>
        <template v-else-if="quiz.step === 'answer'">
          <el-button :disabled="quiz.index === 0 || submitting" @click="quiz.index--">上一题</el-button>
          <el-button v-if="quiz.index < quiz.questions.length - 1" type="primary" :disabled="quiz.answers[quiz.index] == null" @click="quiz.index++">下一题</el-button>
          <el-button v-else type="primary" :loading="submitting" :disabled="quiz.answers.some((a) => a == null)" @click="submitQuiz">交卷</el-button>
        </template>
        <template v-else-if="quiz.step === 'result'">
          <el-button @click="openQuiz(null)">再测一个维度</el-button>
          <el-button type="primary" @click="quizVisible = false">完成</el-button>
        </template>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import {
  Activity, ArrowRight, ArrowUpRight, BadgeCheck, BookOpen, Braces, ChartNoAxesCombined, Check,
  ChevronDown, ChevronLeft, ChevronRight, CircleAlert, CircleDashed, CircuitBoard, ClipboardCheck,
  Clock3, Code, Compass, Cpu, Flag, History, Info, Layers, Lightbulb, LoaderCircle, Radio, Route,
  Search, SlidersHorizontal, Sparkles, Target, Wrench, X, Zap
} from 'lucide-vue-next'
import { fetchAiPlan, fetchSkills, generateAiPlan, skillQuizStart, skillQuizSubmit } from '../../api'
import AiLoadingStatus from '../../components/ai/AiLoadingStatus.vue'

const router = useRouter()
const data = reactive({ skills: [], overall: 0, evidenceTotal: 0, suggestions: [], history: [], events: [] })
const loading = ref(true)
const loadError = ref(false)
const activeTab = ref('overview')
const tabs = [
  { key: 'overview', label: '能力概览', icon: ChartNoAxesCombined },
  { key: 'skills', label: '技能明细', icon: Layers },
  { key: 'records', label: '成长记录', icon: History },
  { key: 'plan', label: '学习规划', icon: Route }
]
const onTabKey = (event, index) => {
  let next = index
  if (event.key === 'ArrowRight') next = (index + 1) % tabs.length
  else if (event.key === 'ArrowLeft') next = (index + tabs.length - 1) % tabs.length
  else if (event.key === 'Home') next = 0
  else if (event.key === 'End') next = tabs.length - 1
  else return
  event.preventDefault()
  activeTab.value = tabs[next].key
  event.currentTarget.parentElement.children[next].focus()
}
const assessedCount = computed(() => data.skills.filter(s => s.evidenceCount > 0).length)
const prioritySkills = computed(() => [...data.skills].sort((a, b) =>
  Number(a.evidenceCount > 0) - Number(b.evidenceCount > 0) || a.score - b.score).slice(0, 3))
const nextSkill = computed(() => prioritySkills.value[0])
const skillSearch = ref('')
const skillFilter = ref('all')
const skillFilters = computed(() => [
  { key: 'all', label: '全部技能', count: data.skills.length },
  { key: 'pending', label: '待校准', count: data.skills.length - assessedCount.value },
  { key: 'assessed', label: '有实证', count: assessedCount.value }
])
const visibleSkills = computed(() => data.skills.filter(s =>
  s.skillName.toLowerCase().includes(skillSearch.value.trim().toLowerCase()) &&
  (skillFilter.value === 'all' || (skillFilter.value === 'assessed' ? s.evidenceCount > 0 : !s.evidenceCount))))

const eventSkill = ref('')
const eventSource = ref('')
const eventPage = ref(1)
const eventPageSize = 8
const eventSkillNames = computed(() => [...new Set([...data.skills.map(s => s.skillName), ...data.events.map(e => e.skillName)])])
const eventSources = computed(() => [...new Set(data.events.map(e => e.source))])
const filteredEvents = computed(() => data.events.filter(e => (!eventSkill.value || e.skillName === eventSkill.value) && (!eventSource.value || e.source === eventSource.value)))
const eventPageCount = computed(() => Math.max(1, Math.ceil(filteredEvents.value.length / eventPageSize)))
const pagedEvents = computed(() => filteredEvents.value.slice((eventPage.value - 1) * eventPageSize, eventPage.value * eventPageSize))
watch([eventSkill, eventSource, () => data.events], () => { eventPage.value = 1 })
const showSkillRecords = name => {
  eventSkill.value = name
  eventSource.value = ''
  activeTab.value = 'records'
}

const knownMeta = {
  '嵌入式开发': { icon: Cpu, color: '#4263d7', bg: '#eef2ff' },
  'PCB设计': { icon: CircuitBoard, color: '#8054c7', bg: '#f4effc' },
  '编程能力': { icon: Code, color: '#26866e', bg: '#eaf7f0' },
  '通信技术': { icon: Radio, color: '#18859b', bg: '#eaf7fa' },
  '信号处理': { icon: Activity, color: '#b88014', bg: '#fff7e7' },
  '硬件调试': { icon: Wrench, color: '#bd6d38', bg: '#fff1e8' }
}
const palette = [
  { icon: Layers, color: '#b15c87', bg: '#fcf0f6' },
  { icon: Braces, color: '#26866e', bg: '#eaf7f0' },
  { icon: BookOpen, color: '#62728c', bg: '#eff3f8' },
  { icon: Target, color: '#6553c3', bg: '#f1eefa' }
]
const meta = (name, i) => knownMeta[name] || palette[i % palette.length]
const skillMeta = s => meta(s.skillName, data.skills.indexOf(s))
const skillStyle = s => ({ background: skillMeta(s).bg, color: skillMeta(s).color })
const levelText = v => v >= 80 ? '精通' : v >= 60 ? '熟练' : v >= 40 ? '进阶' : '入门'
const lastEvent = name => data.events.find(e => e.skillName === name)
const sourceText = src => ({ PROJECT: '项目实证', QUIZ: 'AI 测评', SELF: '自评（旧）', INIT: '初始' }[src] || src)
const deltaClass = e => e.afterScore > e.beforeScore ? 'up' : e.afterScore < e.beforeScore ? 'down' : ''
const formatTime = v => String(v || '').replace('T', ' ').slice(0, 16)

// Keep the chart instances tied to their tab's DOM, including container resizes.
const radarRef = ref(null)
const lineRef = ref(null)
const radarSkills = computed(() => data.skills.slice(0, 8))
const radarDescription = computed(() => '能力雷达：' + radarSkills.value.map(s => s.skillName + ' ' + s.score + '分').join('，'))
const historyDescription = computed(() => '综合评分变化：' + data.history.map(h => formatTime(h.time) + '，' + h.overall + '分').join('；'))
const radarOptions = () => ({
  animationDuration: 450,
  tooltip: { trigger: 'item', renderMode: 'richText', confine: true },
  radar: {
    indicator: radarSkills.value.map(s => ({ name: s.skillName, max: 100 })),
    center: ['50%', '50%'], radius: Math.max(40, Math.min(95, (radarRef.value?.clientWidth || 320) / 2 - 70)), splitNumber: 5,
    axisNameGap: 8,
    axisName: { color: '#68758a', fontSize: 11, formatter: name => name.length > 5 ? name.slice(0, 4) + '…' : name },
    axisLine: { lineStyle: { color: '#e3e9f4' } }, splitLine: { lineStyle: { color: '#e3e9f4' } },
    splitArea: { areaStyle: { color: ['#fff', '#f9faff'] } }
  },
  series: [{
    type: 'radar', symbol: 'circle', symbolSize: 5,
    data: [{ value: radarSkills.value.map(s => s.score), name: data.evidenceTotal ? '综合掌握度' : '待校准参考分' }],
    areaStyle: { color: 'rgba(99,102,241,.19)' }, lineStyle: { color: '#7373e6', width: 2 },
    itemStyle: { color: '#7373e6', borderColor: '#fff', borderWidth: 1.5 }
  }]
})
const lineOptions = () => ({
  grid: { left: 38, right: 20, top: 24, bottom: 30 },
  tooltip: {
    trigger: 'axis', renderMode: 'richText', confine: true,
    formatter: params => {
      const h = data.history[params[0].dataIndex]
      return formatTime(h.time) + '\n综合评分 ' + h.overall + ' · ' + sourceText(h.source)
    }
  },
  xAxis: { type: 'category', data: data.history.map(h => formatTime(h.time).slice(5, 10)), boundaryGap: false, axisLabel: { color: '#8590a1' }, axisLine: { lineStyle: { color: '#e9edf4' } }, axisTick: { show: false } },
  yAxis: { type: 'value', max: 100, axisLabel: { color: '#8590a1' }, splitLine: { lineStyle: { color: '#edf0f6', type: 'dashed' } } },
  series: [{
    type: 'line', data: data.history.map(h => h.overall), smooth: true, symbolSize: 7,
    lineStyle: { width: 3, color: '#7373e6' }, itemStyle: { color: '#7373e6' },
    areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(115,115,230,.18)' }, { offset: 1, color: 'rgba(115,115,230,0)' }]) }
  }]
})
const bindChart = (element, options) => {
  let chart = null
  let observer = null
  const dispose = () => { observer?.disconnect(); chart?.dispose(); observer = null; chart = null }
  const render = () => chart?.setOption(options(), true)
  watch(element, el => {
    dispose()
    if (!el) return
    chart = echarts.init(el, null, { renderer: 'svg' })
    render()
    observer = new ResizeObserver(() => { chart?.resize(); render() })
    observer.observe(el)
  }, { flush: 'post' })
  onBeforeUnmount(dispose)
  return render
}
const renderRadar = bindChart(radarRef, radarOptions)
const renderLine = bindChart(lineRef, lineOptions)
const apply = async res => {
  Object.assign(data, res)
  await nextTick()
  renderRadar()
  renderLine()
}
let mounted = true
const load = async () => {
  loading.value = true
  loadError.value = false
  try { const res = await fetchSkills(); if (mounted) await apply(res) }
  catch { if (mounted) loadError.value = true }
  finally { if (mounted) loading.value = false }
}

// AI learning plan: invalidate stale responses when a new assessment changes the profile.
const plan = ref(null)
const aiGoal = ref('')
const aiHours = ref(6)
const aiLoading = ref(false)
const planLoading = ref(true)
let planVersion = 0
const goalChips = ['嵌入式开发', 'PCB设计', '物联网作品', '边缘AI应用', '信号处理', '备战电赛']
const addGoalChip = k => {
  if (!aiGoal.value.includes(k)) aiGoal.value = (aiGoal.value ? aiGoal.value + '、' + k : '想提升' + k).slice(0, 100)
}
const stageName = s => s === 1 ? '基础补强' : s === 2 ? '综合实践' : '挑战提升'
const diffBadge = d => d === '入门' ? 'badge-green' : d === '进阶' ? 'badge-purple' : 'badge-red'
const goProject = id => router.push('/app/projects/' + id)
const loadPlan = async () => {
  const version = ++planVersion
  try { const result = await fetchAiPlan(); if (mounted && version === planVersion) plan.value = result }
  catch { /* The API interceptor reports request failures. */ }
  finally { if (mounted) planLoading.value = false }
}
const genPlan = async () => {
  if (aiLoading.value || planLoading.value) return
  const version = ++planVersion
  aiLoading.value = true
  try {
    const result = await generateAiPlan({ goal: aiGoal.value.trim() || undefined, weeklyHours: aiHours.value })
    if (!mounted || version !== planVersion) return
    plan.value = result
    if (result?.source === 'RULE_FALLBACK') ElMessage.info('AI 服务未启用或繁忙，已使用智能匹配结果')
  } catch { /* Already reported by the API interceptor. */ }
  finally { if (mounted && version === planVersion) aiLoading.value = false }
}

const submitting = ref(false)
const quizVisible = ref(false)
const quiz = reactive({ step: 'pick', skillName: '', quizId: '', questions: [], answers: [], index: 0, currentScore: 0, result: null })
let quizVersion = 0
const resetQuiz = () => {
  quizVersion++
  Object.assign(quiz, { step: 'pick', skillName: '', quizId: '', questions: [], answers: [], index: 0, currentScore: 0, result: null })
}
const openQuiz = skillName => {
  resetQuiz()
  quiz.skillName = skillName || nextSkill.value?.skillName || ''
  quizVisible.value = true
}
const startQuiz = async () => {
  const version = ++quizVersion
  quiz.step = 'loading'
  quiz.currentScore = data.skills.find(s => s.skillName === quiz.skillName)?.score || 0
  try {
    const r = await skillQuizStart(quiz.skillName)
    if (!mounted || version !== quizVersion || !quizVisible.value) return
    if (!r.questions?.length) { ElMessage.error('暂时未能生成题目，请重试'); quiz.step = 'pick'; return }
    Object.assign(quiz, { quizId: r.quizId, questions: r.questions, answers: r.questions.map(() => null), index: 0, step: 'answer' })
  } catch { if (mounted && version === quizVersion) quiz.step = 'pick' }
}
const submitQuiz = async () => {
  if (submitting.value) return
  submitting.value = true
  try {
    const r = await skillQuizSubmit(quiz.quizId, quiz.answers)
    if (!mounted) return
    quiz.result = r
    quiz.step = 'result'
    planVersion++
    plan.value = null
    aiLoading.value = false
    await apply(r.summary)
  } catch { /* Already reported by the API interceptor. */ }
  finally { if (mounted) submitting.value = false }
}
onMounted(() => { load(); loadPlan() })
onBeforeUnmount(() => { mounted = false; quizVersion++; planVersion++ })
</script>

<style scoped>
.skills-page { max-width: 1320px; margin: 0 auto; color: #26334a; --accent: #615bd7; --line: #e9edf4; }
.skills-page h2, .skills-page h3, .skills-page h4, .skills-page p { margin: 0; }
.skills-page button, .skills-page input, .skills-page select, .skills-page textarea { font: inherit; }
.skills-page button { cursor: pointer; }
.skills-page button:disabled { cursor: not-allowed; opacity: .45; }
.skills-page button:focus-visible, .skills-page summary:focus-visible, .tab-panel:focus-visible { outline: 3px solid #bbb5f3; outline-offset: 4px; }
.skills-page svg { flex-shrink: 0; }
.page-heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin: 4px 0 26px; }
.heading-copy { display: flex; align-items: center; gap: 13px; }
.heading-icon { width: 46px; height: 46px; display: grid; place-items: center; background: #eeecfc; color: var(--accent); border: 1px solid #e4e0fa; border-radius: 14px; }
.page-heading h2 { font-size: 25px; letter-spacing: -.6px; line-height: 1.4; }
.page-heading p { margin-top: 5px; color: #7b8597; font-size: 13px; }
.primary-button, .secondary-button, .text-button, .icon-button { display: inline-flex; align-items: center; justify-content: center; gap: 8px; border: 0; font-size: 13px !important; font-weight: 600 !important; transition: background .18s, box-shadow .18s; }
.primary-button { min-height: 40px; padding: 11px 18px; border-radius: 10px; background: #655cdf; color: #fff; box-shadow: 0 4px 10px #655cdf1c; }
.primary-button:hover:not(:disabled) { background: #554acb; }
.secondary-button { padding: 9px 12px; border: 1px solid #e0e1ef; border-radius: 8px; background: #fff; color: #615bd0; }
.secondary-button:hover:not(:disabled) { background: #f4f3ff; border-color: #c5c0ef; }
.text-button { background: none; color: var(--accent); padding: 4px 0; white-space: nowrap; }
.section-tabs { display: flex; gap: 8px; border-bottom: 1px solid #e5e8f0; margin-bottom: 25px; }
.section-tabs > button { display: inline-flex; align-items: center; gap: 8px; padding: 13px 17px 16px; background: none; border: 0; border-bottom: 2px solid transparent; margin-bottom: -1px; color: #7b8597; font-size: 14px; white-space: nowrap; }
.section-tabs > button:hover { color: var(--accent); }
.section-tabs > button.active { color: var(--accent); border-bottom-color: var(--accent); font-weight: 650; }
.tab-count { padding: 1px 6px; border-radius: 5px; background: #eef0f5; font-size: 11px; }
.tab-panel { outline: none; }
.panel { background: #fff; border: 1px solid var(--line); border-radius: 16px; padding: 24px; box-shadow: 0 3px 10px #27355102; min-width: 0; }
.loading-panel { min-height: 380px; }
.section-heading { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.section-heading h3 { font-size: 16px; font-weight: 650; line-height: 1.5; }
.section-heading p { font-size: 12px; color: #7f899a; margin-top: 5px; line-height: 1.7; }
.status-tag { display: inline-flex; gap: 5px; align-items: center; background: #f4f5f8; color: #7b8495; font-size: 11px; padding: 5px 8px; border-radius: 6px; white-space: nowrap; }
.status-tag.verified { background: #ecf6f2; color: #478b72; }
.overview-grid { display: grid; grid-template-columns: minmax(0, 1.65fr) minmax(290px, 1fr); gap: 20px; }
.profile-body { display: grid; grid-template-columns: 145px minmax(0, 1fr); align-items: center; gap: 12px; margin-top: 10px; min-height: 286px; }
.metric-label { color: #758096; font-size: 12px; }
.score-number { font-size: 49px; line-height: 1.2; font-weight: 700; color: #35415b; letter-spacing: -2px; margin: 9px 0 7px; font-variant-numeric: tabular-nums; }
.score-number > span { font-size: 13px; color: #8993a5; letter-spacing: 0; margin-left: 8px; font-weight: 400; }
.score-caption { font-size: 11px; color: #8a94a6; }
.profile-metrics { border-top: 1px solid var(--line); margin-top: 24px; padding-top: 16px; display: grid; gap: 15px; font-size: 11px; }
.profile-metrics > div { display: flex; align-items: center; justify-content: space-between; gap: 4px; color: #7f899a; }
.profile-metrics b { font-size: 17px; color: #566279; font-variant-numeric: tabular-nums; }
.profile-metrics small { font-size: 11px; font-weight: 400; color: #8993a5; }
.radar-chart { width: 100%; height: 286px; min-width: 0; }
.profile-foot { display: flex; align-items: flex-start; gap: 7px; border-top: 1px solid #eef0f6; padding-top: 14px; color: #818da0; font-size: 11px; line-height: 1.8; }
.profile-foot svg { margin-top: 3px; }
.next-panel { background: linear-gradient(125deg, #f2f0ff, #f8f9ff 82%); border: 1px solid #e6e2f8; border-radius: 16px; display: flex; flex-direction: column; overflow: hidden; min-width: 0; }
.next-eyebrow { color: #7870b0; font-size: 12px; display: flex; align-items: center; gap: 7px; padding: 22px 24px 0; }
.next-main { padding: 26px 24px 22px; flex: 1; }
.next-label { font-size: 11px; color: #857c9f; }
.next-main h3 { font-size: 24px; letter-spacing: -.5px; margin-top: 7px; overflow-wrap: anywhere; color: #3d365f; }
.next-main p { font-size: 12px; line-height: 1.9; color: #807695; margin: 10px 0 20px; max-width: 290px; }
.quiz-duration { display: inline-flex; align-items: center; gap: 5px; font-size: 11px; color: #8980a2; margin: 12px 0 0; width: 100%; }
.plan-shortcut { display: flex; align-items: center; gap: 11px; text-align: left; border: 0; border-top: 1px solid #e9e5f7; background: #ffffff70; width: 100%; padding: 17px 24px; color: #7b759e; }
.plan-shortcut:hover { background: #fff; }
.shortcut-icon { width: 34px; height: 34px; background: #eeebfa; border-radius: 10px; display: grid; place-items: center; color: #8277bc; }
.plan-shortcut > span:nth-child(2) { flex: 1; min-width: 0; }
.plan-shortcut b, .plan-shortcut small { display: block; }
.plan-shortcut b { font-size: 12px; font-weight: 500; color: #696082; }
.plan-shortcut small { font-size: 11px; margin-top: 5px; color: #8f859f; }
.priority-section { margin-top: 27px; }
.priority-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; margin-top: 16px; }
.priority-card { border: 1px solid var(--line); border-radius: 13px; background: #fff; padding: 18px; min-width: 0; }
.skill-card-top { display: flex; align-items: center; gap: 11px; }
.skill-card-top > div { min-width: 0; flex: 1; }
.skill-card-top h3, .skill-card-top h4 { font-size: 14px; font-weight: 650; overflow-wrap: anywhere; }
.skill-icon { display: grid; place-items: center; width: 38px; height: 38px; border-radius: 11px; flex-shrink: 0; }
.small-muted { color: #8a94a6; font-size: 11px; display: block; margin-top: 4px; }
.compact-score { margin-left: auto; font-size: 20px; color: #4c5871; font-variant-numeric: tabular-nums; white-space: nowrap; }
.compact-score small, .skill-score-row small { font-size: 11px; font-weight: 400; margin-left: 2px; color: #8a94a6; }
.skill-bar { height: 5px; border-radius: 5px; overflow: hidden; background: #eff1f7; }
.skill-bar > span { display: block; height: 100%; max-width: 100%; border-radius: inherit; background: #9a95e9; }
.priority-card .skill-bar { margin: 19px 0 13px; }
.priority-foot { display: flex; justify-content: space-between; align-items: center; gap: 8px; font-size: 11px; color: #8993a5; }
.priority-foot .text-button { font-size: 11px !important; }
.detail-heading { margin-bottom: 18px; }
.filter-toolbar { display: flex; justify-content: space-between; align-items: center; gap: 14px; margin-bottom: 20px; }
.filter-pills { display: flex; gap: 5px; flex-wrap: wrap; }
.filter-pills button { border: 1px solid transparent; background: none; color: #7f899a; border-radius: 8px; padding: 8px 11px; font-size: 12px; }
.filter-pills button.selected { background: #eeecfc; color: var(--accent); border-color: #e7e3f9; }
.filter-pills span { font-size: 10px; margin-left: 3px; opacity: .8; }
.search-field { display: flex; align-items: center; gap: 8px; padding: 9px 11px; background: #fff; border: 1px solid #e3e7ef; border-radius: 8px; color: #8a94a6; width: 225px; }
.search-field:focus-within { border-color: #aaa2e7; box-shadow: 0 0 0 2px #f0edfc; }
.search-field input { font-size: 12px; color: #546077; border: 0; outline: none; width: 100%; min-width: 0; background: transparent; }
.search-field input::placeholder { color: #929aaa; }
.search-field button { display: grid; place-items: center; background: none; border: none; color: #8993a5; padding: 0; }
.skill-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 18px; align-items: start; }
.skill-card { padding: 21px; }
.skill-card h3 { flex: 1; }
.skill-description { font-size: 12px; line-height: 1.9; color: #818da0; margin: 16px 0 19px !important; min-height: 46px; }
.skill-score-row { display: flex; justify-content: space-between; align-items: baseline; color: #818da0; font-size: 11px; margin-bottom: 9px; }
.skill-score-row b { font-size: 25px; color: #4f5b74; font-variant-numeric: tabular-nums; }
.skill-evidence { display: flex; align-items: center; gap: 6px; color: #8993a5; font-size: 11px; margin-top: 13px; }
.skill-card-actions { display: flex; justify-content: space-between; gap: 10px; border-top: 1px solid #f0f1f6; padding-top: 15px; margin-top: 18px; }
.skill-card-actions .text-button, .skill-card-actions .secondary-button { font-size: 11px !important; }
.last-evidence { font-size: 11px; color: #818da0; margin-top: 14px; line-height: 1.8; }
.last-evidence summary { cursor: pointer; }
.last-evidence p { padding: 7px 0 0; color: #6f7a8e; }
.empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; gap: 12px; color: #909aaa; min-height: 330px; }
.empty-state h3 { color: #68738a; font-size: 17px; }
.empty-state h4 { color: #7f899b; font-size: 14px; font-weight: 500; }
.empty-state p { font-size: 12px; line-height: 1.9; max-width: 420px; }
.compact-empty { min-height: 190px; gap: 10px; }
.records-section { display: grid; gap: 20px; }
.chart-legend { display: flex; gap: 7px; align-items: center; color: #818da0; font-size: 11px; white-space: nowrap; }
.chart-legend i { width: 7px; height: 7px; border-radius: 50%; background: #8982db; }
.history-chart { width: 100%; height: 230px; margin-top: 14px; }
.record-filters { display: flex; gap: 9px; flex-wrap: wrap; }
.record-filters select, .plan-form select { background: #fff; border: 1px solid #e0e5ee; border-radius: 8px; padding: 8px 10px; color: #748096; font-size: 12px; max-width: 100%; }
.event-list { padding: 0; margin: 16px 0 0; list-style: none; }
.event-item { display: flex; align-items: flex-start; gap: 13px; padding: 18px 0; border-top: 1px solid #eff1f6; }
.event-icon { width: 33px; height: 33px; display: grid; place-items: center; border-radius: 10px; background: #f1effc; color: #9186c9; flex-shrink: 0; }
.event-icon.project { color: #589a83; background: #eff8f4; }
.event-copy { flex: 1; min-width: 0; }
.event-title { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; font-size: 13px; }
.event-title > span { color: #8993a5; font-size: 11px; }
.event-copy p { font-size: 12px; line-height: 1.8; color: #818da0; margin: 6px 0; overflow-wrap: anywhere; }
.event-copy time { font-size: 10px; color: #8993a5; }
.event-score { display: flex; align-items: center; gap: 8px; font-size: 15px; font-variant-numeric: tabular-nums; padding-top: 5px; }
.event-score > span { color: #929bad; font-size: 12px; }
.event-score.up { color: #41947c; }
.event-score.down { color: #c57566; }
.pagination { display: flex; gap: 14px; align-items: center; justify-content: flex-end; border-top: 1px solid var(--line); padding-top: 16px; font-size: 11px; color: #818da0; }
.pagination > span:first-child { margin-right: auto; }
.icon-button { border: 1px solid var(--line); width: 30px; height: 30px; border-radius: 7px; background: #fff; color: #7a839b; }
.plan-layout { display: grid; grid-template-columns: 310px minmax(0, 1fr); gap: 22px; align-items: start; }
.plan-sidebar { display: grid; gap: 16px; }
.plan-form { padding: 23px; }
.plan-icon { width: 44px; height: 44px; display: grid; place-items: center; border-radius: 13px; color: #8c7bc7; background: #f1edfc; margin-bottom: 18px; }
.plan-form h3 { font-size: 18px; }
.plan-form > p { color: #818da0; font-size: 12px; line-height: 1.9; margin: 9px 0 24px; }
.field-label { display: flex; align-items: center; justify-content: space-between; color: #667188; font-size: 12px; margin-bottom: 10px; }
.field-label span { color: #929bad; font-size: 10px; }
.plan-form textarea { width: 100%; resize: vertical; min-height: 80px; border: 1px solid #e0e5ee; background: #fcfcfe; color: #566177; padding: 10px 12px; font-size: 12px; line-height: 1.8; border-radius: 9px; }
.plan-form textarea::placeholder { color: #929bad; }
.plan-form textarea:focus, .plan-form select:focus, .record-filters select:focus { outline: 2px solid #d8d2f3; outline-offset: 2px; }
.goal-chips { display: flex; gap: 7px 6px; flex-wrap: wrap; margin: 11px 0 23px; }
.goal-chips button { border: 1px solid #edf0f5; background: #f9fafe; color: #7f899a; font-size: 10px; padding: 5px 8px; border-radius: 6px; }
.goal-chips button:hover, .goal-chips button.selected { color: #8677bd; background: #f2effc; border-color: #e6e0f8; }
.plan-form select, .generate-button { width: 100%; }
.generate-button { margin-top: 23px; }
.suggestions { padding: 18px; }
.suggestions summary { display: flex; align-items: center; gap: 8px; cursor: pointer; list-style: none; font-size: 12px; color: #7f789b; }
.suggestions summary::-webkit-details-marker { display: none; }
.suggestions summary > span { margin-left: auto; color: #9690a9; font-size: 10px; }
.suggestions[open] summary > svg:last-child { transform: rotate(180deg); }
.suggestions ol { margin: 18px 0 0; padding-left: 18px; color: #857b98; font-size: 12px; line-height: 1.9; }
.suggestions li + li { margin-top: 12px; }
.plan-content { display: grid; gap: 18px; min-width: 0; }
.plan-empty { min-height: 490px; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; color: #a6a0bf; padding: 38px 26px; background: radial-gradient(ellipse at 50% 30%, #f9f7ff, #fff 70%); }
.route-illustration { display: flex; align-items: center; gap: 12px; margin-bottom: 34px; }
.route-illustration > span { display: grid; place-items: center; width: 58px; height: 58px; border: 1px solid #e4e0f5; border-radius: 16px; background: #fff; color: #a195cb; box-shadow: 0 5px 14px #8d7ac009; }
.route-illustration > span:nth-of-type(2) { color: #8672bc; background: #f2edfc; transform: translateY(-9px); }
.route-illustration i { width: 30px; border-top: 2px dashed #ddd7f0; }
.plan-empty h3 { color: #68627f; font-size: 19px; margin-top: 10px; }
.plan-empty > p { color: #8a809b; font-size: 12px; line-height: 1.9; margin-top: 13px; }
.plan-steps { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); width: 100%; max-width: 490px; margin: 34px 0; }
.plan-steps > div + div { border-left: 1px solid #efedf6; }
.plan-steps b { color: #c1b6d4; font-size: 15px; font-weight: 400; }
.plan-steps h4 { font-size: 12px; color: #84788f; margin: 9px 0; font-weight: 500; }
.plan-steps span, .plan-empty-note { font-size: 10px; color: #9689a3; }
.plan-summary h3 { display: flex; gap: 8px; align-items: center; }
.plan-summary > p { color: #858096; font-size: 13px; line-height: 1.9; margin-top: 16px; }
.focus-list { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px; margin-top: 18px; }
.focus-list > div { background: #f8f7fd; padding: 13px; border-radius: 10px; font-size: 12px; }
.focus-list > div > div { display: flex; justify-content: space-between; gap: 10px; flex-wrap: wrap; }
.focus-list b { font-weight: 500; color: #766d8d; }
.focus-list span { display: inline-flex; align-items: center; gap: 4px; color: #9180b1; font-size: 11px; }
.focus-list p { font-size: 11px; line-height: 1.8; color: #8d809b; margin-top: 8px; }
.ai-stages { display: grid; gap: 16px; }
.project-stage { display: flex; gap: 9px; align-items: center; color: #8b76aa; font-size: 11px; }
.project-stage > span:first-child { background: #f3eefb; padding: 5px 6px; border-radius: 6px; font-size: 10px; }
.ai-match { margin-left: auto; color: #86899e; font-size: 10px; }
.project-title { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; margin-top: 16px; }
.project-title h3 { font-size: 16px; }
.ai-reasons { font-size: 12px; line-height: 1.9; color: #818da0; padding-left: 17px; margin: 12px 0; }
.ai-gaps { font-size: 11px; color: #9d8054; margin-bottom: 12px; }
.project-next { display: flex; gap: 15px; justify-content: space-between; align-items: center; border-top: 1px solid #efedf5; padding-top: 14px; }
.project-next p { font-size: 12px; color: #818da0; line-height: 1.8; }
.project-next button { white-space: nowrap; flex-shrink: 0; font-size: 11px !important; }
.ai-meta { display: flex; gap: 6px; align-items: flex-start; color: #9090a3; font-size: 10px; line-height: 1.8; }
.ai-meta > svg { margin-top: 2px; }
.no-projects { color: #818da0; font-size: 12px; line-height: 1.9; }
.spinning { animation: spin 1s linear infinite; }
.assess-tip { font-size: 13px; color: var(--text-secondary); margin: 0 0 16px; line-height: 1.7; }
.muted { font-size: 12px; color: var(--text-secondary); }
.dim-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.dim { display: flex; align-items: center; gap: 10px; text-align: left; padding: 10px 12px; border-radius: 12px; border: 1px solid var(--border); background: #fff; cursor: pointer; }
.dim.on { border-color: #6366f1; box-shadow: 0 0 0 3px #e0e7ff; background: #f5f3ff; }
.dim-icon { width: 32px; height: 32px; border-radius: 9px; display: grid; place-items: center; flex-shrink: 0; }
.dim-text { display: flex; flex-direction: column; }
.dim-text b { font-size: 14px; }
.dim-text small { font-size: 11.5px; color: var(--text-secondary); }
.quiz-loading { display: flex; align-items: center; gap: 16px; padding: 30px 10px; }
.spinner { width: 38px; height: 38px; border-radius: 50%; border: 3px solid #e0e7ff; border-top-color: #4f46e5; animation: spin 1s linear infinite; flex-shrink: 0; }
@keyframes spin { to { transform: rotate(360deg); } }
.quiz-progress { display: flex; justify-content: space-between; align-items: center; font-size: 13px; color: var(--text-secondary); margin-bottom: 12px; }
.q-text { font-size: 16px; font-weight: 700; line-height: 1.6; margin-bottom: 14px; }
.q-options { display: flex; flex-direction: column; gap: 8px; }
.q-opt { display: flex; align-items: center; gap: 12px; text-align: left; padding: 12px 14px; border-radius: 12px; border: 1px solid var(--border); background: #fff; cursor: pointer; font-size: 14px; line-height: 1.5; transition: all .15s; }
.q-opt:hover { border-color: #a5b4fc; background: #fafaff; }
.q-opt.on { border-color: #6366f1; background: #eef2ff; box-shadow: 0 0 0 3px #e0e7ff; }
.q-letter { width: 26px; height: 26px; border-radius: 50%; background: #f3f4f6; display: grid; place-items: center; font-weight: 700; font-size: 12px; flex-shrink: 0; }
.q-opt.on .q-letter { background: #6366f1; color: #fff; }
.q-dots { display: flex; gap: 6px; justify-content: center; margin-top: 16px; }
.q-dots button { width: 28px; height: 28px; border: 0; border-radius: 8px; background: #f0f1f6; color: #8b93a5; font-size: 11px; cursor: pointer; }
.q-dots button.done { background: #e8e5fb; color: #7c70b6; } .q-dots button.cur { background: #6b61d8; color: #fff; }
.quiz-result { display: flex; gap: 16px; align-items: center; margin-bottom: 14px; }
.qr-score { width: 96px; height: 96px; border-radius: 20px; display: flex; flex-direction: column; align-items: center; justify-content: center; color: #fff; flex-shrink: 0; }
.qr-score b { font-size: 34px; line-height: 1; } .qr-score span { font-size: 11px; opacity: .9; margin-top: 4px; }
.qr-score.good { background: linear-gradient(135deg, #22c55e, #15803d); } .qr-score.mid { background: linear-gradient(135deg, #f59e0b, #b45309); } .qr-score.bad { background: linear-gradient(135deg, #ef4444, #991b1b); }
.qr-title { font-size: 15px; font-weight: 700; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.qr-title b { color: #4f46e5; font-size: 18px; }
.qr-detail { display: flex; flex-direction: column; gap: 8px; max-height: 320px; overflow: auto; }
.qr-item { padding: 10px 12px; border-radius: 10px; background: #fef2f2; border: 1px solid #fecaca; }
.qr-item.ok { background: #f0fdf4; border-color: #bbf7d0; }
.qr-q { font-size: 13.5px; font-weight: 600; display: flex; gap: 8px; }
.qr-mark { font-weight: 800; color: #dc2626; } .qr-item.ok .qr-mark { color: #16a34a; }
.qr-ans { font-size: 12.5px; color: #374151; margin-top: 4px; }
.qr-given { color: #dc2626; }
.qr-explain { font-size: 12px; color: var(--text-secondary); margin-top: 4px; line-height: 1.6; }

@media (max-width: 1200px) {
  .overview-grid { grid-template-columns: minmax(0, 1.5fr) minmax(265px, 1fr); gap: 16px; }
  .profile-body { grid-template-columns: 110px minmax(0, 1fr); gap: 0; }
  .score-number { font-size: 40px; }
  .score-number > span { margin-left: 4px; font-size: 11px; }
  .profile-panel { padding: 20px; }
  .priority-grid { gap: 12px; }
  .priority-card { padding: 15px; }
  .skill-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 1000px) {
  .overview-grid { grid-template-columns: 1fr; }
  .profile-body { grid-template-columns: 160px minmax(0, 1fr); }
  .next-main { padding-top: 18px; }
  .next-main p { max-width: none; }
  .priority-grid { grid-template-columns: 1fr; }
  .priority-card .skill-bar { margin-top: 14px; }
  .plan-layout { grid-template-columns: 260px minmax(0, 1fr); gap: 16px; }
  .plan-empty { padding: 25px 18px; }
  .route-illustration { gap: 7px; }
  .route-illustration i { width: 16px; }
  .route-illustration > span { width: 44px; height: 44px; border-radius: 12px; }
  .plan-steps span { display: none; }
  .records-heading { align-items: flex-start; flex-direction: column; }
}
@media (max-width: 600px) {
  .page-heading { align-items: flex-start; gap: 16px; flex-direction: column; margin: 4px 0 20px; }
  .page-heading h2 { font-size: 22px; }
  .page-heading > .primary-button { width: 100%; }
  .section-tabs { gap: 0; justify-content: space-between; margin-bottom: 18px; }
  .section-tabs > button { font-size: 12px; padding: 12px 3px; gap: 5px; }
  .section-tabs > button > svg { width: 14px; }
  .tab-count { display: none; }
  .panel { padding: 18px; border-radius: 13px; }
  .section-heading { gap: 9px; }
  .section-heading h3 { font-size: 15px; }
  .section-heading p { font-size: 11px; }
  .status-tag { font-size: 10px; padding: 4px 6px; }
  .profile-body { grid-template-columns: 1fr; }
  .profile-score { display: grid; grid-template-columns: 1fr 1fr; margin: 22px 0 0; column-gap: 20px; }
  .metric-label, .score-number, .score-caption { grid-column: 1; }
  .score-number { margin: 5px 0; }
  .profile-metrics { grid-column: 2; grid-row: 1 / 4; border-top: 0; border-left: 1px solid var(--line); margin: 0; padding: 8px 0 8px 18px; align-content: center; }
  .radar-chart { height: 255px; }
  .next-main h3 { font-size: 23px; }
  .priority-grid, .skill-grid, .plan-layout { grid-template-columns: 1fr; }
  .priority-section { margin-top: 22px; }
  .priority-section .section-heading { align-items: flex-start; }
  .priority-section .text-button { font-size: 11px !important; }
  .filter-toolbar { flex-direction: column-reverse; align-items: stretch; }
  .search-field { width: 100%; }
  .filter-pills { justify-content: space-between; gap: 0; }
  .skill-description { min-height: 0; }
  .plan-empty { min-height: 350px; }
  .plan-empty h3 { font-size: 17px; }
  .plan-steps span { display: block; font-size: 9px; }
  .plan-steps { margin: 27px 0; }
  .project-next { flex-wrap: wrap; }
  .event-item { gap: 9px; }
  .event-score { gap: 4px; }
  .chart-legend { font-size: 10px; }
  .record-filters { width: 100%; }
  .record-filters select { flex: 1; min-width: 0; width: 50%; }
  .history-chart { height: 210px; }
  .dim-grid { grid-template-columns: 1fr; }
  .quiz-result { align-items: flex-start; }
  .qr-score { width: 74px; height: 80px; border-radius: 14px; }
}
@media (prefers-reduced-motion: reduce) { .skills-page * { animation: none !important; transition: none !important; } }
</style>
