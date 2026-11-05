import { afterEach, describe, expect, it, vi } from 'vitest'
import ApiKeys from '@/views/system/application/apiKeys.vue'
import Application from '@/views/system/application/index.vue'

const messages = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn(), confirm: vi.fn() }))
vi.mock('element-plus', () => ({ ElMessage: messages, ElMessageBox: { confirm: messages.confirm } }))

function createVm(overrides = {}) {
  const vm = { ...ApiKeys.data(), ...overrides }
  Object.entries(ApiKeys.methods).forEach(([name, fn]) => { vm[name] = fn.bind(vm) })
  const api = {
    list: vi.fn().mockResolvedValue({ success: true, data: { records: [], total: 0 } }),
    create: vi.fn().mockResolvedValue({ success: true, data: { id: '9', apiKey: 'sk-synthetic-new' } }),
    update: vi.fn().mockResolvedValue({ success: true }),
    secret: vi.fn().mockResolvedValue({ success: true, data: { apiKey: 'sk-synthetic-view' } }),
    remove: vi.fn().mockResolvedValue({ success: true })
  }
  vm.$API = { system_application: { apiKeys: api } }
  vm.application = { id: '34', applicationName: 'Sample' }
  vm.visible = true
  return { vm, api }
}

afterEach(() => vi.clearAllMocks())

describe('application API Key management', () => {
  it('opens the current application from the application table', () => {
    const open = vi.fn()
    const vm = { dialog: {}, $refs: { apiKeys: { open } }, $nextTick: fn => fn() }
    Application.methods.table_api_keys.call(vm, { id: '34' })
    expect(vm.dialog.apiKeys).toBe(true)
    expect(open).toHaveBeenCalledWith({ id: '34' })
  })

  it('loads a masked paginated list, clears displayed secrets and supports empty state', async () => {
    const { vm, api } = createVm({ revealed: { 9: 'sk-old-displayed' } })
    api.list.mockResolvedValueOnce({ success: true, data: { records: [{ id: '9', maskedKey: 'sk-ab****xy' }], total: 1 } })
    await vm.load()
    expect(api.list).toHaveBeenCalledWith('34', { pageNum: 1, pageSize: 20 })
    expect(vm.rows).toEqual([{ id: '9', maskedKey: 'sk-ab****xy' }])
    expect(vm.revealed).toEqual({})
    expect(vm.loading).toBe(false)
    await vm.load()
    expect(vm.rows).toEqual([])
    expect(vm.total).toBe(0)
  })

  it('reports list failure and always stops loading', async () => {
    const { vm, api } = createVm()
    api.list.mockRejectedValueOnce(new Error('synthetic network failure'))
    await vm.load()
    expect(vm.error).toContain('加载失败')
    expect(vm.loading).toBe(false)
    expect(vm.rows).toEqual([])
  })

  it('creates a Key with independent URL and shows the copy dialog', async () => {
    const { vm, api } = createVm()
    vm.form = { id: null, remark: ' Integration ', accessPath: ' POST:/key/** ' }
    await vm.save()
    expect(api.create).toHaveBeenCalledWith('34', { remark: 'Integration', accessPath: 'POST:/key/**' })
    expect(vm.createdVisible).toBe(true)
    expect(vm.createdKey).toBe('sk-synthetic-new')
    expect(vm.saving).toBe(false)
  })

  it('saves an empty independent URL explicitly without sending a secret', async () => {
    const { vm, api } = createVm()
    vm.form = { id: '9', remark: 'Note', accessPath: '' }
    await vm.save()
    expect(api.update).toHaveBeenCalledWith('34', '9', { remark: 'Note', accessPath: '' })
    expect(api.create).not.toHaveBeenCalled()
    expect(vm.createdVisible).toBe(false)
  })

  it('blocks duplicate submit and keeps editor on failure', async () => {
    const { vm, api } = createVm({ saving: true, editing: true })
    await vm.save()
    expect(api.create).not.toHaveBeenCalled()
    vm.saving = false
    api.create.mockRejectedValueOnce(new Error('synthetic'))
    await vm.save()
    expect(vm.editing).toBe(true)
    expect(vm.saving).toBe(false)
    expect(messages.error).toHaveBeenCalled()
  })

  it('fetches and hides a full secret on demand and copies through authorization', async () => {
    const { vm, api } = createVm()
    await vm.toggle({ id: '9' })
    expect(vm.revealed['9']).toBe('sk-synthetic-view')
    await vm.toggle({ id: '9' })
    expect(vm.revealed['9']).toBeUndefined()
    vm.writeClipboard = vi.fn()
    await vm.copy({ id: '9' })
    expect(api.secret).toHaveBeenCalledTimes(2)
    expect(vm.writeClipboard).toHaveBeenCalledWith('sk-synthetic-view')
  })

  it('ignores a secret response arriving after the drawer closes', async () => {
    const { vm, api } = createVm()
    let resolve
    api.secret.mockReturnValueOnce(new Promise(done => { resolve = done }))
    const pending = vm.toggle({ id: '9' })
    vm.clearSecrets()
    resolve({ success: true, data: { apiKey: 'sk-delayed-secret' } })
    await pending
    expect(vm.revealed).toEqual({})
    expect(vm.createdKey).toBe('')
  })

  it('clears every plaintext value and modal on close', () => {
    const { vm } = createVm({ revealed: { 9: 'sk-visible' }, createdKey: 'sk-created', createdVisible: true, editing: true })
    vm.clearSecrets()
    expect(vm.revealed).toEqual({})
    expect(vm.createdKey).toBe('')
    expect(vm.createdVisible).toBe(false)
    expect(vm.editing).toBe(false)
  })

  it('revokes after confirmation and clears an empty last page', async () => {
    const { vm, api } = createVm({ rows: [{ id: '9' }], pageNum: 2, revealed: { 9: 'sk-visible' } })
    messages.confirm.mockResolvedValueOnce('confirm')
    await vm.remove({ id: '9' })
    expect(api.remove).toHaveBeenCalledWith('34', '9')
    expect(vm.pageNum).toBe(1)
    expect(vm.revealed).toEqual({})
  })

  it('does not revoke when confirmation is cancelled', async () => {
    const { vm, api } = createVm()
    messages.confirm.mockRejectedValueOnce('cancel')
    await vm.remove({ id: '9' })
    expect(api.remove).not.toHaveBeenCalled()
    expect(messages.error).not.toHaveBeenCalled()
    expect(vm.busy).toEqual({})
  })
})


describe('API Key creation time', () => {
  it('converts offsets to browser local time and keeps missing dates empty', () => {
    const format = ApiKeys.methods.formatCreateTime;
    const local = value => new Date(value).toLocaleString('sv-SE', { hour12: false });
    expect(format('2026-11-05T00:50:30Z')).toBe(local('2026-11-05T00:50:30Z'));
    expect(format('2026-11-05T08:50:30+08:00')).toBe(format('2026-11-05T00:50:30Z'));
    expect(format('2026-11-04T23:50:30Z')).toBe(local('2026-11-04T23:50:30Z'));
    expect(format(null)).toBe('—');
    expect(format('')).toBe('—');
    expect(format('invalid')).toBe('—');
  });
});
