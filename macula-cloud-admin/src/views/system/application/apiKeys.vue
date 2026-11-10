<template>
  <el-drawer v-model="visible" :title="`APIKEY 管理 - ${application.applicationName || ''}`"
    size="min(100%, 960px)" destroy-on-close @close="clearSecrets" @closed="$emit('closed')">
    <div class="key-content">
    <div class="key-toolbar">
      <el-button type="primary" @click="edit()">＋ 创建 API Key</el-button>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="key-error" />
    <el-table v-loading="loading" :data="rows" empty-text="暂无 API Key" class="key-table">
      <el-table-column label="API Key" min-width="260">
        <template #default="{ row }">
          <!-- 凭据与操作保持同一行；长 Key 在字段内滚动，避免挤压按钮。 -->
          <div class="key-credential">
          <span class="key-value" tabindex="0" aria-label="API Key">{{ revealed[row.id] || row.maskedKey }}</span>
          <div class="key-actions">
            <el-tooltip :content="revealed[row.id] ? '隐藏 API Key' : '显示 API Key'" placement="top">
              <el-button text size="small" class="key-icon" :loading="!!busy[row.id]"
                :icon="revealed[row.id] ? 'el-icon-hide' : 'el-icon-view'"
                :aria-label="revealed[row.id] ? '隐藏 API Key' : '显示 API Key'" @click="toggle(row)" />
            </el-tooltip>
            <el-tooltip content="复制 API Key" placement="top">
              <el-button text size="small" class="key-icon" :loading="!!busy[row.id]"
                icon="el-icon-copy-document" aria-label="复制 API Key" @click="copy(row)" />
            </el-tooltip>
          </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="100" show-overflow-tooltip />
      <el-table-column label="可访问路径" min-width="150" show-overflow-tooltip>
        <template #default="{ row }"><span class="access-rules">{{ displayUrls(row.accessPath) || '未配置，缓存使用应用 URL' }}</span></template>
      </el-table-column>
      <el-table-column label="允许 IP" min-width="150">
        <template #default="{ row }"><span class="access-rules">{{ row.allowIps || '未配置，使用应用 IP；应用为空则不限' }}</span></template>
      </el-table-column>
      <el-table-column label="创建时间（当地）" width="175">
        <template #default="{ row }">{{ formatCreateTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <div class="key-actions">
            <el-button text type="primary" :disabled="!!busy[row.id]" @click="edit(row)">编辑</el-button>
            <el-button text type="danger" :loading="!!busy[row.id]" @click="remove(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="pageNum" :page-size="pageSize" :total="total"
      layout="total, prev, pager, next" @current-change="load" />
    </div>
    <el-dialog v-model="editing" :title="form.id ? '编辑 API Key' : '创建 API Key'" width="min(92%, 560px)"
      append-to-body :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving">
      <el-form label-position="top" @submit.prevent="save">
        <el-form-item label="备注"><el-input v-model="form.remark" maxlength="255" show-word-limit /></el-form-item>
        <el-form-item label="可访问路径">
          <el-input v-model="form.accessPath" type="textarea" :rows="3" maxlength="2000"
            :placeholder="'GET:/service/api/**\nPOST:/service/api/orders'" />
          <div class="key-hint">格式为 HTTP方法:/路径，支持 Ant 通配符（*、**），每行一条，兼容原英文逗号分隔。</div>
          <div class="key-hint">留空使用应用的可访问路径；应用也未配置时不限制访问路径。</div>
        </el-form-item>
        <el-form-item label="允许 IP">
          <el-input v-model="form.allowIps" type="textarea" :rows="3" maxlength="2000"
            :placeholder="'192.0.2.10\n192.0.2.0/24\n2001:db8::/32'" />
          <div class="key-hint">每行一个 IPv4、IPv6 或 CIDR 网段；留空继承应用，应用也为空时不限。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="editing = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ form.id ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="createdVisible" title="API Key 创建成功" width="min(92%, 620px)" append-to-body
      @closed="createdKey = ''">
      <el-alert title="请复制保存；关闭后可通过列表中的显示或复制按钮获取。" type="success" :closable="false" />
      <el-input :model-value="createdKey" readonly class="created-key">
        <template #append><el-button @click="writeClipboard(createdKey)">复制</el-button></template>
      </el-input>
      <template #footer><el-button type="primary" @click="createdVisible = false">确认</el-button></template>
    </el-dialog>
  </el-drawer>
</template>

<script>
import { ElMessage, ElMessageBox } from 'element-plus'
import tool from '@/utils/tool'
import { normalizeUrlRules, normalizeIpRules } from '@/utils/applicationAccessRules'

/** 应用 API Key 管理；完整凭据仅保留在当前打开的抽屉状态中。 */
export default {
  name: 'ApplicationApiKeys',
  emits: ['closed'],
  data() {
    return {
      visible: false, application: {}, rows: [], total: 0, pageNum: 1, pageSize: 20,
      loading: false, error: '', revealed: {}, busy: {}, generation: 0, requestId: 0,
      editing: false, saving: false, form: { id: null, remark: '', accessPath: '', allowIps: '' },
      createdVisible: false, createdKey: ''
    }
  },
  beforeUnmount() { this.clearSecrets() },
  methods: {
    displayUrls: normalizeUrlRules,
    /** 接口携带时区偏移，复用日期工具按浏览器当地时间展示，避免硬编码时差。 */
    formatCreateTime(value) {
      return tool.formatDateTime(value)
    },
    api() { return this.$API.system_application.apiKeys },
    /** 打开应用抽屉并加载独立分页。 */
    open(application) {
      this.clearSecrets()
      this.application = application
      this.visible = true
      this.pageNum = 1
      this.rows = []
      this.total = 0
      this.load()
    },
    /** 阻止关闭或切换应用后，迟到响应重新填入完整凭据。 */
    clearSecrets() {
      this.generation++
      this.revealed = {}
      this.busy = {}
      this.createdKey = ''
      this.createdVisible = false
      this.editing = false
      this.saving = false
      this.error = ''
    },
    async load() {
      const generation = this.generation
      const request = ++this.requestId
      this.loading = true
      this.error = ''
      this.revealed = {}
      try {
        const result = await this.api().list(this.application.id, { pageNum: this.pageNum, pageSize: this.pageSize })
        if (generation !== this.generation || request !== this.requestId) return
        if (!result.success) throw new Error(result.msg || '加载失败')
        this.rows = result.data.records || []
        this.total = result.data.total || 0
      } catch (error) {
        if (generation === this.generation && request === this.requestId) {
          this.rows = []
          this.total = 0
          this.error = 'API Key 加载失败，请重试'
        }
      } finally {
        if (generation === this.generation && request === this.requestId) this.loading = false
      }
    },
    edit(row) {
      this.form = row ? { id: row.id, remark: row.remark || '', accessPath: normalizeUrlRules(row.accessPath), allowIps: normalizeIpRules(row.allowIps) }
        : { id: null, remark: '', accessPath: '', allowIps: '' }
      this.editing = true
    },
    /** 创建和更新只提交配置，不提交已有完整凭据。 */
    async save() {
      if (this.saving) return
      this.saving = true
      const generation = this.generation
      const appId = this.application.id
      try {
        const payload = { remark: this.form.remark.trim(), accessPath: normalizeUrlRules(this.form.accessPath),
          allowIps: normalizeIpRules(this.form.allowIps) }
        const result = this.form.id
          ? await this.api().update(appId, this.form.id, payload)
          : await this.api().create(appId, payload)
        if (generation !== this.generation) return
        if (!result.success) throw new Error(result.msg || '保存失败')
        this.editing = false
        if (!this.form.id) {
          this.createdKey = result.data.apiKey
          this.createdVisible = true
        } else ElMessage.success('保存成功')
        await this.load()
      } catch (error) {
        if (generation === this.generation) ElMessage.error(error.message || '保存失败，请刷新列表确认后重试')
      } finally {
        if (generation === this.generation) this.saving = false
      }
    },
    async toggle(row) {
      if (this.revealed[row.id]) { delete this.revealed[row.id]; return }
      await this.withSecret(row, value => { this.revealed[row.id] = value })
    },
    async copy(row) { await this.withSecret(row, value => this.writeClipboard(value)) },
    /** 每次查看均重新授权，错误和日志中不包含完整值。 */
    async withSecret(row, use) {
      if (this.busy[row.id]) return
      const generation = this.generation
      this.busy[row.id] = true
      try {
        const result = await this.api().secret(this.application.id, row.id)
        if (generation !== this.generation) return
        if (!result.success) throw new Error('查看失败')
        await use(result.data.apiKey)
      } catch (error) {
        if (generation === this.generation) ElMessage.error('无法获取 API Key')
      } finally {
        if (generation === this.generation) delete this.busy[row.id]
      }
    },
    async writeClipboard(value) {
      try { await navigator.clipboard.writeText(value); ElMessage.success('已复制') }
      catch (error) { ElMessage.error('复制失败，请显示后手动复制') }
    },
    async remove(row) {
      if (this.busy[row.id]) return
      const generation = this.generation
      const appId = this.application.id
      this.busy[row.id] = true
      try {
        await ElMessageBox.confirm('删除后该 API Key 将无法访问网关，确认删除？', '删除 API Key', { type: 'warning' })
        if (generation !== this.generation) return
        const result = await this.api().remove(appId, row.id)
        if (generation !== this.generation) return
        if (!result.success) throw new Error('删除失败')
        delete this.revealed[row.id]
        ElMessage.success('删除成功')
        if (this.rows.length === 1 && this.pageNum > 1) this.pageNum--
        await this.load()
      } catch (error) {
        if (generation === this.generation && error !== 'cancel' && error !== 'close') {
          ElMessage.error('删除失败，请刷新后确认状态')
        }
      } finally {
        if (generation === this.generation) delete this.busy[row.id]
      }
    }
  }
}
</script>

<style scoped>
.key-content { padding: 0 20px 20px; }
/* 表格单元格中的按钮组禁止换行；使用 gap 统一间距，覆盖组件默认相邻按钮外边距。 */
.key-toolbar, .key-actions, .key-credential { display: flex; flex-wrap: nowrap; gap: 8px; align-items: center; }
.key-actions { flex-shrink: 0; }
.key-actions :deep(.el-button) { margin-left: 0; padding: 8px; white-space: nowrap; }
.key-actions :deep(.key-icon) { width: 28px; height: 28px; padding: 6px; }
.key-credential { min-width: 0; }
.key-toolbar { margin-bottom: 20px; }
.key-error, .key-table { margin-bottom: 16px; }
.key-value { flex: 1; min-width: 0; font-family: monospace; white-space: nowrap; overflow-x: auto; user-select: all; }
.access-rules { white-space: pre-line; overflow-wrap: anywhere; }
.key-hint { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; margin-top: 6px; }
.created-key { margin-top: 20px; }
</style>
