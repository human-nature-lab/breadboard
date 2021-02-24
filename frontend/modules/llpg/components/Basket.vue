<template>
  <div class="relative basket" :style="style">
    <CanvasImage
      class="absolute top-0 left-0 z-0 h-full w-full"
      :src="images.basket.back" />
    <div class="absolute top-0 left-0 z-1 w-full h-full">
      <draggable
        :value="value"
        class="basket-items"
        group="basket"
        :sort="false"
        draggable=".basket-item"
        @input="onInput">
        <CanvasImage
          v-for="(item, i) in value"
          :key="item.id"
          class="basket-item"
          :style="{ marginTop: i % 2 ? '15%' : 0 }"
          :src="item.type === 'corn' ? images.corn : images.banana" />
      </draggable>
    </div>
    <CanvasImage
      class="absolute top-0 left-0 z-2 h-full w-full select-none pointer-events-none"
      :src="images.basket.front" />
  </div>  
</template>

<script lang="ts">
  import Vue, { PropOptions } from 'vue'
  import draggable from 'vuedraggable'
  import { images } from '../images'

  export default Vue.extend({
    name: 'Basket',
    components: { draggable },
    props: {
      value: Array as PropOptions<{ id: number, type: 'banana' | 'corn' }[]>,
      size: {
        type: Number,
        default: 1
      }
    },
    data () {
      return {
        images
      }
    },
    methods: {
      onInput (event: any) {
        this.$emit('input', event)
        this.$emit('change')
      }
    },
    computed: {
      style (): object {
        return { width: `${this.size * 993}px`, height: `${this.size * 387}px` }
      }
    }
  })
</script>

<style lang="sass" scoped>
  .basket-items
    display: flex
    width: 100%
    height: 100%
    padding-left: 30%
    padding-right: 30%
    flex-flow: row nowrap
    align-items: flex-start
    .basket-item
      display: flex
      width: 50%
      margin-left: -30%
</style>