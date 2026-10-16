import config from "@/config";
import http from "@/utils/request";

const url = `${config.API_URL}/${config.MODEL.system}/api/v1/oauth2-clients`;
export default {
  list: (params) => http.get(url, params),
  get: (id) => http.get(`${url}/${id}`),
  create: (data) => http.post(url, data),
  update: (id, data) => http.put(`${url}/${id}`, data),
  resetSecret: (id, secret) => http.put(`${url}/${id}/secret`, { secret }),
  remove: (id) => http.delete(`${url}/${id}`),
};
