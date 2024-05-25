import { definePlugin } from "@runikaros/shared"
import AListControl from '@/views/AListControl.vue';
import { Files as FilesIcon } from '@element-plus/icons-vue';
import {  markRaw } from "vue"

export default definePlugin({
    name: 'PluginAList',
    components: {},
    routes: [
      {
        parentName: "Root",
        route: {
          path: '/PluginAListControl',
          component: AListControl,
          name: "AListControl",
          meta: {
            title: 'AList',
            menu: {
              name: 'AList',
              group: 'tool',
              icon: markRaw(FilesIcon),
							priority: 2,
							mobile: true,
            }
          }
        }
      }
    ],
})