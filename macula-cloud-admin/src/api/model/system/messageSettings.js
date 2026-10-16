import config from "@/config";
import http from "@/utils/request";

const url = `${config.API_URL}/${config.MODEL.system}/api/v1/message-settings`;
export default {
  providers: (channel) => http.get(`${url}/providers`, { channel }),
  provider: (id) => http.get(`${url}/providers/${id}`),
  createProvider: (data) => http.post(`${url}/providers`, data),
  updateProvider: (id, data) => http.put(`${url}/providers/${id}`, data),
  removeProvider: (id) => http.delete(`${url}/providers/${id}`),
  templates: (channel, providerType) =>
    http.get(`${url}/templates`, { channel, providerType }),
  saveTemplate: (channel, data) =>
    http.put(`${url}/templates/${channel}`, data),
  preview: (channel, key, providerType, variables) =>
    http.post(
      `${url}/templates/${channel}/${key}/preview?providerType=${encodeURIComponent(
        providerType || ""
      )}`,
      variables
    ),
  test: (data) => http.post(`${url}/test`, data),
};
