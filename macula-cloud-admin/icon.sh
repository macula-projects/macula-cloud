#!/bin/bash
set -euo pipefail


# =================================
# --- 功能介绍 ---
# 快速添加/删除三方 icon 的工具
# scui 扩展图标：https://python-abc.xyz/scui-doc/demo/#/vab/iconfont
# 阿里 iconfont 地址：https://www.iconfont.cn/

# --- 参数介绍 ---
# $1 add/del, 对应添加和删除
# $2, 新图标的名称，首字母大写，驼峰格式，比如 BugFill
# $3, 完整 svg 标签，直接从 iconfont 复制即可，需要使用 '单引号' 引起来
# =================================

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
icon_dir="$script_dir/src/assets/icons"
action="${1:-}"
name="${2:-}"

remove_export() {
  awk -v icon_name="$name" '
    $0 !~ ("^export[[:space:]]*\\{[[:space:]]*default[[:space:]]+as[[:space:]]+" icon_name "[[:space:]]*\\}")
  ' "$icon_dir/index.js"
}

if [[ "$action" == "add" ]]; then
  if [[ $# -ne 3 ]]; then
    echo "usage: $0 add {name} '<svg>...</svg>'" >&2
    echo "- name: 图标名称，首字母大写，比如 BugFill；同名会进行覆盖替换"
    exit 1
  fi
  if [[ "$name" == "Vue" || ! "$name" =~ ^[A-Z][A-Za-z0-9]*$ ]]; then
    echo "不能覆盖模板图标，且图标名称必须是首字母大写的字母数字标识符" >&2
    exit 1
  fi

  svg="$3"
  svg_open='^<svg([[:space:]]|>)'
  svg_close='</svg>$'
  if [[ ! "$svg" =~ $svg_open || ! "$svg" =~ $svg_close ]]; then
    echo "请输入完整的 <svg>...</svg> 标签" >&2
    exit 1
  fi

  temp_dir="$(mktemp -d "$icon_dir/.icon-tmp.XXXXXX")"
  trap 'rm -r "$temp_dir"' EXIT
  printf '%s\n' "$svg" > "$temp_dir/svg"
  awk -v svg_file="$temp_dir/svg" '
    function emit_svg( line) {
      while ((getline line < svg_file) > 0) print line
      close(svg_file)
    }
    /<svg([[:space:]>])/ {
      if (replaced) { invalid = 1; exit }
      replaced = 1
      emit_svg()
      if ($0 !~ /<\/svg>/) inside_svg = 1
      next
    }
    inside_svg {
      if ($0 ~ /<\/svg>/) inside_svg = 0
      next
    }
    { print }
    END { if (!replaced || inside_svg || invalid) exit 1 }
  ' "$icon_dir/Vue.vue" > "$temp_dir/$name.vue"
  remove_export > "$temp_dir/index.js"
  printf "export { default as %s } from './%s.vue'\n" "$name" "$name" >> "$temp_dir/index.js"
  mv "$temp_dir/$name.vue" "$icon_dir/$name.vue"
  mv "$temp_dir/index.js" "$icon_dir/index.js"

  icon=$(echo "ScIcon$name" | sed -E 's/([A-Z])/-\1/g' | sed -E 's/^-//' | tr 'A-Z' 'a-z')
  echo "$icon"
  echo "<el-button icon=\"$icon\"></el-button>"
  echo "<el-icon><$icon /></el-icon>"
  echo "<el-icon><component is=\"$icon\" /></el-icon>"

elif [[ "$action" == "del" ]]; then
  if [[ "$name" == "Vue" || ! "$name" =~ ^[A-Z][A-Za-z0-9]*$ ]]; then
    echo "不能删除模板图标，且图标名称必须是首字母大写的字母数字标识符" >&2
    exit 1
  fi
  temp_dir="$(mktemp -d "$icon_dir/.icon-tmp.XXXXXX")"
  trap 'rm -r "$temp_dir"' EXIT
  remove_export > "$temp_dir/index.js"
  mv "$temp_dir/index.js" "$icon_dir/index.js"
  rm -f "$icon_dir/$name.vue"
  echo "删除成功"
elif [[ "$action" == "ls" ]]; then
  echo "$icon_dir"
  ls -l "$icon_dir"
elif [[ "$action" == "url" ]]; then
  echo "https://www.iconfont.cn/"
elif [[ "$action" == "eg" || "$action" == "ex" ]]; then
  icon="sc-icon-bug-fill"
  echo "<el-button icon=\"$icon\"></el-button>"
  echo "<el-icon><$icon /></el-icon>"
  echo "<el-icon><component is=\"$icon\" /></el-icon>"
else
  echo "第一个参数不对，只允许下列值："
  echo "- 添加/替换: $0 add {name} '<svg>...</svg>'"
  echo "- 删除: $0 del {name}"
  echo "- 列表: $0 ls"
  echo "- 图标地址: $0 url"
  echo "- 示例用法: $0 eg/ex"
fi
