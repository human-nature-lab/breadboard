<template>
  <div
    class="relative w-full h-full"
    @dragover="onDragOver"
    @dragleave="onDragLeave"
    @drop="onDrop">
    <div class="absolute w-full h-full z-10" v-show="visible">
      <div
      class="absolute drag-item"
      :style="{ transform: `translate(${xOffset * i}px, ${yOffset * i}px)` }"
      v-for="i in value"
      :key="i"
      :draggable="!locked"
      @dragstart="onDragStart"
      @dragend="onDragEnd">
      <slot name="item" />
    </div>
    </div>
    <slot />
  </div>
</template>

<script lang="ts">
  import Vue from 'vue'

  export default Vue.extend({
    name: 'CurrencyDropZone',
    props: {
      value: Number,
      dragKey: String,
      visible: {
        type: Boolean,
        default: true
      },
      xOffset: {
        type: Number,
        default: 5,
      },
      yOffset: {
        type: Number,
        default: 5
      },
      locked: {
        type: Boolean,
        default: false
      }
    },
    data () {
      return {
        willAccept: false
      }
    },
    methods: {
      onDragStart (ev: DragEventInit) {
        console.log('ondragstart', ev)
        const data = {
          dragKey: this.dragKey,
          value: 1
        }
        ev.dataTransfer!.effectAllowed = 'move'
        ev.dataTransfer!.setData('currency', JSON.stringify(data))
        // this.$emit('input', this.value - data.value)
      },
      onDragOver (ev: DragEvent) {
        console.log('dragover', ev)
        // Call ev.preventDefault() if this item can be dropped here
        if (ev.dataTransfer && ev.dataTransfer.types.includes('currency')) {
          ev.preventDefault()
        }
      },
      onDragEnter (ev: DragEvent) {
        console.log('dragenter', ev)
      },
      onDragEnd (ev: DragEvent) {
        console.log('dragend', ev, ev.dataTransfer!.dropEffect, ev.dataTransfer!.effectAllowed, ev.dataTransfer!.types.includes('currency'))
        if (ev.dataTransfer && ev.dataTransfer.dropEffect !== 'none') {
          console.log(JSON.stringify(ev.dataTransfer))
          this.$emit('input', this.value - 1)
        }
      },
      onDragLeave (ev: DragEvent) {
        console.log('dragleave', ev)
      },
      onDrop (ev: DragEvent) {
        console.log('onDrop', ev, ev.dataTransfer!.dropEffect, ev.dataTransfer!.effectAllowed)
        if (ev.dataTransfer) {
          try {
            const data = JSON.parse(ev.dataTransfer.getData('currency'))
            if (data.value) {
              this.$emit('input', this.value + data.value)
            }
          } catch (err) {
            console.error(err)
          }
        }
      }
    }
  })
</script>

<style lang="sass">
  
</style>