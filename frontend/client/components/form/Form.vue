<template>
  <v-stepper 
    v-if="form"
    :value="form.location.index">
    <v-stepper-header 
      :non-linear="form.nonLinear"
      v-if="form.useStepper">
      <v-stepper-step 
        v-for="(title, index) in form.titles"
        :key="title"
        :complete="form.location.index > index"
        :step="index">{{title}}</v-stepper-step>
    </v-stepper-header>
    <v-form v-model="valid" v-if="form">
      <v-container>
        <v-layout 
          v-for="(section, sectionIndex) in form.page.sections" 
          :key="sectionIndex"
          column
          class="pb-2">
          <v-flex v-for="(block, blockIndex) in section.blocks" :key="blockIndex">
            <component 
              :is="blockComponent(block.type)" 
              :block="block" 
              :state="form.results[form.location.index]" />
          </v-flex>
        </v-layout>
        <v-layout class="tutorial-foo">
          <v-btn @click="prev" :disabled="!form.location.index">
            <v-icon>mdi-chevron-left</v-icon> Previous
          </v-btn>
          <v-spacer />
          <v-btn @click="next" v-if="form.location.index < form.location.size - 1">
            Next <v-icon>mdi-chevron-right</v-icon>
          </v-btn>
          <v-btn @click="next" v-else>Done</v-btn>
        </v-layout>
      </v-container>
    </v-form>
  </v-stepper>

</template>

<script lang="ts">
  import Vue, { Component } from 'vue'
  import { PlayerWithForms, PlayerForm, BlockType } from './form.types'
  import ScaleQuestion from './blocks/ScaleQuestion.vue'
  import HtmlBlock from './blocks/HtmlBlock.vue'
  import MultipleChoiceQuestion from './blocks/MultipleChoiceQuestion.vue'

  const bb = window.Breadboard
  export default Vue.extend({
    name: 'Form',
    props: {
      player: {
        type: Object as () => PlayerWithForms,
        required: true
      },
      formsKey: {
        type: String,
        default: 'forms'
      },
      name: {
        type: String
      }
    },
    data () {
      return {
        valid: false
      }
    },
    methods: {
      next () {
        bb.send('f-' + this.name + '-n', [])
      },
      prev () {
        bb.send('f-' + this.name + '-p', [])
      },
      blockComponent (type: BlockType): Component {
        console.log('type', type)
        switch (type) {
          case BlockType.SCALE:
            return ScaleQuestion
          case BlockType.MULTIPLE_CHOICE:
            return MultipleChoiceQuestion
          default:
            return HtmlBlock
        }
      }
    },
    computed: {
      form (): PlayerForm | null {
        // @ts-ignore
        if (!this.player || !this.player[this.formsKey] || !this.player[this.formsKey][this.name]) {
          return null
        }
        //@ts-ignore
        return this.player[this.formsKey][this.name]
      }
    }
  })
</script>

<style lang="sass">
  
</style>