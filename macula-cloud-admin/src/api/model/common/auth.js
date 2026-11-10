import config from '@/config'
import http from '@/utils/request'

const root = `${config.API_URL}/${config.MODEL.system}/api/v1`
function endpoint(path) {
    return {url: root + path, get(options = {}) { return http.get(this.url, {}, options) }}
}
export default {
    getUserInfo: endpoint('/users/me'),
    getTenantOptions: endpoint('/tenants/options'),
    getRoutes: endpoint('/menus/routes')
}
