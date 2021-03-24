<template>
  <div class="relative w-full h-full" ref="parent">
    <!-- <canvas ref="canvas" class="absolute w-full h-full z-10 select-none pointer-events-none" /> -->
    <svg class="absolute w-full h-full top-0 left-0 z-10 select-none pointer-events-none" xmlns="http://www.w3.org/2000/svg" :viewBox="`0 0 ${width} ${height}`">
      <defs>
        <marker id="arrowhead" markerWidth="8" markerHeight="5" refX="6" refY="2.5" orient="auto">
          <polygon points="0 0, 8 2.5, 0 5" />
        </marker>
      </defs>
      <g v-if="step === 'one'">
        <Arrow 
          :from="refToPoint('basketOne')" 
          :to="refToPoint('basketTwo')"
          :length=".4" />

      </g>
      <g v-else-if="step === 'two'">
        <Arrow 
          :from="refToPoint('basketTwo')" 
          :to="refToPoint('basketThree')"
          :length=".2" />
      </g>
      <g v-else>
        <Arrow 
          :from="refToPoint('basketThree')" 
          :to="refToPoint('basketOne')"
          :length=".4" />
      </g>
    </svg>
    <Transform :transform="transforms.basketTwo">
      <Basket
        ref="basketTwo"
        v-model="basketTwo"
        @change="onChange"
        :size="basketSize" />
    </Transform>
    <Transform :transform="transforms.basketThree">
      <Basket
        ref="basketThree"
        v-model="basketThree"
        @change="onChange"
        :size="basketSize" />
    </Transform>
    <Transform :transform="transforms.basketOne">
      <Basket
        ref="basketOne"
        v-model="basketOne"
        @change="onChange"
        :size="basketSize" />
    </Transform>
  </div>
</template>

<script lang="ts">
  import { rangeArr } from 'goodish'
  import Vue from 'vue'
  import { drawLineWithArrows, elementCenterPoint } from '../canvas'
  import { Point } from '../components/Arrow.vue'
  import { images } from '../images'

  const count = 4

  export default Vue.extend({
    name: 'Practice',
    data () {
      return {
        images,
        isMounted: false,
        step: 'one',
        basketSize: .4,
        basketOne: rangeArr(0, count).map(i => ({ id: i, type: 'banana' })),
        basketTwo: [],
        basketThree: [],
        transforms: {
          basketOne: { x: 50, y: 80 },
          basketTwo: { x: 25, y: 25 },
          basketThree: { x: 75, y: 25}
        },
        width: window.innerWidth,
        height: window.innerHeight
      }
    },
    mounted () {
      // @ts-ignore
      // this.ctx = this.$refs.canvas.getContext('2d')
      window.addEventListener('resize', this.onResize)
      setTimeout(this.onResize, 1000)
      this.isMounted = true
      // this.$nextTick(this.onResize)
    },
    beforeDestroy () {
      window.removeEventListener('resize', this.onResize)
    },
    methods: {
      onResize () {
        // @ts-ignore
        this.width = this.$refs.parent.clientWidth
        // @ts-ignore
        this.height = this.$refs.parent.clientHeight
        // this.drawArrows()
      },
      onChange () {
        // TODO: Check if the current step is complete and move onto the next step
        if (this.step === 'one' && this.countType(this.basketTwo, 'banana') === count) {
          this.step = 'two'
        } else if (this.step === 'two' && this.countType(this.basketThree, 'banana') === count) {
          this.step = 'three'
        } else if (this.step === 'three' && this.countType(this.basketOne, 'banana') === count) {
          window.Breadboard.send('complete')
        }
      },
      refToPoint (ref: string): Point {
        if (!this.isMounted) return { x: 0, y: 0 }
        const r = this.$refs[ref]
        if (r instanceof Vue) {
          // @ts-ignore
          return elementCenterPoint(r.$el)
        } else if (r instanceof Element) {
          return elementCenterPoint(r as HTMLElement)
        }
        return { x: 0, y: 0 }
      },
      countType (arr: { type: string }[], type: string): number {
        return arr.reduce((sum, val) => val.type === type ? sum + 1 : sum, 0)
      },
      drawArrows () {
        // @ts-ignore
        const ctx: CanvasRenderingContext2D = this.ctx
        // @ts-ignore
        const p: HtmlElement = this.$refs.parent
        ctx.canvas.width = p.clientWidth
        ctx.canvas.height = p.clientHeight
        ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height)
        ctx.strokeStyle = 'black'
        ctx.lineWidth = 2
        if (this.step === 'sort') {
          // @ts-ignore
          drawLineWithArrows(ctx, elementCenterPoint(this.$refs.basketOne.$el), elementCenterPoint(this.$refs.basketTwo.$el))
          // @ts-ignore
          drawLineWithArrows(ctx, elementCenterPoint(this.$refs.basketOne.$el), elementCenterPoint(this.$refs.basketThree.$el))
        }
      }
    }
  })
</script>

<style lang="sass">
  
</style>