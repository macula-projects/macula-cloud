import config from "@/config";
import http from "@/utils/request";

const url = `${config.API_URL}/${config.MODEL.system}/api/v1/security-policy`;

export default {
  get: () => http.get(url),
  saveBasic: (data) => http.put(`${url}/basic`, data),
  saveDefense: (data) => http.put(`${url}/defense`, data),
  savePassword: (data) => http.put(`${url}/password`, data),
};
