import config from "@/config";
import http from "@/utils/request";

const url = `${config.API_URL}/${config.MODEL.system}/api/v1/security-settings`;
export default {
  get: () => http.get(url),
  save: (data) => http.put(url, data),
};
