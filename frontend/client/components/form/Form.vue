<template>
  <v-stepper 
    v-if="form"
    :value="form.location.index">
    <v-stepper-header 
      :non-linear="form.nonLinear"
      v-if="form.showStepper && form.pages.length > 1">
      <v-stepper-step 
        v-for="(page, index) in form.pages"
        :key="index"
        :complete="form.location.index > index"
        :step="index">{{page.title}}</v-stepper-step>
    </v-stepper-header>
    <v-form v-model="valid" v-if="form">
      <v-container>
        <v-layout 
          v-for="(section, sectionIndex) in form.page.sections" 
          :key="sectionIndex"
          column
          class="pb-2">
          <v-flex v-for="(block, blockIndex) in section.blocks" :key="blockIndex" class="my-2">
            <component 
              :is="blockComponent(block.type)" 
              :block="block"
              :disabled="isBusy"
              @update="updateResult(block, $event)" />
          </v-flex>
        </v-layout>
        <slot />
        <v-divider />
        <v-layout class="tutorial-foo mt-2">
          <v-btn v-if="form.pages.length > 1" @click="prev" :disabled="!form.location.index || isBusy" :loading="isBusy && dir === 'prev'">
            <v-icon>mdi-chevron-left</v-icon> Previous
          </v-btn>
          <v-spacer />
          <v-btn @click="next" :disabled="!valid || isBusy" :loading="isBusy && dir === 'next'">
            <span v-if="form.location.index < form.location.size - 1">
              Next <v-icon>mdi-chevron-right</v-icon>
            </span>
            <span v-else>
              Done
            </span>
          </v-btn>
        </v-layout>
      </v-container>
    </v-form>
  </v-stepper>

</template>

<script lang="ts">
  import Vue, { Component, PropOptions } from 'vue'
  import { PlayerWithForms, PlayerForm, BlockType, QuestionResult, Prim, BaseBlock, FormError } from './form.types'
  import ScaleQuestion from './blocks/ScaleQuestion.vue'
  import HtmlBlock from './blocks/HtmlBlock.vue'
  import ChoiceQuestion from './blocks/ChoiceQuestion.vue'
  import TextQuestion from './blocks/TextQuestion.vue'

  type PlayerForms = { [key: string]: PlayerForm }
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
      name: <PropOptions<string | string[]>>{
        type: [String, Array]
      }
    },
    data () {
      return {
        isBusy: false,                    // set to true when form is transferring data
        valid: false,                     // indicates state of frontend validators
        dir: null as string | null,       // keeps track of which button was pressed
        error: null as FormError | null,  // Applied if error in form navigation
        results: {} as { [key: string]: QuestionResult<Prim | Prim[]> },
        prevName: null as string | null
      }
    },
    created () {
      window.Breadboard.on(`f-${this.name}-e`, (err: FormError) => {
        console.error(err)
        this.isBusy = false
        this.error = err
      })
      this.$watch(`player.${this.formsKey}`, (newForms: PlayerForms, oldForms: PlayerForms) => {
        if (this.formName) {
          if (!oldForms) {
            console.log('no old forms')
            return this.makeResults()
          }
          const newForm = newForms[this.formName]
          const oldForm = oldForms[this.formName]
          if (newForm && oldForm && newForm.pages[newForm.location.index].index !== oldForm.pages[oldForm.location.index].index) {
            console.log('page changed')
            this.makeResults()
          }          
        }
      })
    },
    methods: {
      next () {
        this.isBusy = true
        this.dir = 'next'
        bb.send('f-' + this.formName + '-n', this.results)
      },
      prev () {
        this.isBusy = true
        this.dir = 'prev'
        bb.send('f-' + this.formName + '-p', this.results)
      },
      seek (index: number) {
        this.isBusy = true
        bb.send('f-' + this.formName + '-s', this.results)
      },
      updateResult (block: BaseBlock, value: any) {
        const res = this.results[block.name]
        console.log('updateResult', block, value, res)
        if (res) {
          res.value = value
          res.updatedAt = new Date()
        }
      },
      makeResults () {
        if (this.form) {
          this.isBusy = false
          this.results = {}
          for (const section of this.form.page.sections) {
            for (const block of section.blocks) {
              console.log('block', block.name)
              if (block.name) {
                const res = {
                  value: (block.type === BlockType.CHOICE && block.multiple) || block.type === BlockType.SCALE ? [] : null,
                  updatedAt: new Date(),
                  createdAt: new Date()
                } as QuestionResult<Prim | Prim[]>
                this.results[block.name] = res
              }
            }
          }
        }
      },
      blockComponent (type: BlockType): Component {
        switch (type) {
          case BlockType.SCALE:
            return ScaleQuestion
          case BlockType.CHOICE:
            return ChoiceQuestion
          case BlockType.TEXT:
            return TextQuestion
          default:
            return HtmlBlock
        }
      }
    },
    computed: {
      formName (): string | null {
        let rName: string | null = null
        if (Array.isArray(this.name)) {
          for (const name of this.name) {
            if (this.forms[name]) {
              rName = name
              break
            }
          }
        } else if (this.name) {
          rName = this.name
        } else {
          const keys = Object.keys(this.forms)
          rName = keys.length ? keys[0] : null
        }
        if (rName !== this.prevName) {
          console.log('name changed')
          this.$nextTick(this.makeResults)
        }
        this.prevName = rName
        return rName
      },
      forms (): { [key: string]: PlayerForm } {
        // @ts-ignore
        if (!this.player || !this.player[this.formsKey]) {
          return {}
        } else {
          // @ts-ignore
          return this.player[this.formsKey]
        }
      },
      form (): PlayerForm | null {
        return this.formName ? this.forms[this.formName] : null
      }
    }
  })
</script>

<style lang="sass">
  
</style>