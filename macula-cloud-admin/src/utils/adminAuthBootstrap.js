import auth from '@/api/model/common/auth'
import tool from './tool'
import {useTenantStore} from '@/stores/tenant'
import {assertCurrent, authTask, clearAuth, commitSession, readSession, ownsSession, onAuthCleared} from './adminAuthSession'

let pending
let readySessionId
onAuthCleared(() => { readySessionId = undefined; pending = undefined })

/** 保留正向/排除角色规则，未声明角色的节点可见。 */
export function filterAuthorizedMenu(nodes, roles) {
    return tool.treeFilter(nodes, node => {
        const required = node.meta?.roles
        if (!Array.isArray(required) || !required.length) return true
        return roles.some(role => !role.startsWith('!') && required.includes(role)) &&
            !roles.some(role => role.startsWith('!') && required.includes(role.slice(1)))
    })
}
function resultData(response) {
    if (!response?.success || response.data == null) throw new Error('AUTH_INITIALIZATION_FAILED')
    return response.data
}
/** 候选 Token 只存在内存中，全部权限初始化完成后才提交。 */
export async function initializeSession(session) {
    const generation = session.generation
    const signal = authTask(generation)
    const options = {signal, authContext: session, skipTenant: true, showErrorNotification: false,
        headers: {Authorization: `Bearer ${session.accessToken}`}}
    try {
        const user = resultData(await auth.getUserInfo.get(options))
        assertCurrent(generation, signal)
        if (!user.username || !Array.isArray(user.roles) || !Array.isArray(user.perms) ||
            user.enabled === false || user.status === false) throw new Error('AUTH_INVALID_USER')
        const tenants = resultData(await auth.getTenantOptions.get(options))
        assertCurrent(generation, signal)
        if (!Array.isArray(tenants) || !tenants.length || tenants.some(item => item.value == null || !item.label)) {
            throw new Error('AUTH_NO_TENANT')
        }
        const oldTenantId = tool.data.get('tenantId')
        const tenant = tenants.find(item => String(item.value) === String(oldTenantId)) || tenants[0]
        const routes = resultData(await auth.getRoutes.get({...options,
            headers: {...options.headers, tenantId: String(tenant.value)}}))
        assertCurrent(generation, signal)
        if (!Array.isArray(routes)) throw new Error('AUTH_NO_PERMISSION')
        const menu = filterAuthorizedMenu(routes, user.roles)
        if (!menu.length || !user.roles.length || !user.perms.length) throw new Error('AUTH_NO_PERMISSION')
        // 同一同步提交段内不再 await；存储失败会清理本代次，不能留下半个登录。
        tool.data.set('USER_INFO', user)
        tool.data.set('MENU', menu)
        tool.data.set('PERMISSIONS', user.perms)
        const tenantStore = useTenantStore()
        tenantStore.resetAuth()
        tenantStore.pushTenantOptions(tenants)
        tenantStore.updateTenantId(tenant.value)
        tenantStore.updateTenantLabel(tenant.label)
        commitSession(session)
        readySessionId = session.sessionId
        return session
    } catch (error) {
        // 依赖故障不能撤销已提交会话；新登录候选仍须完整初始化后才能提交。
        if ((error.status ?? error.response?.status) !== 503 || !ownsSession(session)) clearAuth(generation)
        throw error
    }
}
export function ensureSessionInitialized() {
    const session = readSession()
    if (!session) return Promise.reject(new Error('AUTH_REQUIRED'))
    if (readySessionId === session.sessionId) return Promise.resolve(session)
    if (pending?.sessionId === session.sessionId) return pending.promise
    const promise = initializeSession(session).then(value => {
        if (!ownsSession(value)) throw new Error('AUTH_SUPERSEDED')
        return value
    }).finally(() => { if (pending?.promise === promise) pending = undefined })
    pending = {sessionId: session.sessionId, promise}
    return promise
}
