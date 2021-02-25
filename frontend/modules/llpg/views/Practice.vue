<template>
  <div class="relative w-full h-full" ref="parent">
    <!-- <canvas ref="canvas" class="absolute w-full h-full z-10 select-none pointer-events-none" /> -->
    <svg class="absolute w-full h-full top-0 left-0 z-10 select-none pointer-events-none" xmlns="http://www.w3.org/2000/svg" :viewBox="`0 0 ${width} ${height}`">
      <defs>
        <marker id="arrowhead" markerWidth="8" markerHeight="5" refX="6" refY="2.5" orient="auto">
          <polygon points="0 0, 8 2.5, 0 5" />
        </marker>
      </defs>
      <g v-if="step === 'sort'">
        <Arrow 
          :from="refToPoint('basketOne')" 
          :to="refToPoint('basketTwo')"
          :image="images.banana"
          :imageOffsetX="-100"
          :length=".4" />
        <Arrow 
          :from="refToPoint('basketOne')" 
          :to="refToPoint('basketThree')"
          :image="images.corn"
          :imageOffsetX="50"
          :length=".4" />
      </g>
      <g v-else-if="step === 'return corn'">
        <Arrow 
          :from="refToPoint('basketThree')" 
          :to="refToPoint('basketOne')"
          :image="images.corn"
          :imageOffsetX="50"
          :length=".4" />
      </g>
      <g v-else>
        <Arrow 
          :from="refToPoint('basketTwo')" 
          :to="refToPoint('basketThree')"
          :image="images.banana"
          :imageOffsetY="-100"
          :imageOffsetX="-50"
          :length=".2" />
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

  const count = 3

  export default Vue.extend({
    name: 'Practice',
    data () {
      return {
        images,
        isMounted: false,
        step: 'sort',
        basketSize: .4,
        basketOne: rangeArr(0, count * 2).map(i => ({ id: i, type: i < 3 ? 'banana' : 'corn' })),
        basketTwo: [],
        basketThree: [],
        transforms: {
          basketOne: { x: 25, y: 70 },
          basketTwo: { x: 0, y: 10 },
          basketThree: { x: 50, y: 10}
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
        if (this.step === 'sort' && this.countType(this.basketThree, 'corn') === 3 && this.countType(this.basketTwo, 'banana') === 3) {
          this.step = 'return corn'
        } else if (this.step === 'return corn' && this.countType(this.basketOne, 'corn') === 3 && this.basketOne.length === 3) {
          this.step = 'move bananas'
        } else if (this.step === 'move bananas' && this.countType(this.basketThree, 'banana') === 3 && this.basketThree.length === 3) {
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