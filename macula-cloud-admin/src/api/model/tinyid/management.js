import config from "@/config"
import http from "@/utils/request"

const baseUrl = `${config.API_URL}/${config.MODEL.tinyid}/api/v1/admin`

export default {
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
