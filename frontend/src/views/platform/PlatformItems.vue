<template>
  <div>
    <div class="pf-page-head">
      <div>
        <h2 class="pf-page-title">条目审核与分享</h2>
        <p class="pf-page-desc">学校发上来的项目先到这里。通过后会出现在各客户的商店里；推荐的项目排在商店最前；也可以只分享给指定学校。</p>
      </div>
      <div class="pf-seg view-seg">
        <button :class="{ active: view === 'card' }" @click="view = 'card'"><LayoutGrid :size="14" />卡片</button>
        <button :class="{ active: view === 'list' }" @click="view = 'list'"><List :size="14" />列表</button>
      </div>
    </div>

    <div class="pf-stats">
      <div class="pf-card pf-stat">
        <span class="pf-stat-icon" style="background:#fffbeb;color:#d97706"><Clock3 :size="20" /></span>
        <div><div class="pf-stat-value">{{ stats.pending ?? '–' }}</div><div class="pf-stat-label">待审核</div></div>
      </div>
      <div class="pf-card pf-stat">
        <span class="pf-stat-icon" style="background:#ecfdf5;color:#059669"><CircleCheckBig :size="20" /></span>
        <div><div class="pf-stat-value">{{ stats.approved ?? '–' }}</div><div class="pf-stat-label">已上架</div></div>
      </div>
      <div class="pf-card pf-stat">
        <span class="pf-stat-icon" style="background:#fdf4ff;color:#a21caf"><Star :size="20" /></span>
        <div><div class="pf-stat-value">{{ stats.featured ?? '–' }}</div><div class="pf-stat-label">推荐中</div></div>
      </div>
      <div class="pf-card pf-stat">
        <span class="pf-stat-icon" style="background:#f0f9ff;color:#0284c7"><Download :size="20" /></span>
        <div><div class="pf-stat-value">{{ stats.installs ?? '–' }}</div><div class="pf-stat-label">累计安装</div></div>
      </div>
    </div>

    <div class="flow">
      <div class="pf-card flow-step"><div class="n">1</div><h3>客户提交</h3><p>学校管理员或教师把项目发到商店，封面、教案图、资料一起上传。</p></div>
      <div class="pf-card flow-step"><div class="n">2</div><h3>你来审核</h3><p>点开条目看内容预览，通过或驳回；可勾选多条一次通过。好的项目点星推荐，商店里置顶。</p></div>
      <div class="pf-card flow-step"><div class="n">3</div><h3>对方安装</h3><p>其他学校在自己的商店里安装，会复制到本校。谁装了、装了几次在「安装情况」里能看到。</p></div>
    </div>

    <div class="pf-card">
      <div class="pf-card-head">
        <div class="pf-seg">
          <button v-for="s in segments" :key="s.value" :class="{ active: status === s.value }" @click="switchStatus(s.value)">
            {{ s.label }}<span v-if="s.count !== undefined" class="count">{{ s.count }}</span>
          </button>
        </div>
        <div class="pf-toolbar">
          <el-input v-model="keyword" placeholder="搜索标题 / 发布方" clearable style="width:240px">
            <template #prefix><Search :size="14" /></template>
          </el-input>
          <el-button @click="load"><RefreshCw :size="14" style="margin-right:6px" />刷新</el-button>
        </div>
      </div>

      <!-- 批量操作条 -->
      <div class="batch-bar" :class="{ show: selected.length > 0 }">
        <el-checkbox :model-value="allSelected" :indeterminate="selected.length > 0 && !allSelected" @change="toggleAll">
          已选 <b>{{ selected.length }}</b> / {{ filtered.length }} 项
        </el-checkbox>
        <span class="spacer"></span>
        <el-button size="small" type="primary" :loading="batching" @click="batch('APPROVED')"><CircleCheckBig :size="14" style="margin-right:4px" />批量通过并上架</el-button>
        <el-button size="small" type="danger" plain :loading="batching" @click="batch('REJECTED')">批量驳回</el-button>
        <el-button size="small" :loading="batching" @click="batch('OFFLINE')">批量下架</el-button>
        <el-button size="small" text @click="selected = []; syncTableSelection()">取消选择</el-button>
      </div>

      <!-- 列表视图 -->
      <el-table v-if="view === 'list'" ref="tableRef" :data="filtered" row-key="id" @selection-change="onSelectionChange" @row-click="open" row-class-name="clickable">
        <el-table-column type="selection" width="44" />
        <el-table-column label="项目" min-width="300">
          <template #default="{ row }">
            <div class="pf-title-cell">
              <img v-if="row.coverUrl" :src="row.coverUrl" class="pf-thumb" alt="" />
              <span v-else class="pf-thumb">📦</span>
              <div>
                <div class="t"><Star v-if="row.featured" :size="13" class="star-inline" />{{ row.title }}</div>
                <div class="s">#{{ row.id }} · {{ row.category || '未分类' }} · {{ (row.tags || []).slice(0, 3).join(' / ') }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="发布方" width="170">
          <template #default="{ row }">
            <div>{{ row.publisherTenantName }}</div>
            <div class="s2">{{ row.publisherUserName || '–' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><span class="pf-pill" :class="statusType(row.reviewStatus)">{{ statusText(row.reviewStatus) }}</span></template>
        </el-table-column>
        <el-table-column label="版本" width="120">
          <template #default="{ row }">
            <div>上架 <b>{{ row.currentVersionNo ? 'v' + row.currentVersionNo : '–' }}</b></div>
            <div class="s2">最新 v{{ row.latestVersionNo }}</div>
          </template>
        </el-table-column>
        <el-table-column label="可见范围" width="110">
          <template #default="{ row }"><span class="pf-pill" :class="row.visibility === 'PUBLIC' ? 'primary' : 'info'">{{ row.visibility === 'PUBLIC' ? '所有客户' : '指定客户' }}</span></template>
        </el-table-column>
        <el-table-column prop="installCount" label="安装" width="70" />
        <el-table-column label="更新时间" width="140">
          <template #default="{ row }"><span class="s2">{{ fmt(row.updatedAt) }}</span></template>
        </el-table-column>
        <el-table-column width="150" align="right">
          <template #default="{ row }">
            <button class="star-btn" :class="{ on: row.featured }" :title="row.featured ? '取消推荐' : '推荐置顶'" @click.stop="toggleFeatured(row)"><Star :size="16" /></button>
            <el-button size="small" type="primary" plain @click.stop="open(row)">处理</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="pf-empty">
            <div class="pf-empty-icon">🗂️</div>
            <div class="pf-empty-title">{{ status === 'PENDING' ? '没有待审核的条目' : '这里还没有条目' }}</div>
            <div>客户站点在「管理后台 → 项目商店 → 本站发布」提交的项目会出现在这里</div>
          </div>
        </template>
      </el-table>

      <!-- 卡片视图 -->
      <div v-else class="cards">
        <div v-if="filtered.length === 0" class="pf-empty" style="grid-column:1/-1">
          <div class="pf-empty-icon">🗂️</div>
          <div class="pf-empty-title">{{ status === 'PENDING' ? '没有待审核的条目' : '这里还没有条目' }}</div>
          <div>客户站点在「管理后台 → 项目商店 → 本站发布」提交的项目会出现在这里</div>
        </div>
        <div v-for="row in filtered" :key="row.id" class="item-card" :class="{ selected: selectedIds.has(row.id) }">
          <div class="cover" @click="open(row)">
            <img v-if="row.coverUrl" :src="row.coverUrl" alt="" />
            <div v-else class="cover-ph">📦</div>
            <span class="pick" @click.stop><el-checkbox :model-value="selectedIds.has(row.id)" @change="toggleOne(row)" /></span>
            <span class="pf-pill on-cover" :class="statusType(row.reviewStatus)">{{ statusText(row.reviewStatus) }}</span>
            <span v-if="row.featured" class="featured-badge"><Star :size="12" />推荐</span>
          </div>
          <div class="body">
            <div class="title" @click="open(row)">{{ row.title }}</div>
            <div class="pub">{{ row.publisherTenantName }} · {{ row.publisherUserName || '–' }}</div>
            <div class="summary">{{ row.summary || '暂无简介' }}</div>
            <div class="meta">
              <span>{{ row.category || '未分类' }}</span>
              <span>上架 {{ row.currentVersionNo ? 'v' + row.currentVersionNo : '–' }} / 最新 v{{ row.latestVersionNo }}</span>
              <span>安装 {{ row.installCount }}</span>
              <span>{{ row.visibility === 'PUBLIC' ? '所有客户' : '指定客户' }}</span>
            </div>
            <div class="actions">
              <el-button size="small" type="primary" @click="open(row)">处理</el-button>
              <el-button size="small" @click="openTab(row, 'preview')">预览</el-button>
              <span class="spacer"></span>
              <button class="star-btn" :class="{ on: row.featured }" :title="row.featured ? '取消推荐' : '推荐置顶'" @click="toggleFeatured(row)"><Star :size="17" /></button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 处理抽屉 -->
    <el-drawer v-model="visible" size="780px" class="pf-root" :with-header="false">
      <div v-if="detail" class="detail">
        <div class="detail-top">
          <img v-if="detail.coverUrl" :src="detail.coverUrl" class="detail-cover" alt="" />
          <div v-else class="detail-cover ph">📦</div>
          <div class="detail-meta">
            <div class="detail-title">{{ detail.title }}</div>
            <div class="row">
              <span class="pf-pill" :class="statusType(detail.reviewStatus)">{{ statusText(detail.reviewStatus) }}</span>
              <span class="pf-pill" :class="detail.visibility === 'PUBLIC' ? 'primary' : 'info'">{{ detail.visibility === 'PUBLIC' ? '所有客户可见' : '仅指定客户可见' }}</span>
              <span v-if="detail.featured" class="pf-pill featured">推荐置顶</span>
            </div>
            <dl class="pf-kv">
              <dt>发布方</dt><dd>{{ detail.publisherTenantName }} · {{ detail.publisherUserName || '–' }}</dd>
              <dt>分类 / 标签</dt><dd>{{ detail.category || '–' }}<span v-if="(detail.tags || []).length"> · {{ detail.tags.join(' / ') }}</span></dd>
              <dt>版本</dt><dd>上架 {{ detail.currentVersionNo ? 'v' + detail.currentVersionNo : '–' }} / 最新 v{{ detail.latestVersionNo }} · 安装 {{ detail.installCount }} 次</dd>
            </dl>
            <div class="detail-actions">
              <el-button :type="detail.featured ? 'default' : 'warning'" plain size="small" :loading="acting" @click="toggleFeatured(detail, true)">
                <Star :size="14" style="margin-right:4px" />{{ detail.featured ? '取消推荐' : '推荐置顶' }}
              </el-button>
              <el-button size="small" text @click="visible = false">关闭</el-button>
            </div>
          </div>
        </div>
        <div v-if="detail.reviewComment" class="pf-note warning" style="margin-top:14px"><MessageSquareText :size="15" />上次审核意见:{{ detail.reviewComment }}</div>

        <el-tabs v-model="tab" class="detail-tabs" @tab-change="onTab">
          <el-tab-pane label="审核与分享" name="review">
            <div class="pf-section-title">审核最新版本 v{{ detail.latestVersionNo }}</div>
            <div class="pf-card review-box">
              <el-input v-model="comment" type="textarea" :autosize="{ minRows: 2, maxRows: 5 }" placeholder="审核意见(驳回时必填,会展示给发布方)" />
              <div class="review-actions">
                <el-button text @click="tab = 'preview'; loadPreview()"><Eye :size="14" style="margin-right:6px" />先看内容</el-button>
                <span class="spacer"></span>
                <el-button :loading="acting" @click="review('OFFLINE')" :disabled="detail.reviewStatus === 'OFFLINE'">下架</el-button>
                <el-button type="danger" plain :loading="acting" @click="review('REJECTED')">驳回</el-button>
                <el-button type="primary" :loading="acting" @click="review('APPROVED')">通过并上架</el-button>
              </div>
            </div>

            <div class="pf-section-title">可见范围</div>
            <div class="pf-card pf-card-body vis-box">
              <div class="pf-seg">
                <button :class="{ active: visibility === 'PUBLIC' }" @click="saveVisibility('PUBLIC')">所有客户可见</button>
                <button :class="{ active: visibility === 'RESTRICTED' }" @click="saveVisibility('RESTRICTED')">仅指定客户可见</button>
              </div>
              <div class="grant-row">
                <el-select v-model="grantIds" multiple filterable collapse-tags collapse-tags-tooltip placeholder="选择可见的客户(任何可见范围下都可额外指定)" style="flex:1">
                  <el-option v-for="t in tenants" :key="t.id" :value="t.id" :label="`${t.name}(${t.code})`" />
                </el-select>
                <el-button type="primary" plain :loading="acting" @click="saveGrants">保存名单</el-button>
              </div>
              <div class="hint">{{ visibility === 'PUBLIC' ? '当前所有已接入客户都能浏览并安装;名单用于额外记录重点分享对象。' : '当前只有名单中的客户能在商店里看到此项目。' }}</div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="内容预览" name="preview">
            <div v-if="!payload" class="pf-empty tight">正在加载内容…</div>
            <div v-else class="preview">
              <div class="pv-head">
                <span class="pv-icon">{{ payload.icon || '🔌' }}</span>
                <div class="grow">
                  <div class="pv-title">{{ payload.title }}</div>
                  <div class="pv-sub">{{ payload.summary || '暂无简介' }}</div>
                </div>
                <el-button size="small" text @click="rawVisible = true">查看原始 JSON</el-button>
              </div>
              <div class="pv-facts">
                <div><span>难度</span><b>{{ payload.difficulty || '–' }}</b></div>
                <div><span>周期</span><b>{{ payload.duration || '–' }}</b></div>
                <div><span>团队</span><b>{{ payload.teamSize || '–' }}</b></div>
                <div><span>作者</span><b>{{ payload.author || '–' }}</b></div>
                <div><span>许可</span><b>{{ payload.license || '–' }}</b></div>
                <div><span>预估成本</span><b>{{ payload.cost != null ? '¥' + payload.cost : '–' }}</b></div>
              </div>
              <div v-if="arr(payload.tags).length" class="pv-tags"><el-tag v-for="t in payload.tags" :key="t" size="small" effect="plain">{{ t }}</el-tag></div>

              <div class="pv-section" v-if="arr(payload.features).length || arr(payload.learningGoals).length || arr(payload.prerequisites).length">
                <div class="pv-cols">
                  <div v-if="arr(payload.features).length"><h4>项目亮点</h4><ul><li v-for="(f, i) in payload.features" :key="i">{{ f }}</li></ul></div>
                  <div v-if="arr(payload.learningGoals).length"><h4>学习目标</h4><ul><li v-for="(f, i) in payload.learningGoals" :key="i">{{ f }}</li></ul></div>
                  <div v-if="arr(payload.prerequisites).length"><h4>先修要求</h4><ul><li v-for="(f, i) in payload.prerequisites" :key="i">{{ f }}</li></ul></div>
                </div>
              </div>

              <div class="pv-section" v-if="payload.description">
                <h4>项目介绍</h4>
                <div class="rich" v-html="safeHtml(payload.description)"></div>
              </div>

              <div class="pv-section" v-if="arr(payload.syllabus).length">
                <h4>教学大纲 <span class="pv-count">{{ payload.syllabus.length }} 个阶段 · 共 {{ sum(payload.syllabus, 'hours') }} 小时</span></h4>
                <el-timeline>
                  <el-timeline-item v-for="(s, i) in payload.syllabus" :key="i" :timestamp="s.phase" placement="top">
                    <div class="syl-t">{{ s.title }} <span class="s2">{{ s.hours }} 小时</span></div>
                    <div class="syl-c">{{ s.content }}</div>
                  </el-timeline-item>
                </el-timeline>
              </div>

              <div class="pv-section" v-if="arr(payload.skillRequirements).length || arr(payload.assessments).length">
                <div class="pv-cols">
                  <div v-if="arr(payload.skillRequirements).length">
                    <h4>技能要求</h4>
                    <div v-for="(s, i) in payload.skillRequirements" :key="i" class="skill-row"><span>{{ s.name }}</span><el-progress :percentage="Math.min(100, Number(s.required) || 0)" :stroke-width="8" style="flex:1" /></div>
                  </div>
                  <div v-if="arr(payload.assessments).length">
                    <h4>考核方式</h4>
                    <div v-for="(a, i) in payload.assessments" :key="i" class="assess-row"><span>{{ a.name }}</span><span class="s2">{{ a.desc }}</span><b>{{ a.weight }}%</b></div>
                  </div>
                </div>
              </div>

              <div class="pv-section" v-if="arr(payload.bom).length">
                <h4>BOM 物料 <span class="pv-count">{{ payload.bom.length }} 项</span></h4>
                <el-table :data="payload.bom" size="small">
                  <el-table-column prop="ref" label="位号" width="90" />
                  <el-table-column prop="name" label="名称" />
                  <el-table-column prop="qty" label="数量" width="70" />
                  <el-table-column prop="footprint" label="封装" width="120" />
                  <el-table-column label="单价" width="90"><template #default="{ row }">{{ row.price != null ? '¥' + row.price : '–' }}</template></el-table-column>
                </el-table>
              </div>

              <div class="pv-section" v-if="arr(payload.resources).length || arr(payload.equipmentNames).length">
                <div class="pv-cols">
                  <div v-if="arr(payload.resources).length">
                    <h4>教学资料 <span class="pv-count">{{ payload.resources.length }} 个文件</span></h4>
                    <div v-for="(r, i) in payload.resources" :key="i" class="res-row">
                      <span class="pf-pill info">{{ r.type || '文件' }}</span>
                      <span class="grow">{{ r.name }}</span>
                      <a v-if="r.url" :href="r.url" target="_blank" class="pf-link">打开</a>
                      <span v-else class="s2">未上传</span>
                    </div>
                  </div>
                  <div v-if="arr(payload.equipmentNames).length">
                    <h4>所需设备</h4>
                    <div class="pv-tags"><el-tag v-for="(e, i) in payload.equipmentNames" :key="i" size="small">{{ e }}</el-tag></div>
                  </div>
                </div>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="安装情况" name="installs">
            <div v-if="!installs" class="pf-empty tight">正在加载…</div>
            <div v-else>
              <div class="inst-summary">
                <div class="pf-card pf-stat"><span class="pf-stat-icon" style="background:#f0f9ff;color:#0284c7"><Download :size="18" /></span><div><div class="pf-stat-value">{{ installs.total }}</div><div class="pf-stat-label">累计安装次数</div></div></div>
                <div class="pf-card pf-stat"><span class="pf-stat-icon" style="background:#eef2ff;color:#4f46e5"><Building2 :size="18" /></span><div><div class="pf-stat-value">{{ installs.tenantCount }}</div><div class="pf-stat-label">安装过的站点</div></div></div>
              </div>
              <div v-if="installs.byTenant.length === 0" class="pf-empty tight">
                <div class="pf-empty-title">还没有站点安装过</div>
                <div>{{ detail.reviewStatus === 'APPROVED' ? '已上架,等其他学校在商店里安装' : '先通过审核上架,其他学校才能安装' }}</div>
              </div>
              <el-table v-else :data="installs.byTenant" size="small">
                <el-table-column label="站点" min-width="200">
                  <template #default="{ row }">
                    <div class="t">{{ row.tenantName }}</div>
                    <div class="s2 pf-mono">{{ row.tenantCode || '–' }}</div>
                  </template>
                </el-table-column>
                <el-table-column label="安装次数" width="90" prop="count" />
                <el-table-column label="最近版本" width="90"><template #default="{ row }">{{ row.lastVersionNo ? 'v' + row.lastVersionNo : '–' }}</template></el-table-column>
                <el-table-column label="最近安装" min-width="160">
                  <template #default="{ row }"><div>{{ fmt(row.lastInstalledAt) }}</div><div class="s2">{{ row.lastInstalledBy || '–' }}</div></template>
                </el-table-column>
                <el-table-column label="状态" width="90">
                  <template #default="{ row }"><span class="pf-pill" :class="row.tenantStatus === 'ACTIVE' ? 'success' : 'danger'">{{ row.tenantStatus === 'ACTIVE' ? '运行中' : (row.tenantStatus ? '已停用' : '已注销') }}</span></template>
                </el-table-column>
              </el-table>
              <template v-if="installs.recent.length">
                <div class="pf-section-title">最近安装记录</div>
                <el-timeline>
                  <el-timeline-item v-for="(r, i) in installs.recent" :key="i" :timestamp="fmt(r.installedAt)" placement="top">
                    <b>{{ r.tenantName }}</b> 安装了 v{{ r.versionNo }}<span v-if="r.installedBy" class="s2"> · {{ r.installedBy }}</span>
                  </el-timeline-item>
                </el-timeline>
              </template>
            </div>
          </el-tab-pane>

          <el-tab-pane label="版本与记录" name="history">
            <div class="pf-section-title">版本</div>
            <el-table :data="detail.versions" size="small">
              <el-table-column prop="versionNo" label="版本" width="70"><template #default="{ row }">v{{ row.versionNo }}</template></el-table-column>
              <el-table-column prop="changelog" label="说明" show-overflow-tooltip />
              <el-table-column prop="createdBy" label="提交人" width="130" show-overflow-tooltip />
              <el-table-column label="时间" width="140"><template #default="{ row }"><span class="s2">{{ fmt(row.createdAt) }}</span></template></el-table-column>
              <el-table-column width="80"><template #default="{ row }"><span v-if="row.current" class="pf-pill success">上架中</span></template></el-table-column>
            </el-table>
            <div class="pf-section-title">审核记录</div>
            <div v-if="!detail.reviewLogs?.length" class="s2" style="padding:6px 4px">还没有审核记录</div>
            <el-timeline v-else class="logs">
              <el-timeline-item v-for="l in detail.reviewLogs" :key="l.id" :timestamp="fmt(l.reviewedAt)" placement="top">
                <b>{{ l.reviewer }}</b> {{ statusText(l.decision) }}<span v-if="l.comment"> · {{ l.comment }}</span>
              </el-timeline-item>
            </el-timeline>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>

    <el-dialog v-model="rawVisible" title="最新版本内容(JSON)" width="760px" class="pf-root">
      <pre class="json">{{ payload ? JSON.stringify(payload, null, 2) : '' }}</pre>
    </el-dialog>

    <!-- 批量驳回意见 -->
    <el-dialog v-model="batchVisible" :title="batchTitle" width="480px" class="pf-root">
      <p class="s2" style="margin:0 0 10px">将对已选的 {{ selected.length }} 个条目执行「{{ batchTitle }}」。{{ batchDecision === 'REJECTED' ? '驳回意见必填,会展示给各发布方。' : '可选填写说明。' }}</p>
      <el-input v-model="batchComment" type="textarea" :autosize="{ minRows: 3, maxRows: 6 }" :placeholder="batchDecision === 'REJECTED' ? '例如:封面缺失、教案内容不完整' : '可留空'" />
      <template #footer>
        <el-button @click="batchVisible = false">取消</el-button>
        <el-button :type="batchDecision === 'REJECTED' ? 'danger' : 'primary'" :loading="batching" @click="runBatch">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Building2, CircleCheckBig, Clock3, Download, Eye, LayoutGrid, List, MessageSquareText, RefreshCw, Search, Star } from 'lucide-vue-next'
import {
  hubBatchReview, hubFeatured, hubGrants, hubItem, hubItemInstalls, hubItemPayload, hubItems, hubReview, hubStats, hubTenants, hubVisibility
} from '../../api/hub'

const VIEW_KEY = 'pf_items_view'
const emit = defineEmits(['refresh-stats'])
const view = ref(localStorage.getItem(VIEW_KEY) || 'card')
const status = ref('PENDING')
const keyword = ref('')
const items = ref([])
const stats = ref({})
const tenants = ref([])
const tableRef = ref(null)

const selected = ref([])
const selectedIds = computed(() => new Set(selected.value.map((r) => r.id)))
const batching = ref(false)
const batchVisible = ref(false)
const batchDecision = ref('APPROVED')
const batchComment = ref('')

const visible = ref(false)
const tab = ref('review')
const detail = ref(null)
const payload = ref(null)
const installs = ref(null)
const comment = ref('')
const visibility = ref('PUBLIC')
const grantIds = ref([])
const acting = ref(false)
const rawVisible = ref(false)

watch(view, (v) => {
  localStorage.setItem(VIEW_KEY, v)
  syncTableSelection()
})

const segments = computed(() => [
  { value: 'PENDING', label: '待审核', count: stats.value.pending },
  { value: 'APPROVED', label: '已上架', count: stats.value.approved },
  { value: 'REJECTED', label: '已驳回', count: stats.value.rejected },
  { value: 'OFFLINE', label: '已下架', count: stats.value.offline },
  { value: 'ALL', label: '全部' }
])
const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  return items.value.filter((i) => !k || (i.title + (i.publisherTenantName || '')).toLowerCase().includes(k))
})
const allSelected = computed(() => filtered.value.length > 0 && filtered.value.every((r) => selectedIds.value.has(r.id)))
const batchTitle = computed(() => ({ APPROVED: '批量通过并上架', REJECTED: '批量驳回', OFFLINE: '批量下架' }[batchDecision.value]))

const load = async () => {
  const [list, s] = await Promise.all([hubItems(status.value), hubStats()])
  items.value = list
  stats.value = s
  // 列表刷新后已选项可能已不在当前筛选下,按 id 重新对齐
  selected.value = list.filter((r) => selectedIds.value.has(r.id))
  syncTableSelection()
  emit('refresh-stats')
}

const switchStatus = (v) => {
  status.value = v
  selected.value = []
  load()
}

// ---- 选择(卡片与表格共用同一份 selected;表格自身的勾选状态由这里单向同步) ----
let syncing = false
const syncTableSelection = async () => {
  if (view.value !== 'list') return
  await nextTick()
  const t = tableRef.value
  if (!t) return
  syncing = true
  try {
    t.clearSelection()
    selected.value.forEach((r) => {
      const row = items.value.find((i) => i.id === r.id)
      if (row) t.toggleRowSelection(row, true)
    })
  } finally {
    syncing = false
  }
}
const onSelectionChange = (rows) => {
  if (syncing) return
  selected.value = rows
}
const toggleOne = (row) => {
  if (selectedIds.value.has(row.id)) selected.value = selected.value.filter((r) => r.id !== row.id)
  else selected.value = [...selected.value, row]
}
const toggleAll = (checked) => {
  selected.value = checked ? [...filtered.value] : []
  syncTableSelection()
}

// ---- 批量审核 ----
const batch = (decision) => {
  if (selected.value.length === 0) return
  batchDecision.value = decision
  batchComment.value = ''
  batchVisible.value = true
}
const runBatch = async () => {
  if (batchDecision.value === 'REJECTED' && !batchComment.value.trim()) { ElMessage.warning('批量驳回请填写意见'); return }
  batching.value = true
  try {
    const r = await hubBatchReview(selected.value.map((s) => s.id), batchDecision.value, batchComment.value)
    batchVisible.value = false
    if (r.failed.length === 0) {
      ElMessage.success(`${batchTitle.value}完成,共 ${r.ok} 项`)
    } else {
      ElMessage.warning(`成功 ${r.ok} 项,失败 ${r.failed.length} 项:${r.failed.map((f) => '#' + f.id + ' ' + f.message).join(';')}`)
    }
    selected.value = []
    await load()
  } finally {
    batching.value = false
  }
}

// ---- 推荐置顶 ----
const toggleFeatured = async (row, fromDetail = false) => {
  const next = !row.featured
  if (next && !row.currentVersionNo) { ElMessage.warning('先通过审核上架,再推荐'); return }
  acting.value = true
  try {
    const updated = await hubFeatured(row.id, next)
    ElMessage.success(next ? '已推荐,将在各客户商店置顶' : '已取消推荐')
    const idx = items.value.findIndex((i) => i.id === row.id)
    if (idx >= 0) items.value[idx] = { ...items.value[idx], ...updated }
    if (fromDetail || (detail.value && detail.value.id === row.id)) detail.value = { ...detail.value, ...updated }
    stats.value = await hubStats()
  } finally {
    acting.value = false
  }
}

// ---- 抽屉 ----
const open = async (row) => {
  detail.value = await hubItem(row.id)
  payload.value = null
  installs.value = null
  comment.value = ''
  tab.value = 'review'
  visibility.value = detail.value.visibility
  grantIds.value = (detail.value.grants || []).map((g) => g.tenantId)
  if (tenants.value.length === 0) tenants.value = await hubTenants()
  visible.value = true
}
const openTab = async (row, t) => {
  await open(row)
  tab.value = t
  onTab(t)
}
const onTab = (t) => {
  if (t === 'preview') loadPreview()
  if (t === 'installs') loadInstalls()
}
const loadPreview = async () => {
  if (payload.value || !detail.value) return
  payload.value = await hubItemPayload(detail.value.id)
}
const loadInstalls = async () => {
  if (installs.value || !detail.value) return
  installs.value = await hubItemInstalls(detail.value.id)
}

const review = async (decision) => {
  if (decision === 'REJECTED' && !comment.value.trim()) { ElMessage.warning('驳回请填写审核意见'); return }
  if (decision === 'OFFLINE') {
    try { await ElMessageBox.confirm('下架后所有客户商店里都看不到此项目,已安装的站点不受影响。可随时再通过恢复。', '下架项目', { type: 'warning', confirmButtonText: '下架', cancelButtonText: '取消' }) } catch (e) { return }
  }
  acting.value = true
  try {
    await hubReview(detail.value.id, { decision, comment: comment.value })
    ElMessage.success({ APPROVED: '已上架', REJECTED: '已驳回', OFFLINE: '已下架' }[decision])
    detail.value = await hubItem(detail.value.id)
    comment.value = ''
    await load()
  } finally {
    acting.value = false
  }
}

const saveVisibility = async (v) => {
  if (v === visibility.value) return
  await hubVisibility(detail.value.id, v)
  visibility.value = v
  detail.value.visibility = v
  ElMessage.success(v === 'PUBLIC' ? '已设为所有客户可见' : '已设为仅指定客户可见')
  await load()
}

const saveGrants = async () => {
  acting.value = true
  try {
    detail.value = await hubGrants(detail.value.id, grantIds.value)
    ElMessage.success('分享名单已保存')
  } finally {
    acting.value = false
  }
}

// ---- 渲染式预览用的小工具 ----
const arr = (v) => (Array.isArray(v) ? v : [])
const sum = (list, key) => arr(list).reduce((s, x) => s + (Number(x?.[key]) || 0), 0)
// 富文本来自客户站点,展示前剔除脚本与事件属性,避免在平台管理员会话里执行
const safeHtml = (html) => {
  const doc = new DOMParser().parseFromString(String(html || ''), 'text/html')
  doc.querySelectorAll('script,iframe,object,embed,link,style,meta,form').forEach((n) => n.remove())
  doc.body.querySelectorAll('*').forEach((el) => {
    for (const a of Array.from(el.attributes)) {
      const name = a.name.toLowerCase()
      const val = String(a.value).trim().toLowerCase()
      if (name.startsWith('on') || ((name === 'href' || name === 'src') && (val.startsWith('javascript:') || val.startsWith('data:text')))) {
        el.removeAttribute(a.name)
      }
    }
  })
  return doc.body.innerHTML
}

const statusText = (s) => ({ PENDING: '待审核', APPROVED: '已上架', REJECTED: '已驳回', OFFLINE: '已下架' }[s] || s)
const statusType = (s) => ({ PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', OFFLINE: 'info' }[s] || 'info')
const fmt = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '–')

onMounted(load)
</script>

<style scoped>
.s2 { font-size: 12px; color: var(--pf-text-3); }
.t { font-weight: 600; }
.grow { flex: 1; min-width: 0; }
.spacer { flex: 1; }
:deep(.clickable) { cursor: pointer; }
.view-seg button { padding: 7px 12px; }
.star-inline { color: #f59e0b; fill: #f59e0b; vertical-align: -2px; margin-right: 4px; }
.star-btn { border: none; background: transparent; color: #cbd5e1; cursor: pointer; padding: 4px; border-radius: 8px; vertical-align: middle; margin-right: 6px; display: inline-grid; place-items: center; }
.star-btn:hover { background: #fffbeb; color: #f59e0b; }
.star-btn.on { color: #f59e0b; }
.star-btn.on svg { fill: #f59e0b; }

/* 批量条 */
.batch-bar { display: none; align-items: center; gap: 10px; padding: 10px 22px; background: var(--pf-primary-soft); border-bottom: 1px solid #e0e7ff; font-size: 13px; }
.batch-bar.show { display: flex; }

/* 卡片视图 */
.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; padding: 18px 22px 22px; }
.item-card { border: 1px solid var(--pf-border); border-radius: 12px; overflow: hidden; background: #fff; display: flex; flex-direction: column; transition: box-shadow .15s, border-color .15s; }
.item-card:hover { box-shadow: var(--pf-shadow); }
.item-card.selected { border-color: var(--pf-primary); box-shadow: 0 0 0 2px var(--pf-primary-soft); }
.cover { position: relative; height: 150px; background: #eef0f6; cursor: pointer; }
.cover img { width: 100%; height: 100%; object-fit: cover; display: block; }
.cover-ph { height: 100%; display: grid; place-items: center; font-size: 42px; }
.pick { position: absolute; top: 8px; left: 10px; background: rgba(255, 255, 255, .92); border-radius: 6px; padding: 0 6px; line-height: 1; display: flex; align-items: center; height: 24px; }
.on-cover { position: absolute; top: 9px; right: 10px; }
.featured-badge { position: absolute; bottom: 10px; left: 10px; display: inline-flex; align-items: center; gap: 4px; padding: 3px 9px; border-radius: 999px; font-size: 12px; font-weight: 700; background: #f59e0b; color: #1f1300; }
.featured-badge svg { fill: #1f1300; }
.body { padding: 12px 14px 14px; display: flex; flex-direction: column; gap: 6px; flex: 1; }
.title { font-weight: 700; font-size: 15px; cursor: pointer; }
.pub { font-size: 12px; color: var(--pf-text-2); }
.summary { font-size: 13px; color: var(--pf-text-3); line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; min-height: 39px; }
.meta { display: flex; gap: 10px; flex-wrap: wrap; font-size: 12px; color: var(--pf-text-3); }
.actions { display: flex; align-items: center; gap: 8px; margin-top: auto; padding-top: 8px; }

/* 抽屉 */
.detail-top { display: flex; gap: 18px; }
.detail-cover { width: 220px; height: 140px; object-fit: cover; border-radius: 12px; flex-shrink: 0; background: #eef0f6; }
.detail-cover.ph { display: grid; place-items: center; font-size: 40px; }
.detail-meta { flex: 1; min-width: 0; }
.detail-title { font-size: 18px; font-weight: 800; margin-bottom: 8px; }
.detail-meta .row { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.detail-actions { display: flex; gap: 8px; margin-top: 12px; }
.pf-pill.featured { background: #fef3c7; color: #b45309; }
.detail-tabs { margin-top: 16px; }
.review-box { padding: 16px; display: flex; flex-direction: column; gap: 12px; }
.review-actions { display: flex; align-items: center; gap: 8px; }
.vis-box { display: flex; flex-direction: column; gap: 12px; }
.grant-row { display: flex; gap: 10px; align-items: center; }
.hint { font-size: 12px; color: var(--pf-text-3); }
.logs { margin-top: 6px; padding-left: 4px; }
.json { background: #0f172a; color: #e2e8f0; padding: 16px; border-radius: 12px; max-height: 60vh; overflow: auto; font-size: 12px; margin: 0; }
.pf-empty.tight { padding: 32px 16px; }

/* 渲染式预览 */
.pv-head { display: flex; gap: 12px; align-items: flex-start; }
.pv-icon { width: 44px; height: 44px; border-radius: 12px; background: var(--pf-primary-soft); display: grid; place-items: center; font-size: 22px; flex-shrink: 0; }
.pv-title { font-size: 16px; font-weight: 700; }
.pv-sub { font-size: 13px; color: var(--pf-text-2); margin-top: 3px; line-height: 1.55; }
.pv-facts { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-top: 14px; }
.pv-facts > div { background: #f8f9fc; border-radius: 10px; padding: 8px 12px; display: flex; justify-content: space-between; font-size: 13px; }
.pv-facts span { color: var(--pf-text-3); }
.pv-tags { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 12px; }
.pv-section { margin-top: 20px; padding-top: 16px; border-top: 1px solid var(--pf-border); }
.pv-section h4 { margin: 0 0 10px; font-size: 14px; display: flex; align-items: center; gap: 8px; }
.pv-count { font-size: 12px; color: var(--pf-text-3); font-weight: 500; }
.pv-cols { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 18px; }
.pv-cols ul { margin: 0; padding-left: 18px; font-size: 13px; color: var(--pf-text-2); line-height: 1.7; }
.rich { font-size: 14px; line-height: 1.75; color: var(--pf-text); word-break: break-word; }
.rich :deep(img) { max-width: 100%; border-radius: 10px; display: block; margin: 10px 0; }
.rich :deep(pre) { background: #f8f9fc; padding: 12px; border-radius: 10px; overflow: auto; font-size: 12px; }
.rich :deep(table) { border-collapse: collapse; width: 100%; font-size: 13px; }
.rich :deep(td), .rich :deep(th) { border: 1px solid var(--pf-border); padding: 6px 8px; }
.syl-t { font-weight: 600; font-size: 14px; }
.syl-c { font-size: 13px; color: var(--pf-text-2); margin-top: 3px; line-height: 1.6; white-space: pre-wrap; }
.skill-row { display: flex; align-items: center; gap: 10px; font-size: 13px; margin-bottom: 8px; }
.skill-row > span { width: 90px; flex-shrink: 0; }
.assess-row { display: flex; align-items: center; gap: 10px; font-size: 13px; padding: 6px 0; border-bottom: 1px dashed var(--pf-border); }
.assess-row > span:first-child { font-weight: 600; }
.assess-row .s2 { flex: 1; }
.res-row { display: flex; align-items: center; gap: 10px; font-size: 13px; padding: 7px 0; border-bottom: 1px dashed var(--pf-border); }
.inst-summary { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 16px; }

.flow { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; margin-bottom: 16px; }
.flow-step { padding: 18px 20px; }
.flow-step .n { width: 26px; height: 26px; border-radius: 8px; background: var(--pf-primary-soft); color: var(--pf-primary); font-weight: 800; display: grid; place-items: center; margin-bottom: 10px; font-size: 13px; }
.flow-step h3 { margin: 0 0 6px; font-size: 14px; }
.flow-step p { margin: 0; font-size: 13px; color: var(--pf-text-2); line-height: 1.6; }
@media (max-width: 960px) { .flow, .pv-facts, .inst-summary { grid-template-columns: 1fr; } }
</style>
