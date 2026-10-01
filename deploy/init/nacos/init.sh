#!/bin/sh
set -eu

nacos_base_url=${NACOS_BASE_URL:-http://nacos:8080}
nacos_namespace=${NACOS_NAMESPACE:-MACULA5}
force_config=${FORCE_NACOS_CONFIG:-false}
mysql_app_user=${MYSQL_APP_USER:?MYSQL_APP_USER is required}
mysql_app_password=${MYSQL_APP_PASSWORD:?MYSQL_APP_PASSWORD is required}

for attempt in $(seq 1 60); do
  readiness=$(curl --fail --silent --show-error "${nacos_base_url}/v3/console/health/readiness" 2>/dev/null || true)
  if printf '%s' "$readiness" | grep -Fq '"data":"ok"'; then
    break
  fi
  if [ "$attempt" -eq 60 ]; then
    echo "Nacos did not become ready" >&2
    exit 1
  fi
  sleep 2
done

namespaces=$(curl --fail --silent --show-error "${nacos_base_url}/v3/console/core/namespace/list")
if ! printf '%s' "$namespaces" | grep -Fq "\"namespace\":\"${nacos_namespace}\""; then
  create_response=$(curl --fail --silent --show-error -X POST "${nacos_base_url}/v3/console/core/namespace" \
    --data-urlencode "customNamespaceId=${nacos_namespace}" \
    --data-urlencode "namespaceName=${nacos_namespace}" \
    --data-urlencode "namespaceDesc=Macula Cloud local development")
  printf '%s' "$create_response" | grep -Fq '"data":true'
  echo "Created Nacos namespace ${nacos_namespace}"
else
  echo "Nacos namespace ${nacos_namespace} already exists"
fi

publish_config() {
  data_id=$1
  group=$2
  file=$3
  publish_file=$file

  escaped_user=$(printf '%s' "$mysql_app_user" | sed 's/[\\&|]/\\&/g')
  escaped_password=$(printf '%s' "$mysql_app_password" | sed 's/[\\&|]/\\&/g')
  publish_file=/tmp/$(basename "$file")
  sed -e "s|__MYSQL_APP_USER__|${escaped_user}|g" \
    -e "s|__MYSQL_APP_PASSWORD__|${escaped_password}|g" "$file" > "$publish_file"

  if [ "$force_config" != "true" ]; then
    existing_config=$(curl --fail --silent --show-error --get \
      "${nacos_base_url}/v3/console/cs/config" \
      --data-urlencode "dataId=${data_id}" \
      --data-urlencode "groupName=${group}" \
      --data-urlencode "namespaceId=${nacos_namespace}")
    if printf '%s' "$existing_config" | grep -Fq "\"dataId\":\"${data_id}\""; then
      echo "Skipping existing Nacos config ${group}/${data_id}"
      return
    fi
  fi

  publish_response=$(curl --fail --silent --show-error -X POST "${nacos_base_url}/v3/console/cs/config" \
    --data-urlencode "dataId=${data_id}" \
    --data-urlencode "groupName=${group}" \
    --data-urlencode "namespaceId=${nacos_namespace}" \
    --data-urlencode "type=properties" \
    --data-urlencode "content@${publish_file}")
  printf '%s' "$publish_response" | grep -Fq '"data":true'
  echo "Published Nacos config ${group}/${data_id}"
}

publish_config seata.properties SEATA_GROUP /init/seata.properties
echo "Nacos initialization completed"
