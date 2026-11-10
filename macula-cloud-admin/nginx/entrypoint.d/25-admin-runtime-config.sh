#!/bin/sh
set -eu
encode_config_value() { printf '%s' "$1" | base64 | tr -d '\n'; }
export ADMIN_OAUTH_CLIENT_ID_B64="$(encode_config_value "${VITE_APP_OAUTH_CLIENT_ID:?VITE_APP_OAUTH_CLIENT_ID is required}")"
export ADMIN_OAUTH_AUTHORITY_B64="$(encode_config_value "${VITE_APP_OAUTH_AUTHORITY:?VITE_APP_OAUTH_AUTHORITY is required}")"
export ADMIN_PUBLIC_URL_B64="$(encode_config_value "${VITE_APP_ADMIN_PUBLIC_URL:?VITE_APP_ADMIN_PUBLIC_URL is required}")"
export ADMIN_OAUTH_SCOPE_B64="$(encode_config_value "${VITE_APP_OAUTH_SCOPE:-openid profile}")"
template=/opt/macula-cloud-admin/config.js.template
target=/usr/share/nginx/html/config.js
variables='${ADMIN_OAUTH_CLIENT_ID_B64} ${ADMIN_OAUTH_AUTHORITY_B64} ${ADMIN_PUBLIC_URL_B64} ${ADMIN_OAUTH_SCOPE_B64}'
envsubst "$variables" < "$template" > "${target}.tmp"
mv "${target}.tmp" "$target"
