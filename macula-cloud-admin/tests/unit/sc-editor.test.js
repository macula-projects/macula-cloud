import {flushPromises, mount} from '@vue/test-utils'
import {afterEach, describe, expect, it, vi} from 'vitest'

vi.mock('@/api', () => ({
    default: {common: {upload: {post: vi.fn()}}, common_common: {upload: {post: vi.fn()}}}
}))
vi.mock('@/utils/request', () => ({default: {get: vi.fn()}}))

import API from '@/api'
import ScEditor from '@/components/scEditor/index.vue'
import ScForm from '@/components/scForm/index.vue'

const mounted = []

function createEditor(props = {}) {
    const wrapper = mount(ScEditor, {props})
    mounted.push(wrapper)
    return wrapper
}

afterEach(() => {
    for (const wrapper of mounted.splice(0)) wrapper.unmount()
    vi.restoreAllMocks()
})

describe('open-source rich text editor', () => {
    it('keeps HTML v-model and read-only state', async () => {
        const wrapper = createEditor({modelValue: '<p>原内容</p>', placeholder: '请输入'})
        expect(wrapper.vm.editor.getHTML()).toContain('原内容')

        wrapper.vm.editor.commands.setContent('<p><strong>新内容</strong></p>')
        expect(wrapper.emitted('update:modelValue')?.at(-1)[0]).toContain('<strong>新内容</strong>')

        await wrapper.setProps({modelValue: '<p>外部更新</p>', disabled: true})
        expect(wrapper.vm.editor.getHTML()).toContain('外部更新')
        expect(wrapper.vm.editor.isEditable).toBe(false)
        expect(wrapper.find('button').attributes('disabled')).toBeDefined()
    })

    it('uploads images and inserts templates, tables, media and page breaks', async () => {
        const wrapper = createEditor({templates: [{title: '通知', content: '<p>模板内容</p>'}]})
        API.common.upload.post.mockResolvedValue({data: {src: 'https://example.com/upload.png'}})

        const input = wrapper.find('input[type="file"]')
        Object.defineProperty(input.element, 'files', {configurable: true, value: [new File(['a'], 'a.png', {type: 'image/png'})]})
        await input.trigger('change')
        await flushPromises()
        expect(wrapper.vm.editor.getHTML()).toContain('https://example.com/upload.png')

        await wrapper.find('select[aria-label="插入模板"]').setValue('0')
        expect(wrapper.vm.editor.getHTML()).toContain('模板内容')

        await wrapper.findAll('button').find(button => button.text() === '表格').trigger('click')
        expect(wrapper.vm.editor.getHTML()).toContain('<table')

        vi.spyOn(window, 'prompt').mockReturnValue('https://example.com/video.mp4')
        await wrapper.findAll('button').find(button => button.text() === '媒体').trigger('click')
        expect(wrapper.vm.editor.getHTML()).toContain('<video')

        await wrapper.findAll('button').find(button => button.text() === '分页符').trigger('click')
        expect(wrapper.vm.editor.getHTML()).toContain('data-pagebreak')
    })

    it('edits source and previews it in a sandboxed frame', async () => {
        const wrapper = createEditor({modelValue: '<p>开始</p>'})
        await wrapper.findAll('button').find(button => button.text() === '源码').trigger('click')
        await wrapper.find('textarea[aria-label="HTML 源码"]').setValue('<p>源码更新</p>')
        await wrapper.findAll('button').find(button => button.text() === '完成源码').trigger('click')
        expect(wrapper.vm.editor.getHTML()).toContain('源码更新')

        await wrapper.findAll('button').find(button => button.text() === '预览').trigger('click')
        expect(wrapper.find('iframe[title="富文本预览"]').attributes('sandbox')).toBe('')
        expect(wrapper.find('iframe[title="富文本预览"]').attributes('srcdoc')).toContain('源码更新')
    })

    it('loads common saved HTML and rejects executable media URLs', async () => {
        const wrapper = createEditor({modelValue: '<h2>标题</h2><p><strong>加粗</strong> <a href="https://example.com">链接</a></p><table><tbody><tr><td>单元格</td></tr></tbody></table>'})
        const html = wrapper.vm.editor.getHTML()
        expect(html).toContain('<h2>标题</h2>')
        expect(html).toContain('<strong>加粗</strong>')
        expect(html).toContain('https://example.com')
        expect(html).toContain('<table')

        vi.spyOn(window, 'prompt').mockReturnValue('javascript:alert(1)')
        await wrapper.findAll('button').find(button => button.text() === '媒体').trigger('click')
        expect(wrapper.vm.editor.getHTML()).not.toContain('javascript:')
    })

    it('passes HTML edits through the dynamic form v-model', async () => {
        const slot = {template: '<div><slot /></div>'}
        const wrapper = mount(ScForm, {
            props: {
                modelValue: {body: '<p>原始内容</p>'},
                config: {formItems: [{component: 'editor', name: 'body', label: '正文', value: '', rules: []}]}
            },
            global: {components: {ScEditor}, stubs: {
                ElForm: slot, ElRow: slot, ElCol: slot, ElFormItem: slot, ElSkeleton: slot, ElButton: slot
            }}
        })
        mounted.push(wrapper)
        await flushPromises()
        const editor = wrapper.findComponent(ScEditor)
        expect(editor.exists()).toBe(true)
        expect(editor.vm.editor.getHTML()).toContain('原始内容')

        editor.vm.editor.commands.setContent('<p>表单更新</p>')
        await flushPromises()
        expect(wrapper.emitted('update:modelValue')?.at(-1)[0].body).toContain('表单更新')
    })
})
