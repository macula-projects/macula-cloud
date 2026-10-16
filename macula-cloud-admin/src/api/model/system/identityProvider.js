import config from "@/config";
import http from "@/utils/request";

const url = `${config.API_URL}/${config.MODEL.system}/api/v1/identity-providers`;
export default {
  catalog: () => http.get(`${url}/catalog`),
  list: (params) => http.get(url, params),
  get: (id) => http.get(`${url}/${id}`),
  create: (data) => http.post(url, data),
  update: (id, data) => http.put(`${url}/${id}`, data),
  remove: (id) => http.delete(`${url}/${id}`),
};
