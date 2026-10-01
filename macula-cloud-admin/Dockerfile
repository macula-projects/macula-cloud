# syntax=docker/dockerfile:1.7
FROM node:20.19.5-alpine AS build
WORKDIR /workspace
ARG VITE_APP_API_BASEURL=/api
ARG VITE_APP_IAM_URL=/iam
ENV VITE_APP_API_BASEURL=${VITE_APP_API_BASEURL} \
    VITE_APP_IAM_URL=${VITE_APP_IAM_URL}
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.27.5-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
USER 101:101
EXPOSE 8080
