<template>
  <v-flex v-if="hasChoices">
    <v-container>
      <v-layout row>
        <Choice
          v-for="choice of defaultChoices"
          :choice="choice"
          :key="choice.uid"
          @click="submit(choice)" />
        <v-flex v-for="choice of slotChoices" :key="choice.uid">
          <slot name="choice" :choice="choice" />
        </v-flex>
      </v-layout>
    </v-container>
  </v-flex>
</template>

<script lang="ts">
import Vue from 'vue'
import { PlayerChoice } from '../../core/breadboard.types'

/**
 * A container for all of the player choices
 */
export default Vue.extend({
  name: 'PlayerChoices',
  props: {
    player: {
      type: Object,
      required: true
    },
    choiceFilter: {
      type: Function
    }
  },
  data() {
    return {
      choicesAreEnabled: true
    }
  },
  updated() {
    this.choicesAreEnabled = true
    // console.log('player choices updated')
  },
  methods: {
    submit (choice: PlayerChoice) {
      const params = this.getCustomParams()
      window.Breadboard.sendChoice(choice.uid, params)
      this.choicesAreEnabled = false
    },
    getCustomParams () {
      const map: { [key: string]: any } = {}
      const customInputs = Array.from(document.querySelectorAll('.param'))
      for (const inp of customInputs) {
        const name = inp.getAttribute('name')
        if (name) {
          const type = inp.getAttribute('type')
          if (inp instanceof HTMLInputElement) {
            if (type === 'checkbox') {
              if (!map.hasOwnProperty(name)) {
                map[name] = []
              }
              if (inp.checked!) {
                map[name].push(inp.value)
              }
            } else {
              map[name] = inp.value
            }
          } else if (inp instanceof HTMLTextAreaElement) {
            map[name] = inp.value
          } else {
            console.log(`.param class isn't on an input field`, inp)
          }
        } else {
          console.log('skipped field without a name', inp)
        }
      }
      return map
    },
    /**
     * This filter allows to easily replace only certain choices and keep the other choices by default
     */
    useDefaultChoice (choice: { [key: string]: any }): boolean {
      if (!this.$slots.choice && !this.$scopedSlots.choice) {
        return true
      } else if (!this.choiceFilter) {
        return false
      } else {
        return !this.choiceFilter(choice)
      }
    }
  },
  computed: {
    defaultChoices (): any[] {
      if (this.player && this.player.choices) {
        return this.player.choices.filter((choice: any) => this.useDefaultChoice(choice))
      } else {
        return []
      }
    },
    slotChoices (): any[] {
      if (this.player && this.player.choices) {
        return this.player.choices.filter((choice: any) => !this.useDefaultChoice(choice))
      } else {
        return []
      }
    },
    hasChoices (): boolean {
      return !!this.defaultChoices.length || !!this.slotChoices.length
    }
  }
})
</script>
