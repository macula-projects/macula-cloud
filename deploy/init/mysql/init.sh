#!/bin/sh
set -eu

mysql_host=${MYSQL_HOST:-mysql}
mysql_root_password=${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}
mysql_app_user=${MYSQL_APP_USER:-macula}
mysql_app_password=${MYSQL_APP_PASSWORD:?MYSQL_APP_PASSWORD is required}

case "$mysql_app_user" in
  *[!A-Za-z0-9_.-]*) echo "MYSQL_APP_USER contains unsupported characters" >&2; exit 1 ;;
esac

sql_escape() {
  printf '%s' "$1" | sed "s/'/''/g"
}

mysql_root() {
  mysql --protocol=TCP -h "$mysql_host" -uroot -p"$mysql_root_password" "$@"
}

for attempt in $(seq 1 60); do
  if mysql_root -e 'SELECT 1' >/dev/null 2>&1; then
    break
  fi
  if [ "$attempt" -eq 60 ]; then
    echo "MySQL did not become ready" >&2
    exit 1
  fi
  sleep 2
done

escaped_password=$(sql_escape "$mysql_app_password")
mysql_root <<SQL
CREATE USER IF NOT EXISTS '${mysql_app_user}'@'%' IDENTIFIED BY '${escaped_password}';
ALTER USER '${mysql_app_user}'@'%' IDENTIFIED BY '${escaped_password}';
CREATE DATABASE IF NOT EXISTS \`macula-system\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS \`macula-tinyid\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS \`seata\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS \`macula-snailjob\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS \`nacos\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON \`macula-system\`.* TO '${mysql_app_user}'@'%';
GRANT ALL PRIVILEGES ON \`macula-tinyid\`.* TO '${mysql_app_user}'@'%';
GRANT ALL PRIVILEGES ON \`seata\`.* TO '${mysql_app_user}'@'%';
GRANT ALL PRIVILEGES ON \`macula-snailjob\`.* TO '${mysql_app_user}'@'%';
GRANT ALL PRIVILEGES ON \`nacos\`.* TO '${mysql_app_user}'@'%';
GRANT SELECT ON performance_schema.user_variables_by_thread TO '${mysql_app_user}'@'%';
FLUSH PRIVILEGES;
SQL

init_schema() {
  database=$1
  marker_table=$2
  sql_file=$3
  exists=$(mysql_root -Nse "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${database}' AND table_name='${marker_table}'")
  if [ "$exists" = "0" ]; then
    echo "Initializing ${database} from ${sql_file}"
    mysql_root --database="$database" < "$sql_file"
  else
    echo "Skipping ${database}; marker table ${marker_table} already exists"
  fi
}

init_schema nacos config_info /init/nacos-mysql.sql

mysql_root --database=nacos <<'SQL'
INSERT IGNORE INTO users (username, password, enabled)
VALUES ('nacos', '$2a$10$naftHYJp6.QvSL.bQyhESOA1yD/U1hYsYMtFPprh3DCVPu13d.l2a', TRUE);
INSERT IGNORE INTO roles (username, role) VALUES ('nacos', 'ROLE_ADMIN');
SQL

echo "MySQL initialization completed"
