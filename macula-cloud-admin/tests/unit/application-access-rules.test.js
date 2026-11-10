import { describe, expect, it } from 'vitest'
import { normalizeUrlRules, normalizeIpRules } from '@/utils/applicationAccessRules'

describe('multiline application access rules', () => {
  it('normalizes comma and all newline styles for URL', () => {
    expect(normalizeUrlRules(' GET:/a,\r\nPOST:/b\r PUT:/c\n ')).toBe('GET:/a\nPOST:/b\nPUT:/c')
    expect(normalizeUrlRules(null)).toBe('')
    expect(normalizeUrlRules(' \n ')).toBe('')
    expect(() => normalizeUrlRules(',,,')).toThrow()
    expect(() => normalizeUrlRules('x'.repeat(2001))).toThrow()
  })
  it('keeps IPv6 colons and CIDR intact and only separates IP by newlines', () => {
    expect(normalizeIpRules(' 192.0.2.0/24\r\n\n2001:db8::/32 ')).toBe('192.0.2.0/24\n2001:db8::/32')
    expect(normalizeIpRules(undefined)).toBe('')
    expect(normalizeIpRules(' \r ')).toBe('')
    // 不在客户端猜测或拆分错误 IP，交服务端统一拒绝。
    expect(normalizeIpRules('192.0.2.1,192.0.2.2')).toBe('192.0.2.1,192.0.2.2')
  })
})
