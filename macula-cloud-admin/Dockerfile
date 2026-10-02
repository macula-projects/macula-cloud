# syntax=docker/dockerfile:1.7
FROM node:20.19.5-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_MODE=docker
ARG VITE_APP_API_BASEURL=/api
ENV VITE_APP_API_BASEURL=${VITE_APP_API_BASEURL}
RUN npm run build -- --mode "${VITE_MODE}"

FROM nginxinc/nginx-unprivileged:1.27.5-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY nginx/templates/config.js.template /opt/macula-cloud-admin/config.js.template
COPY nginx/entrypoint.d/25-admin-runtime-config.sh /docker-entrypoint.d/25-admin-runtime-config.sh
COPY --chown=101:101 --from=build /workspace/dist /usr/share/nginx/html
USER root
RUN chmod 755 /docker-entrypoint.d/25-admin-runtime-config.sh \
    && chown 101:101 /usr/share/nginx/html
USER 101:101
EXPOSE 8080
