<!--
  - Copyright (c) 2023 Macula
  -   macula.dev, China
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -    http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->

<template>
  <div class="sceditor" :class="{ 'is-disabled': disabled }">
    <div class="sceditor-toolbar" role="toolbar" aria-label="富文本编辑器工具栏">
      <button type="button" :disabled="visualDisabled" @click="run('undo')">撤销</button>
      <button type="button" :disabled="visualDisabled" @click="run('redo')">重做</button>
      <button type="button" :disabled="visualDisabled" @click="run('toggleBold')">粗体</button>
      <button type="button" :disabled="visualDisabled" @click="run('toggleItalic')">斜体</button>
      <button type="button" :disabled="visualDisabled" @click="run('toggleUnderline')">下划线</button>
      <button type="button" :disabled="visualDisabled" @click="run('toggleStrike')">删除线</button>
      <select aria-label="段落格式" :disabled="visualDisabled" @change="setBlock($event.target.value)">
        <option value="paragraph">正文</option>
        <option value="1">标题 1</option>
        <option value="2">标题 2</option>
        <option value="3">标题 3</option>
      </select>
      <select aria-label="字体" :disabled="visualDisabled" @change="run('setFontFamily', $event.target.value)">
        <option value="">字体</option>
        <option value="Arial">Arial</option>
        <option value="Microsoft YaHei">微软雅黑</option>
        <option value="SimSun">宋体</option>
      </select>
      <select aria-label="字号" :disabled="visualDisabled" @change="run('setFontSize', $event.target.value)">
        <option value="">字号</option>
        <option v-for="size in [12, 14, 16, 18, 22, 24, 36, 72]" :key="size" :value="`${size}px`">{{ size }}</option>
      </select>
      <label class="sceditor-color">文字色 <input type="color" aria-label="文字颜色" :disabled="visualDisabled" @input="run('setColor', $event.target.value)" /></label>
      <label class="sceditor-color">背景色 <input type="color" aria-label="背景颜色" :disabled="visualDisabled" @input="run('setBackgroundColor', $event.target.value)" /></label>
      <button type="button" :disabled="visualDisabled" @click="run('setTextAlign', 'left')">左对齐</button>
      <button type="button" :disabled="visualDisabled" @click="run('setTextAlign', 'center')">居中</button>
      <button type="button" :disabled="visualDisabled" @click="run('setTextAlign', 'right')">右对齐</button>
      <button type="button" :disabled="visualDisabled" @click="run('setTextAlign', 'justify')">两端对齐</button>
      <button type="button" :disabled="visualDisabled" @click="run('toggleBulletList')">无序列表</button>
      <button type="button" :disabled="visualDisabled" @click="run('toggleOrderedList')">有序列表</button>
      <button type="button" :disabled="visualDisabled" @click="setLink">链接</button>
      <button type="button" :disabled="visualDisabled || uploading" @click="$refs.imageInput.click()">图片</button>
      <input ref="imageInput" class="sceditor-file" type="file" accept="image/*" :disabled="visualDisabled || uploading" @change="uploadImage" />
      <button type="button" :disabled="visualDisabled" @click="insertMedia">媒体</button>
      <button type="button" :disabled="visualDisabled" @click="insertTable">表格</button>
      <select v-if="templates.length" aria-label="插入模板" :disabled="visualDisabled" @change="insertTemplate">
        <option value="">插入模板</option>
        <option v-for="(item, index) in templates" :key="index" :value="index">{{ item.title }}</option>
      </select>
      <button type="button" :disabled="visualDisabled" @click="run('insertContent', { type: 'pageBreak' })">分页符</button>
      <button type="button" :disabled="disabled || uploading" @click="toggleSource">{{ sourceMode ? '完成源码' : '源码' }}</button>
      <button type="button" @click="previewOpen = true">预览</button>
    </div>
    <p v-if="uploadError" class="sceditor-error" role="alert">{{ uploadError }}</p>
    <textarea v-if="sourceMode" v-model="sourceValue" class="sceditor-source" aria-label="HTML 源码" :disabled="disabled" :style="{ minHeight: `${height}px` }" @input="updateSource($event.target.value)" />
    <editor-content v-show="!sourceMode" :editor="editor" class="sceditor-content" :style="{ minHeight: `${height}px` }" />
    <div v-if="previewOpen" class="sceditor-preview">
      <button type="button" @click="previewOpen = false">关闭预览</button>
      <iframe title="富文本预览" sandbox="" :srcdoc="previewHtml" />
    </div>
  </div>
</template>

<script>
import API from '@/api'
import {Node} from '@tiptap/core'
import {Editor, EditorContent} from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Image from '@tiptap/extension-image'
import {TableKit} from '@tiptap/extension-table'
import {TextStyleKit} from '@tiptap/extension-text-style'
import TextAlign from '@tiptap/extension-text-align'
import Placeholder from '@tiptap/extension-placeholder'

const PAGE_BREAK_MARKER = '<!-- pagebreak -->'
// ProseMirror drops HTML comments; use a visible node internally but keep the stored TinyMCE marker.
const PAGE_BREAK_HTML = '<div data-pagebreak="true" style="page-break-after: always;"></div>'

function toEditorHtml(value) {
  return (value || '').replace(/<!--\s*pagebreak\s*-->/gi, PAGE_BREAK_HTML)
}

function toStoredHtml(value) {
  return value.replaceAll(PAGE_BREAK_HTML, PAGE_BREAK_MARKER)
}

function safeMediaUrl(value) {
  try {
    const url = new URL(value, window.location.href)
    return ['http:', 'https:'].includes(url.protocol) ? url.href : null
  } catch {
    return null
  }
}

const Media = Node.create({
  name: 'media',
  group: 'block',
  atom: true,
  addAttributes() {
    return {src: {default: null}, kind: {default: 'iframe'}}
  },
  parseHTML() {
    return [
      {tag: 'iframe[src]', getAttrs: element => safeMediaUrl(element.getAttribute('src')) ? {src: element.getAttribute('src'), kind: 'iframe'} : false},
      {tag: 'video[src]', getAttrs: element => safeMediaUrl(element.getAttribute('src')) ? {src: element.getAttribute('src'), kind: 'video'} : false}
    ]
  },
  renderHTML({node}) {
    const src = safeMediaUrl(node.attrs.src)
    if (node.attrs.kind === 'video') return ['video', {src, controls: 'controls'}]
    return ['iframe', {src, allowfullscreen: 'true', title: '嵌入媒体'}]
  }
})

const PageBreak = Node.create({
  name: 'pageBreak',
  group: 'block',
  atom: true,
  parseHTML() {
    return [{tag: 'div[data-pagebreak]'}, {tag: 'div.mce-pagebreak'}]
  },
  renderHTML() {
    return ['div', {'data-pagebreak': 'true', style: 'page-break-after: always'}]
  }
})

export default {
  name: 'ScEditor',
  components: {EditorContent},
  props: {
    modelValue: {type: String, default: ''},
    placeholder: {type: String, default: ''},
    height: {type: Number, default: 300},
    disabled: {type: Boolean, default: false},
    templates: {type: Array, default: () => []},
    // Accepted for old callers, but TinyMCE-specific values are not portable.
    plugins: {type: [String, Array], default: ''},
    toolbar: {type: [String, Array], default: ''},
    options: {type: Object, default: () => ({})}
  },
  data() {
    return {editor: null, sourceMode: false, sourceValue: '', previewOpen: false, uploadError: '', uploading: false, uploadGeneration: 0}
  },
  computed: {
    visualDisabled() {
      return this.disabled || this.sourceMode
    },
    previewHtml() {
      return this.sourceMode ? toEditorHtml(this.sourceValue) : this.editor?.getHTML() || ''
    }
  },
  watch: {
    modelValue(value) {
      if (this.editor && toStoredHtml(this.editor.getHTML()) !== value) {
        // A parent may reuse this component for another record while an upload is pending.
        this.uploadGeneration += 1
        this.uploading = false
        this.uploadError = ''
        this.editor.commands.setContent(toEditorHtml(value), {emitUpdate: false})
        if (this.sourceMode) this.sourceValue = value || ''
      }
    },
    disabled(value) {
      this.editor?.setEditable(!value && !this.sourceMode)
    },
    placeholder(value) {
      const extension = this.editor?.extensionManager.extensions.find(item => item.name === 'placeholder')
      if (extension) {
        extension.options.placeholder = value
        this.editor.view.dispatch(this.editor.state.tr)
      }
    }
  },
  mounted() {
    this.editor = new Editor({
      content: toEditorHtml(this.modelValue),
      editable: !this.disabled,
      extensions: [
        StarterKit.configure({link: {openOnClick: false}}),
        Image,
        TableKit,
        TextStyleKit,
        TextAlign.configure({types: ['heading', 'paragraph']}),
        Placeholder.configure({placeholder: this.placeholder}),
        Media,
        PageBreak
      ],
      onUpdate: ({editor}) => this.$emit('update:modelValue', toStoredHtml(editor.getHTML())),
      editorProps: {
        handleClick: (_view, _pos, event) => {
          this.$emit('onClick', event, this.editor)
          return false
        }
      }
    })
  },
  beforeUnmount() {
    this.uploadGeneration += 1
    const editor = this.editor
    this.editor = null
    editor?.destroy()
  },
  methods: {
    run(command, ...args) {
      if (!this.editor || this.visualDisabled) return
      this.editor.chain().focus()[command](...args).run()
    },
    setBlock(value) {
      if (value === 'paragraph') this.run('setParagraph')
      else this.run('toggleHeading', {level: Number(value)})
    },
    setLink() {
      if (this.visualDisabled || !this.editor) return
      const url = window.prompt('请输入链接地址', this.editor.getAttributes('link').href || 'https://')
      if (url === null) return
      if (!url.trim()) this.run('unsetLink')
      else if (safeMediaUrl(url)) this.run('setLink', {href: url})
    },
    async uploadImage(event) {
      const file = event.target.files?.[0]
      event.target.value = ''
      if (!file || this.visualDisabled || this.uploading || !this.editor) return
      this.uploadError = ''
      this.uploading = true
      const generation = this.uploadGeneration
      try {
        const data = new FormData()
        data.append('file', file, file.name)
        const result = await API.common.upload.post(data)
        if (generation !== this.uploadGeneration) return
        const src = result?.data?.src
        if (!src || !safeMediaUrl(src)) throw new Error('Invalid uploaded image URL')
        if (this.visualDisabled) throw new Error('Editor became read-only before image insertion')
        this.run('setImage', {src})
      } catch {
        if (generation === this.uploadGeneration) this.uploadError = '图片上传失败'
      } finally {
        if (generation === this.uploadGeneration) this.uploading = false
      }
    },
    insertMedia() {
      if (this.visualDisabled || !this.editor) return
      const url = window.prompt('请输入媒体地址')
      if (!url || !safeMediaUrl(url)) return
      this.run('insertContent', {type: 'media', attrs: {src: url, kind: /\.(mp4|webm|ogg)(\?|$)/i.test(url) ? 'video' : 'iframe'}})
    },
    insertTable() {
      this.run('insertTable', {rows: 3, cols: 3, withHeaderRow: true})
    },
    insertTemplate(event) {
      const index = Number(event.target.value)
      event.target.value = ''
      if (this.visualDisabled || !Number.isInteger(index)) return
      const content = this.templates[index]?.content
      if (typeof content === 'string') this.run('insertContent', content)
    },
    updateSource(value) {
      if (!this.editor || this.disabled) return
      this.sourceValue = value
      // Keep the form model current even if the user submits without leaving source mode.
      this.editor.commands.setContent(toEditorHtml(value))
    },
    toggleSource() {
      if (this.disabled || this.uploading || !this.editor) return
      if (!this.sourceMode) this.sourceValue = toStoredHtml(this.editor.getHTML())
      this.sourceMode = !this.sourceMode
      this.editor.setEditable(!this.visualDisabled)
    }
  }
}
</script>

<style scoped>
.sceditor { border: 1px solid #dcdfe6; border-radius: 4px; background: #fff; }
.sceditor-toolbar { display: flex; flex-wrap: wrap; gap: 4px; padding: 6px; border-bottom: 1px solid #dcdfe6; }
.sceditor-toolbar button, .sceditor-toolbar select { min-height: 28px; padding: 2px 6px; border: 1px solid #dcdfe6; border-radius: 3px; background: #fff; cursor: pointer; }
.sceditor-toolbar button:disabled, .sceditor-toolbar select:disabled { cursor: not-allowed; opacity: .5; }
.sceditor-color { display: inline-flex; align-items: center; gap: 4px; font-size: 12px; }
.sceditor-color input { width: 26px; height: 28px; padding: 0; border: 0; }
.sceditor-file { display: none; }
.sceditor-content { padding: 12px; }
.sceditor-content :deep(.ProseMirror) { min-height: inherit; outline: 0; line-height: 1.5; }
.sceditor-content :deep(.ProseMirror p.is-editor-empty:first-child::before) { content: attr(data-placeholder); color: #aaa; float: left; height: 0; pointer-events: none; }
.sceditor-content :deep(table) { border-collapse: collapse; }
.sceditor-content :deep(td), .sceditor-content :deep(th) { border: 1px solid #bbb; padding: 4px 8px; }
.sceditor-content :deep(img), .sceditor-content :deep(video), .sceditor-content :deep(iframe) { max-width: 100%; }
.sceditor-content :deep([data-pagebreak]) { border-top: 1px dashed #aaa; margin: 16px 0; }
.sceditor-source { display: block; width: 100%; padding: 12px; border: 0; box-sizing: border-box; font-family: monospace; }
.sceditor-preview { position: fixed; inset: 10%; z-index: 3000; display: flex; flex-direction: column; padding: 12px; background: #fff; border: 1px solid #bbb; box-shadow: 0 8px 32px #0003; }
.sceditor-preview button { align-self: flex-end; }
.sceditor-preview iframe { flex: 1; width: 100%; border: 0; }
.sceditor-error { color: #f56c6c; margin: 4px 12px; }
.is-disabled .sceditor-content { background: #f5f7fa; }
</style>
