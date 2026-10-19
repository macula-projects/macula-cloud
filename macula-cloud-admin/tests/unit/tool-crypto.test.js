import {webcrypto} from 'node:crypto'
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'

beforeEach(() => {
    vi.stubGlobal('crypto', webcrypto)
    localStorage.clear()
})

afterEach(() => vi.unstubAllGlobals())

describe('browser-native crypto utilities', () => {
    it('encrypts with a fresh IV and rejects modified or wrongly keyed ciphertext', async () => {
        const {default: tool} = await import('@/utils/tool')
        const key = await tool.crypto.AES.generateKey()
        const otherKey = await tool.crypto.AES.generateKey()
        const first = await tool.crypto.AES.encrypt('租户 secret 🔒', key)
        const second = await tool.crypto.AES.encrypt('租户 secret 🔒', key)

        expect(first).toMatch(/^gcm1:/)
        expect(second).not.toBe(first)
        expect(await tool.crypto.AES.decrypt(first, key)).toBe('租户 secret 🔒')
        await expect(tool.crypto.AES.decrypt(first, otherKey)).rejects.toThrow()

        const bytes = Uint8Array.from(atob(first.slice(5)), char => char.charCodeAt(0))
        bytes[bytes.length - 1] ^= 1
        const tampered = 'gcm1:' + btoa(String.fromCharCode(...bytes))
        await expect(tool.crypto.AES.decrypt(tampered, key)).rejects.toThrow()
        await expect(tool.crypto.AES.decrypt('legacy', key)).rejects.toThrow(/Unsupported/)
    })

    it('imports raw AES keys without embedding a key in the application', async () => {
        const {default: tool} = await import('@/utils/tool')
        const key = await tool.crypto.AES.importKey(new Uint8Array(32).fill(7))
        const encrypted = await tool.crypto.AES.encrypt('hello', key)

        expect(await tool.crypto.AES.decrypt(encrypted, key)).toBe('hello')
        expect(() => tool.crypto.AES.importKey(new Uint8Array(5))).toThrow(/16, 24, or 32/)
    })

    it('uses native UTF-8 Base64 and SHA-256', async () => {
        const {default: tool} = await import('@/utils/tool')
        const plain = '中文 + emoji 🚀'
        expect(tool.crypto.BASE64.decrypt(tool.crypto.BASE64.encrypt(plain))).toBe(plain)
        expect(await tool.crypto.SHA256('abc')).toBe(
            'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad'
        )
    })
})
