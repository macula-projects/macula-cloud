(window["webpackJsonp"]=window["webpackJsonp"]||[]).push([["chunk-2d21a08f"],{ba93:function(t,e,a){"use strict";a.r(e);var n=function(){var t=this,e=t._self._c;return e("a-card",{attrs:{bordered:!1}},[e("div",{staticClass:"table-page-search-wrapper"},[e("a-form",{attrs:{layout:"inline"}},[e("a-row",{attrs:{gutter:48}},[[e("a-col",{attrs:{md:8,sm:24}},[e("a-form-item",{attrs:{label:"组名称"}},[e("a-input",{attrs:{placeholder:"请输入组名称",allowClear:""},model:{value:t.queryParam.groupName,callback:function(e){t.$set(t.queryParam,"groupName",e)},expression:"queryParam.groupName"}})],1)],1)],e("a-col",{attrs:{md:t.advanced?24:8,sm:24}},[e("span",{staticClass:"table-page-search-submitButtons",style:t.advanced&&{float:"right",overflow:"hidden"}||{}},[e("a-button",{attrs:{type:"primary"},on:{click:function(e){return t.$refs.table.refresh(!0)}}},[t._v("查询")]),e("a-button",{staticStyle:{"margin-left":"8px"},on:{click:function(){return t.queryParam={}}}},[t._v("重置")])],1)])],2)],1)],1),e("div",{staticClass:"table-operator"},[t.$auth("group.add")?e("a-button",{attrs:{type:"primary",icon:"plus"},on:{click:function(e){return t.handleNew()}}},[t._v("新建")]):t._e()],1),e("s-table",{ref:"table",attrs:{size:"default",rowKey:"key",columns:t.columns,data:t.loadData,alert:t.options.alert,rowSelection:t.options.rowSelection,scroll:{x:1600}},scopedSlots:t._u([{key:"serial",fn:function(a,n,r){return e("span",{},[t._v(" "+t._s(r+1)+" ")])}},{key:"groupStatus",fn:function(a){return e("span",{},[t._v(" "+t._s(0===a?"停用":"启用")+" ")])}},{key:"initScene",fn:function(a){return e("span",{},[t._v(" "+t._s(0===a?"否":"是")+" ")])}},{key:"action",fn:function(a,n){return e("span",{},[[e("a",{on:{click:function(e){return t.handleEdit(n)}}},[t._v("编辑")]),e("a-divider",{attrs:{type:"vertical"}}),e("a",{on:{click:function(e){return t.handleEditStatus(n)}}},[t._v(t._s(1===n.groupStatus?"停用":"启用"))])]],2)}}])})],1)},r=[],o=(a("d3b7"),a("25f0"),a("27e3")),s=a("0fea"),i=a("2af9"),u=a("c1df"),c=a.n(u),d={name:"TableListWrapper",components:{AInput:o["a"],STable:i["j"]},data:function(){var t=this;return{advanced:!1,queryParam:{},columns:[{title:"#",scopedSlots:{customRender:"serial"}},{title:"名称",dataIndex:"groupName"},{title:"状态",dataIndex:"groupStatus",scopedSlots:{customRender:"groupStatus"}},{title:"路由策略",dataIndex:"routeKey",customRender:function(e){return t.routeKey[e]}},{title:"版本",dataIndex:"version"},{title:"分区",dataIndex:"groupPartition",needTotal:!0},{title:"ID生成模式",dataIndex:"idGeneratorModeName"},{title:"初始化场景",dataIndex:"initScene",scopedSlots:{customRender:"initScene"}},{title:"更新时间",dataIndex:"updateDt",sorter:!0,customRender:function(t){return c()(t).format("YYYY-MM-DD HH:mm:ss")}},{title:"描述",dataIndex:"description"},{title:"OnLine机器",dataIndex:"onlinePodList",customRender:function(t){return t.toString()}},{title:"操作",dataIndex:"action",width:"150px",fixed:"right",scopedSlots:{customRender:"action"}}],loadData:function(e){return Object(s["i"])(Object.assign(e,t.queryParam)).then((function(t){return t}))},selectedRowKeys:[],selectedRows:[],options:{alert:{show:!0,clear:function(){t.selectedRowKeys=[]}},rowSelection:{selectedRowKeys:this.selectedRowKeys,onChange:this.onSelectChange}},routeKey:{1:"一致性hash算法",2:"随机算法",3:"最近最久未使用算法"}}},created:function(){},methods:{handleNew:function(){this.$router.push("/basic-config")},handleEdit:function(t){this.$router.push({path:"/basic-config",query:{groupName:t.groupName}})},toggleAdvanced:function(){this.advanced=!this.advanced},handleEditStatus:function(t){var e=this,a=t.groupStatus,n=t.groupName,r=this.$notification;Object(s["F"])({groupName:n,groupStatus:1===a?0:1}).then((function(t){0===t.status?r["error"]({message:t.message}):(r["success"]({message:t.message}),e.$refs.table.refresh())}))}}},l=d,p=a("2877"),f=Object(p["a"])(l,n,r,!1,null,null,null);e["default"]=f.exports}}]);