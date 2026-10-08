import config from "@/config"
import http from "@/utils/request"

const baseUrl = `${config.API_URL}/${config.MODEL.tinyid}/api/v1/admin`

export default {
	applications: {
		list: params => http.get(`${baseUrl}/apps`, params),
		get: appId => http.get(`${baseUrl}/apps/${appId}`),
		create: data => http.post(`${baseUrl}/apps`, data),
		updateRemark: (appId, data) => http.put(`${baseUrl}/apps/${appId}/remark`, data),
		addBusinesses: (appId, data) => http.post(`${baseUrl}/apps/${appId}/businesses`, data),
		delete: appId => http.delete(`${baseUrl}/apps/${appId}`)
	},
	businesses: {
		list: params => http.get(`${baseUrl}/businesses`, params),
		get: bizType => http.get(`${baseUrl}/businesses/${encodeURIComponent(bizType)}`),
		consistency: bizType => http.get(`${baseUrl}/businesses/${encodeURIComponent(bizType)}/consistency`),
		create: data => http.post(`${baseUrl}/businesses`, data),
		delete: bizType => http.delete(`${baseUrl}/businesses/${encodeURIComponent(bizType)}`)
	},
	dataSources: () => http.get(`${baseUrl}/data-sources`),
	auditLogs: params => http.get(`${baseUrl}/audit-logs`, params)
}
