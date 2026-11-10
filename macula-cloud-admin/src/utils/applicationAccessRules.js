/** URL 兼容历史逗号输入；持久化与展示统一为每行一条。 */
export function normalizeUrlRules(value) {
  return normalizeRules(value, /[,\r\n]/)
}

/** IP 只按换行拆分，字面量与 CIDR 的最终校验由服务端公共规则执行。 */
export function normalizeIpRules(value) {
  return normalizeRules(value, /[\r\n]/)
}

function normalizeRules(value, separator) {
  const text = value == null ? '' : String(value)
  if (text.length > 2000) throw new Error('访问规则最多 2000 字符')
  if (!text.trim()) return ''
  const rules = text.split(separator).map(rule => rule.trim()).filter(Boolean)
  if (!rules.length) throw new Error('请填写有效的访问规则')
  return rules.join('\n')
}
