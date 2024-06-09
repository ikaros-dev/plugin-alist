<script setup lang="ts">
import {  ref } from "vue"

const alistPath = ref("/")
</script>

<template>
  <div class="ik-plugin-alist-container">
    <h3>AList 插件操作</h3>
    <hr />
    <form name="form" method="post" action="#">
      <input name="path" placeholder="请输入需要导入的相对路径路径" required v-model="alistPath">
      <input type="submit" name="submit" value="提交导入">
      {{alistPath}}
    </form>
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
