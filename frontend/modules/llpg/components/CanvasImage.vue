<template>
  <canvas ref="canvas" />
</template>

<script lang="ts">
  import Vue, { PropOptions } from 'vue'
  import { ImageLoader } from '../ImageLoader'
  import { images } from '../images'

  const loader = new ImageLoader([
    ...Object.values(images.envelope),
    ...Object.values(images.box),
    ...Object.values(images.currency),
    ...Object.values(images.lock),
    ...Object.values(images.basket),
    images.person,
    images.wallet,
    images.banana,
    images.corn
  ])

  export default Vue.extend({
    name: 'CanvasImage',
    props: {
      src: {
        type: String,
        required: true
      },
      sources: {
        type: Array,
        default: () => []
      } as PropOptions<string[]>
    },
    data () {
      return {
        loaded: false,
        image: null as HTMLImageElement | null
      }
    },
    async mounted () {
      await this.updateImage()
    },
    watch: {
      src (newSrc: string, oldSrc: string) {
        if (newSrc !== oldSrc) {
          this.updateImage()
        }
      },
      sources (newSources: string[]) {
        for (const src of newSources) {
          loader.load(src)
        }
      }
    },
    methods: {
      async updateImage ()  {
        try {
          this.loaded = false
          this.image = await loader.load(this.src)
          this.drawImage()
          this.loaded = true
        } catch (err) {
          this.drawError(err)
        }
      },
      drawImage () {
        if (this.$refs.canvas && this.image) {
          const canv = this.$refs.canvas as HTMLCanvasElement
          const ctx = canv!.getContext('2d')
          canv!.width = this.image!.width
          canv!.height = this.image!.height
          ctx!.clearRect(0, 0, canv.width, canv.height)
          ctx!.drawImage(this.image!, 0, 0)
        }
      },
      drawError (err: Error) {
        console.error(err)
        if (this.$refs.canvas) {
          const canv = this.$refs.canvas as HTMLCanvasElement
          const ctx = canv!.getContext('2d')
          ctx!.textAlign = 'center'
          ctx!.textBaseline = 'middle'
          ctx!.fillText('error', canv.width / 2, canv.height / 2)
        }
      }
    }
  })
</script>

<style lang="sass">
  
</style>