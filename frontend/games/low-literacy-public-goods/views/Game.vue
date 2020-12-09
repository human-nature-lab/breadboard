<template>
  <Fullscreen>
    <div class="relative w-full h-full">
      <div v-if="isLoading || player.step === 'Loading'" class="absolute text-center text-3xl w-64 h-64 top-0 left-0 bottom-0 right-0 m-auto">
        Please wait for the game to begin
      </div>
      <div v-else-if="player.step !== 'Complete'" class="relative w-full h-full">
        <Transform class="w-64 h-64 top-0" :transform="transforms.box" :visible="flags.showBox">
          <Box
            ref="box"
            :value="flags.doubleBox ? player.totalPoolDoubled : player.totalPool"
            :double="flags.doubleBox"
            :open="flags.boxOpen"
            :showValue="flags.showBoxValue" />
        </Transform>
        <Transform class="w-64 h-64 left-0 top-0" :transform="transforms.contributing" :visible="flags.showContributing">
          <Envelope
            v-model="decision.contributing"
            :closed="player.hasContributed" />
        </Transform>
        <Transform class="w-64 h-64" :transform="transforms.keeping">
          <Wallet
            v-model="decision.keeping"
            :earned="player.roundValue + decision.keeping" 
            :showMoney="player.step === 'Decision'"
            :closed="player.hasContributed" />
        </Transform>
        <transition name="fade" v-for="(loc, i) in partnerLocations" :key="loc.id">
          <Player
            v-if="flags.showGroup"
            ref="partners"
            class="absolute"
            :envelope="flags.isEnvelope"
            :showItem="flags.showPlayerItems"
            :itemInBox="partners[i].data.hasContributed"
            :value="player.groupPayout"
            :boxOffset="boxOffset(i + 1)"
            :boxLoc="transforms.box"
            :transform="loc" />
        </transition>
        <Player
          :hasItem="false"
          :itemInBox="player.hasContributed"
          :value="player.groupPayout"
          :showItem="false"
          :boxOffset="boxOffset(0)"
          :boxLoc="transforms.box"
          :transform="playerLoc"
          :locked="false" />
        <PlayerEnvelope
          :value="player.groupPayout"
          :boxOffset="boxOffset(0)"
          :boxLoc="transforms.box"
          group="player envelope"
          :transform="{ y: transforms.keeping.y, x: transforms.keeping.x + 7 }"
          :itemInBox="player.hasContributed"
          :visible="!['Decision', 'Results'].includes(player.step) && flags.showPlayerItems && showMyEnvelope"
          :envelope="flags.isEnvelope"
          />
        <Transform 
          class="w-64 h-64 top-0" 
          :transform="transforms.pending" 
          :visible="flags.showPending">
          <MoneyStack
            :locked="player.hasContributed"
            :xOffset="75"
            :yOffset="0"
            :rotate="90"
            :showValue="false"
            v-model="decision.pending">
            <Lock 
              @click="sendDecision"
              v-if="!decision.pending"
              type="checkbox"
              v-model="player.hasContributed"
              class="z-20 w-32 h-32 absolute bottom-0 left-0 right-0 top-0 m-auto"/>
          </MoneyStack>
        </Transform>
      </div>
      <div v-else class="absolute text-center text-3xl w-64 h-64 top-0 left-0 bottom-0 right-0 m-auto">
        The game has finished!
      </div>
    </div>
    <div v-if="showDialog" class="absolute top-0 left-0 w-screen h-screen bg-white z-20">
      <div class="absolute text-center text-3xl w-64 h-32 top-0 left-0 bottom-0 right-0 m-auto pt-12">
        Proxima Ronda
      </div>
    </div>
    <PortalTarget class="z-10" name="game" multiple />
  </Fullscreen>
</template>

<script lang="ts">
  import Vue, { PropOptions } from 'vue'
  import { PortalTarget } from 'portal-vue'
  import Fullscreen from '../components/Fullscreen.vue'
  import gsap from 'gsap'
  import { boxLayout } from '../boxLayout'
  import { cloneDeep } from 'lodash'
  import { Step, steps } from '../steps'
  import { delay } from '../../../core/delay'
  import { Node } from '../../../core/breadboard.types'
  import { Graph } from '../../../client/lib/graph'

  type Player = {
    step: Step
    roundValue: number
    keeping: number
    contributing: number
    allotted: number
  }

  export default Vue.extend({
    name: 'Game',
    components: { Fullscreen, PortalTarget },
    props: {
      player: Object,
      graph: Object as PropOptions<Graph>
    },
    data () {
      return {
        isLoading: true,
        showMyEnvelope: false,
        decision: {
          contributing: 0,
          keeping: 0,
          pending: 5
        },
        showDialog: false,
        flags: steps.Decision.flags,
        playerLoc: {
          x: 45,
          y: 80
        },
        transforms: cloneDeep(steps.Decision.transforms)
      }
    },
    watch: {
      player (player: any, oldPlayer: any) {
        if (this.isLoading) {
          this.initDecisionStep(player)
          this.isLoading = false
          return
        } else if (player.step !== oldPlayer.step) {
          this.transitionStep(oldPlayer.step, player.step)
        }
      }
    },
    methods: {
      initDecisionStep (player: Player) {
        console.log('init decision step', player.step)
        this.flags = cloneDeep(steps[player.step].flags)
        this.transforms = cloneDeep(steps[player.step].transforms)
        if (player.step === Step.Decision) {
          this.resetDecision(player)
        } else if (player.step === Step.Distributing) {
          this.showMyEnvelope = true
        }
      },
      resetDecision (player: Player) {
        const keeping = player.keeping || 0
        const contributing = player.contributing || 0
        const pending = player.allotted
        this.decision.keeping = keeping
        this.decision.contributing = contributing
        this.decision.pending = pending - (keeping + contributing)
      },
      sendDecision (data: { keeping: number, contributing: number }) {
        if (this.player.hasContributed) return 
        window.Breadboard.send('player-decision', {
          contributing: this.decision.contributing,
          keeping: this.decision.keeping
        })
      },
      async transitionStep (oldStep: Step, newStep: Step) {
        if (newStep === Step.PostDecision) {
          this.flags.showPending = false
          this.showMyEnvelope = true
          this.transforms = cloneDeep(steps[newStep].transforms)
          await delay(1500)
          this.decision.keeping = 0
          this.decision.contributing = 0
        } else if (newStep === Step.Decision) {
          this.showDialog = true
          setTimeout(() => {
            this.showDialog = false
          }, 3000)
          await delay(1500)
          return this.initDecisionStep(this.player)
        } else if (newStep === Step.Distributed) {
          this.flags.doubleBox = true
          await delay(2500)
          this.flags = cloneDeep(steps[this.player.step].flags)
          this.showMyEnvelope = true
          await delay(3500)
          this.flags.showBoxValue = false
          setTimeout(() => {
            this.showMyEnvelope = false
          }, 2500)
        }
        this.flags = cloneDeep(steps[this.player.step].flags)
        this.transforms = cloneDeep(steps[newStep].transforms)
      },
      boxOffset (index: number) {
        return boxLayout(index)
      }
    },
    computed: {
      itemsInBox (): number {
        return this.graph.nodes.filter((n: Node<{ hasContributed: boolean }>) => n.data.hasContributed).length
      },
      partners (): any[] {
        return this.graph.nodes.filter((n: any) => n.id !== this.player.id)
      },
      partnerLocations (): { x: number, y: number }[] {
        const l = this.partners.length
        const dA = l ? 2 * Math.PI / (l + 1) : 0
        let startAngle = Math.PI / 2 + dA
        return this.partners.map((n: any, i: number) => {
          const angle = startAngle + dA * i
          const x = 40 * Math.cos(angle) + 45
          const y = 40 * Math.sin(angle) + 50
          return { x, y, i, id: n.id }
        })
      },
      playerLocations (): { x: number, y: number }[] {
        return [this.playerLoc].concat(this.partnerLocations)
      }
    }
  })
</script>

<style lang="sass">
  html, body 
    width: 100% 
    height: 100%
    margin: 0
    overflow: hidden
</style>