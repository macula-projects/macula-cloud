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

    it('round-trips TinyMCE page break comments without losing saved separators', async () => {
        const legacyHtml = '<p>第一页</p><!-- pagebreak --><p>第二页</p>'
        const wrapper = createEditor({modelValue: legacyHtml})
        expect(wrapper.vm.editor.getHTML()).toContain('data-pagebreak')

        await wrapper.findAll('button').find(button => button.text() === '源码').trigger('click')
        expect(wrapper.find('textarea[aria-label="HTML 源码"]').element.value).toContain('<!-- pagebreak -->')
        await wrapper.findAll('button').find(button => button.text() === '预览').trigger('click')
        expect(wrapper.find('iframe[title="富文本预览"]').attributes('srcdoc')).toContain('data-pagebreak')
        await wrapper.find('textarea[aria-label="HTML 源码"]').setValue(`${legacyHtml}<p>补充</p>`)
        expect(wrapper.emitted('update:modelValue')?.at(-1)[0]).toContain('<!-- pagebreak -->')
        expect(wrapper.emitted('update:modelValue')?.at(-1)[0]).not.toContain('data-pagebreak')

        await wrapper.findAll('button').find(button => button.text() === '完成源码').trigger('click')
        await wrapper.findAll('button').find(button => button.text() === '分页符').trigger('click')
        expect(wrapper.emitted('update:modelValue')?.at(-1)[0].match(/<!-- pagebreak -->/g)).toHaveLength(2)
    })

    it('keeps a legacy page break embedded inside a paragraph', () => {
        const wrapper = createEditor({modelValue: '<p>前<!-- pagebreak -->后</p>'})
        expect(wrapper.vm.editor.getHTML()).toContain('data-pagebreak')
        wrapper.vm.editor.commands.insertContent('<p>补充</p>')
        expect(wrapper.emitted('update:modelValue')?.at(-1)[0]).toContain('<!-- pagebreak -->')
    })

    it('replaces a source draft when the form value changes externally', async () => {
        const wrapper = createEditor({modelValue: '<p>开始</p>'})
        await wrapper.findAll('button').find(button => button.text() === '源码').trigger('click')
        await wrapper.find('textarea[aria-label="HTML 源码"]').setValue('<p>草稿</p>')

        await wrapper.setProps({modelValue: '<p>外部更新</p>'})
        expect(wrapper.find('textarea[aria-label="HTML 源码"]').element.value).toBe('<p>外部更新</p>')
        await wrapper.findAll('button').find(button => button.text() === '完成源码').trigger('click')
        expect(wrapper.vm.editor.getHTML()).toContain('外部更新')
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

    it('submits source edits without requiring a switch back to visual mode', async () => {
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
        await editor.findAll('button').find(button => button.text() === '源码').trigger('click')
        await editor.find('textarea[aria-label="HTML 源码"]').setValue('<p>源码新内容</p>')
        wrapper.vm.submit()

        expect(wrapper.emitted('submit')?.at(-1)[0].body).toContain('源码新内容')
    })

    it('does not insert an old upload after the form switches to a record with the same HTML', async () => {
        let finishUpload
        API.common.upload.post.mockReturnValue(new Promise(resolve => { finishUpload = resolve }))
        const slot = {template: '<div><slot /></div>'}
        const wrapper = mount(ScForm, {
            props: {
                modelValue: {id: 'A', body: '<p>相同正文</p>'},
                config: {formItems: [{component: 'editor', name: 'body', label: '正文', value: '', rules: []}]}
            },
            global: {components: {ScEditor}, stubs: {
                ElForm: slot, ElRow: slot, ElCol: slot, ElFormItem: slot, ElSkeleton: slot, ElButton: slot
            }}
        })
        mounted.push(wrapper)
        await flushPromises()

        const oldEditor = wrapper.findComponent(ScEditor)
        const file = new File(['image'], 'image.png', {type: 'image/png'})
        const request = oldEditor.vm.uploadImage({target: {files: [file], value: 'image.png'}})
        await wrapper.setProps({modelValue: {id: 'B', body: '<p>相同正文</p>'}})
        expect(wrapper.findComponent(ScEditor).vm).not.toBe(oldEditor.vm)

        finishUpload({data: {src: 'https://example.com/old-record.png'}})
        await request
        expect(wrapper.vm.form.id).toBe('B')
        expect(wrapper.vm.form.body).not.toContain('old-record.png')
        expect(wrapper.findComponent(ScEditor).vm.editor.getHTML()).not.toContain('old-record.png')

        const activeEditor = wrapper.findComponent(ScEditor).vm
        activeEditor.editor.commands.setContent('<p>记录 B 的本地编辑</p>')
        await flushPromises()
        await wrapper.setProps({modelValue: wrapper.vm.form})
        expect(wrapper.findComponent(ScEditor).vm).toBe(activeEditor)

        await wrapper.setProps({config: {formItems: [{component: 'editor', name: 'body', label: '正文', value: '', rules: []}]}})
        await flushPromises()
        expect(wrapper.findComponent(ScEditor).vm).not.toBe(activeEditor)
    })

    it('keeps every visual editing entry inert while source mode is active', async () => {
        const wrapper = createEditor({modelValue: '<p>原文</p>', templates: [{title: '通知', content: '<p>模板</p>'}]})
        await wrapper.findAll('button').find(button => button.text() === '源码').trigger('click')

        expect(wrapper.vm.editor.isEditable).toBe(false)
        for (const button of wrapper.findAll('.sceditor-toolbar button')) {
            if (['完成源码', '预览'].includes(button.text())) continue
            expect(button.attributes('disabled')).toBeDefined()
        }
        for (const control of wrapper.findAll('.sceditor-toolbar select, .sceditor-toolbar input')) {
            expect(control.attributes('disabled')).toBeDefined()
        }

        wrapper.vm.run('insertContent', {type: 'pageBreak'})
        const prompt = vi.spyOn(window, 'prompt')
        wrapper.vm.setLink()
        wrapper.vm.insertMedia()
        wrapper.vm.insertTemplate({target: {value: '0'}})
        expect(prompt).not.toHaveBeenCalled()
        expect(wrapper.vm.editor.getHTML()).not.toContain('data-pagebreak')
        expect(wrapper.vm.editor.getHTML()).not.toContain('模板')

        await wrapper.setProps({disabled: true})
        expect(wrapper.find('textarea[aria-label="HTML 源码"]').attributes('disabled')).toBeDefined()
        await wrapper.setProps({disabled: false})
        expect(wrapper.vm.editor.isEditable).toBe(false)
        await wrapper.find('textarea[aria-label="HTML 源码"]').setValue('<p>原文新字</p>')
        expect(wrapper.emitted('update:modelValue')?.at(-1)[0]).toContain('原文新字')

        await wrapper.findAll('button').find(button => button.text() === '完成源码').trigger('click')
        expect(wrapper.vm.editor.isEditable).toBe(true)
        await wrapper.findAll('button').find(button => button.text() === '分页符').trigger('click')
        expect(wrapper.emitted('update:modelValue')?.at(-1)[0]).toContain('<!-- pagebreak -->')
    })

    it('waits for an in-flight image insertion before entering source mode', async () => {
        let finishUpload
        API.common.upload.post.mockReturnValue(new Promise(resolve => { finishUpload = resolve }))
        const wrapper = createEditor()
        const file = new File(['image'], 'image.png', {type: 'image/png'})
        const request = wrapper.vm.uploadImage({target: {files: [file], value: 'image.png'}})

        const sourceButton = wrapper.findAll('button').find(button => button.text() === '源码')
        await wrapper.vm.$nextTick()
        expect(sourceButton.attributes('disabled')).toBeDefined()
        await wrapper.vm.uploadImage({target: {files: [file], value: 'image.png'}})
        expect(API.common.upload.post).toHaveBeenCalledTimes(1)
        wrapper.vm.toggleSource()
        expect(wrapper.vm.sourceMode).toBe(false)

        finishUpload({data: {src: 'https://example.com/pending.png'}})
        await request
        await wrapper.vm.$nextTick()
        expect(wrapper.vm.editor.getHTML()).toContain('pending.png')
        expect(sourceButton.attributes('disabled')).toBeUndefined()
    })

    it('unlocks source mode after an image upload fails', async () => {
        API.common.upload.post.mockRejectedValue(new Error('upload failed'))
        const wrapper = createEditor()
        const file = new File(['image'], 'image.png', {type: 'image/png'})
        await wrapper.vm.uploadImage({target: {files: [file], value: 'image.png'}})
        await wrapper.vm.$nextTick()

        expect(wrapper.find('[role="alert"]').text()).toBe('图片上传失败')
        expect(wrapper.findAll('button').find(button => button.text() === '源码').attributes('disabled')).toBeUndefined()
        wrapper.vm.toggleSource()
        expect(wrapper.vm.sourceMode).toBe(true)
    })

    it('reports an image that cannot be inserted after the editor becomes read-only', async () => {
        let finishUpload
        API.common.upload.post.mockReturnValue(new Promise(resolve => { finishUpload = resolve }))
        const wrapper = createEditor()
        const file = new File(['image'], 'image.png', {type: 'image/png'})
        const request = wrapper.vm.uploadImage({target: {files: [file], value: 'image.png'}})
        await wrapper.setProps({disabled: true})

        finishUpload({data: {src: 'https://example.com/pending.png'}})
        await request
        expect(wrapper.find('[role="alert"]').text()).toBe('图片上传失败')
        expect(wrapper.vm.editor.getHTML()).not.toContain('pending.png')
        expect(wrapper.vm.uploading).toBe(false)
    })

    it('discards an old upload when the parent replaces the edited record', async () => {
        let finishOldUpload
        let finishNewUpload
        API.common.upload.post
            .mockReturnValueOnce(new Promise(resolve => { finishOldUpload = resolve }))
            .mockReturnValueOnce(new Promise(resolve => { finishNewUpload = resolve }))
        const wrapper = createEditor({modelValue: '<p>记录 A</p>'})
        const file = new File(['image'], 'image.png', {type: 'image/png'})
        const oldRequest = wrapper.vm.uploadImage({target: {files: [file], value: 'image.png'}})

        await wrapper.setProps({modelValue: '<p>记录 B</p>'})
        expect(wrapper.vm.uploading).toBe(false)
        const newRequest = wrapper.vm.uploadImage({target: {files: [file], value: 'image.png'}})
        expect(API.common.upload.post).toHaveBeenCalledTimes(2)

        finishOldUpload({data: {src: 'https://example.com/old-record.png'}})
        await oldRequest
        expect(wrapper.vm.editor.getHTML()).toContain('记录 B')
        expect(wrapper.vm.editor.getHTML()).not.toContain('old-record.png')
        expect(wrapper.vm.uploading).toBe(true)
        expect(wrapper.find('[role="alert"]').exists()).toBe(false)

        finishNewUpload({data: {src: 'https://example.com/new-record.png'}})
        await newRequest
        expect(wrapper.vm.editor.getHTML()).toContain('new-record.png')
        expect(wrapper.vm.uploading).toBe(false)
    })

    it('ignores an upload that finishes after the editor is unmounted', async () => {
        let finishUpload
        API.common.upload.post.mockReturnValue(new Promise(resolve => { finishUpload = resolve }))
        const wrapper = createEditor({modelValue: '<p>即将卸载</p>'})
        const file = new File(['image'], 'image.png', {type: 'image/png'})
        const request = wrapper.vm.uploadImage({target: {files: [file], value: 'image.png'}})
        wrapper.unmount()

        finishUpload({data: {src: 'https://example.com/stale.png'}})
        await expect(request).resolves.toBeUndefined()
        expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    })
})
