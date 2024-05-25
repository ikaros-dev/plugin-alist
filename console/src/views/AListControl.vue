<script setup lang="ts">
</script>

<template>
  <div class="ik-plugin-alist-container">
    Hello Ikaros Plugin AList Home View.
    <hr />
    <form name="form" method="post" action="#">
      <input name="path" placeholder="请输入需要导入的基础路径" required>
      <input type="submit" name="submit" value="提交导入">
    </form>
  </div>
</template>

<style scoped>
.ik-plugin-alist-container {
  display: inline-block;
  width: 100%;
  height: 100%;
  background-color: antiquewhite;
  border: 1px solid blue;
}
</style>
