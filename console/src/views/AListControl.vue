<script setup lang="ts">
import {  ref } from "vue"
import axios from "axios";
import {Base64} from 'js-base64'

const path = ref('')

const http = axios.create({
  baseURL: "/",
  timeout: 40 * 1000, // 30s
})

const doPostImportPath = () => {
  if (!path.value) {
    window.alert('请输入alist的浏览器路径，比如：https://domain.com/PKPK/LP-Raws');
    return;
  }
  // basic64 编码
  console.debug('original path value: ', path.value);
  const base64Path = Base64.encode(path.value);
  console.debug('basic64 path value: ', base64Path);
  let config = {
        headers: {'Content-Type': "application/json;charset=UTF-8"}
    };
  let data = {
      path: base64Path
  };

  // 设置jwt token的请求头
  const userStoreJson = window.localStorage.getItem('ikaros-store-user');
  if (!userStoreJson) {
    window.alert("操作取消，jwt令牌为空。")
    return
  }
  const userStore = JSON.parse(userStoreJson);
  if (!userStore || !userStore.jwtToken) {
    window.alert("操作取消，jwt令牌为空。")
    return
  }
  const jwtToken = userStore.jwtToken;
  http.defaults.headers.common['Authorization'] = 'Bearer ' + jwtToken;

  var submitBtn = document.getElementById('submitBtn')
  console.debug('submitBtn:', submitBtn)
  if (submitBtn) submitBtn.innerHTML = '处理中...'
  http.post('/apis/plugin.ikaros.run/v1alpha1/PluginAList/alist/import', data, config)
    .then((response) => {
      console.debug('response', response)
      if (response.status === 200) {
        window.alert('导入目录['+path.value+']成功!!')
      }
    })
    .catch(error => {
      console.error(error)
    })
    .finally(()=>{
      if (submitBtn) submitBtn.innerHTML = '提交导入'
    });

};

const onInputChange = (event:any) => {
  // console.debug('event', event)
  path.value = event.target.value
}

</script>

<template>
  <div class="ik-plugin-alist-container">
    <h3>AList 插件操作</h3>
    <hr />
    <input style="width: 800px;display: block;" placeholder="请输入需要导入AList浏览器路径，比如：https://domain.com/PKPK/LP-Raws , 递归处理，提交后还请耐心等待。" :value="path"  @input="onInputChange">
    <br />
    <button id="submitBtn" v-on:click="doPostImportPath">提交导入</button>
  </div> 
</template>

<style scoped>
.ik-plugin-alist-container {
  display: block;
  width: 100%;
  height: 100%;
  background-color: antiquewhite;
  border: 1px solid blue;
}
</style>
