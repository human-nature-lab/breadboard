<template>
  <component :is="container" :value="tutorialIndex">
    <v-stepper-header v-if="tutorial">
      <template v-for="(title, index) in tutorial.titles">
        <v-stepper-step
          :key="index + 'step'"
          :step="index + 1"
          :complete="tutorial.index >= index">
          {{title}}
        </v-stepper-step>
        <v-divider 
          v-if="index !== tutorial.maxSteps - 1"
          :key="index" />
      </template>
    </v-stepper-header>
    
    <slot />

    <div class="tutorial-foo" v-if="tutorial">
      <v-btn @click="prev" :disabled="!tutorial.index">
        Previous
      </v-btn>
      <v-btn @click="next">
        {{tutorial.index < tutorial.maxSteps - 1 ? 'Next' : 'Done'}}
      </v-btn>
    </div>
  </component>
</template>

<script lang="ts">
  import Vue from 'vue'
  import { TutorialState } from './tutorial.types'
  import { PlayerData } from '../../../core/breadboard.types'

  export default Vue.extend({
    name: 'Tutorial',
    props: {
      player: {
        type: Object as () => PlayerData | null
      },
      stateKey: {
        type: String,
        default: 'tutorial'
      }
    },
    computed: {
      tutorial (): TutorialState | null {
        return this.player ? this.player[this.stateKey] : null
      },
      container (): string {
        return this.tutorial && this.tutorial.useStepper ? 'v-stepper' : 'div'
      },
      tutorialIndex (): number {
        return this.tutorial ? this.tutorial.index : 0
      }
    },
    methods: {
      next () {
        window.Breadboard.send('tutorial-next')
      },
      prev () {
        window.Breadboard.send('tutorial-prev')
      }
    }
  })
</script>