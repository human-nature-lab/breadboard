<template>
  <div class="absolute w-full h-full top-0 left-0 z-40" v-if="value">
    <div class="notepad shadow shadow-lg absolute top-0 left-0 right-0 bottom-0 m-auto">
      <div class="bg-gray-800 w-full h-16 rounded-t text-right">
        <button class="float-right text-white p-5" @click="$emit('input', false)">X</button>
      </div>
      <slot name="header"></slot>
      <div class="line" v-for="i in skipLines" :key="`top${i}`" />
      <div class="line" v-for="(item, index) in items" :key="`item${index}`">
        <slot name="item" :item="item">
          {{item}}
        </slot>
      </div>
      <div class="line" v-for="i in bottomLines" :key="`end${i}`" />
    </div>
  </div>
</template>

<script lang="ts">
  import Vue from 'vue'

  export default Vue.extend({
    name: 'Notepad',
    props: {
      value: Boolean,
      items: Array,
      skipLines: {
        type: Number,
        default: 2
      },
      minLines: {
        type: Number,
        default: 20
      }
    },
    computed: {
      bottomLines (): number {
        const n = this.minLines - this.skipLines - this.items.length
        return n <= 0 ? 0 : n
      }
    }
  })
</script>

<style lang="sass">
  $lineSize: 30px
  $paperColor: #F1EDE9

  .notepad
    background-color: $paperColor
    width: 80%
    max-width: 600px
    height: 90%
    max-height: 700px
    .line
      height: $lineSize
      padding-left: 40px
      vertical-align: text-bottom
      border-bottom: 1px solid #94ACD4
</style>