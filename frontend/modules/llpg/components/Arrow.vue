<template>
  <g>
  <line 
    :x1="fromP.x"
    :y1="fromP.y"
    :x2="toP.x"
    :y2="toP.y"
    :stroke="color"
    :stroke-width="width"
    marker-end="url(#arrowhead)" />
    <image
      v-if="image"
      :href="image"
      :x="center.x + imageOffsetX"
      :y="center.y + imageOffsetY"
      width="75"
      height="75" />
  </g>
</template>

<script lang="ts">
  import Vue, { PropOptions } from 'vue'

  export type Point = { x: number, y: number }

  export default Vue.extend({
    name: 'Arrow',
    props: {
      from: Object as PropOptions<Point>,
      to: Object as PropOptions<Point>,
      color: {
        type: String,
        default: '#000'
      },
      width: {
        type: Number,
        default: 8
      },
      length: Number,
      image: String,
      imageOffsetX: {
        type: Number,
        default: 0
      },
      imageOffsetY: {
        type: Number,
        default: 0
      }
    },
    computed: {
      center (): Point {
        return {
          x: (this.to.x + this.from.x) / 2,
          y: (this.to.y + this.from.y) / 2
        }
      },
      mag (): number {
        const dx = this.to.x - this.from.x
        const dy = this.to.y - this.from.y
        return Math.sqrt(dx*dx + dy*dy)
      },
      dir (): Point {
        const dx = this.to.x - this.from.x
        const dy = this.to.y - this.from.y
        const mag = Math.sqrt(dx*dx + dy*dy)
        return {
          x: dx / mag,
          y: dy / mag
        }
      },
      fromP (): Point {
        if (this.length) {
          return {
            x: this.center.x - (this.dir.x * (this.mag * this.length) / 2),
            y: this.center.y - (this.dir.y * (this.mag * this.length) / 2)
          }
        } else {
          return this.from
        }
      },
      toP (): Point {
        if (this.length) {
          return {
            x: this.center.x + (this.dir.x * (this.mag * this.length) / 2),
            y: this.center.y + (this.dir.y * (this.mag * this.length) / 2)
          }
        } else {
          return this.to
        }
      }
    }
  })
</script>

<style lang="sass">
  
</style>