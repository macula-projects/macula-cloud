import config from "@/config";
import http from "@/utils/request";

const url = `${config.API_URL}/${config.MODEL.system}/api/v1/message-settings`;
export default {
  provider: (channel) => http.get(`${url}/providers/${channel}`),
  saveProvider: (channel, data) => http.put(`${url}/providers/${channel}`, data),
  templates: (channel, providerType, languageCode = "zh-CN") =>
    http.get(`${url}/templates/${channel}`, { providerType, languageCode }),
  saveTemplate: (channel, scene, data) =>
    http.put(`${url}/templates/${channel}/${scene}`, data),
  preview: (scene, variables = {}) =>
    http.post(`${url}/templates/MAIL/${scene}/preview`, { variables }),
  test: (data) => http.post(`${url}/test`, data),
};
