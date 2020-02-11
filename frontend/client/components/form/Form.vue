<template>
  <v-stepper 
    v-if="form"
    :value="form.location.index + 1">
    <v-stepper-header 
      :non-linear="form.nonLinear"
      v-if="form.showStepper && form.pages.length > 1">
      <v-stepper-step 
        v-for="(page, index) in form.pages"
        :key="index + 1"
        :complete="form.location.index > index"
        :step="index + 1">{{page.title}}</v-stepper-step>
    </v-stepper-header>
    <slot name="loading" v-if="useEfficientLoading && fetchTimeoutId">
      Loading form...
    </slot>
    <v-form v-model="valid" v-if="form" @submit.prevent="()=>{}">
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
  import { PlayerWithForms, PlayerForm, BlockType, QuestionResult, Prim, BaseBlock, FormError, FormPage } from './form.types'
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
      },
      fetchTimeout: {
        type: Number,
        default: 5000
      }
    },
    data () {
      return {
        isBusy: false,                    // set to true when form is transferring data
        valid: false,                     // indicates state of frontend validators
        dir: null as string | null,       // keeps track of which button was pressed
        error: null as FormError | null,  // Applied if error in form navigation
        results: {} as { [key: string]: QuestionResult<Prim | Prim[]> },
        prevName: null as string | null,
        fetchTimeoutId: null as number | null,
        memForm: null as null | PlayerForm // This gets updated when the form is in "efficient" mode
      }
    },
    created () {
      this.startListening()
      if (this.useEfficientLoading) {
        this.fetch()
      }
      this.$watch(`player.${this.formsKey}`, (newForms: PlayerForms, oldForms: PlayerForms) => {
        if (this.formName) {
          if (!oldForms) {
            console.log('no old forms')
            return this.makeResults()
          } else if (!newForms) {
            this.reset()
          }
          const newForm = newForms[this.formName]
          const oldForm = oldForms[this.formName]
          if (newForm && oldForm && !newForm.efficient && newForm.pages[newForm.location.index].index !== oldForm.pages[oldForm.location.index].index) {
            console.log('page changed')
            this.makeResults()
          }
        }
      })
    },
    methods: {
      reset () {
        console.log('reset form')
        this.isBusy = false
        this.valid = false
        this.prevName = null
        this.error = null
        this.memForm = null
      },
      next () {
        this.isBusy = true
        this.dir = 'next'
        bb.send('f-next', {
          name: this.formName,
          results: this.results
        })
      },
      prev () {
        this.isBusy = true
        this.dir = 'prev'
        bb.send('f-prev', {
          name: this.formName,
          results: this.results
        })
      },
      seek (index: number) {
        this.isBusy = true
        bb.send('f-seek', {
          name: this.formName,
          results: this.results
        })
      },
      fetch () {
        if (this.fetchTimeoutId) return
        console.log('fetch')
        this.isBusy = true
        bb.send('f-fetch', {
          name: this.formName
        })
        this.fetchTimeoutId = setTimeout(() => {
          this.fetchTimeoutId = null
          console.log('fetch timeout')
          if (this.formName) {
            this.fetch()
          }
        }, this.fetchTimeout)
      },
      onError (data: {name: string, err: FormError}) {
        if (data.name !== this.formName)
        this.isBusy = false
        this.error = data.err
      },
      onFetch (data: { name: string, form: PlayerForm }) {
        if (data.name !== this.formName) return
        this.isBusy = false
        this.memForm = data.form
        this.makeResults()
        if (this.fetchTimeoutId) {
          clearTimeout(this.fetchTimeoutId)
          this.fetchTimeoutId = null
        }
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
      },
      startListening (): void {
        window.Breadboard.on('f-error', this.onError)
        window.Breadboard.on('f-fetch', this.onFetch)
        window.Breadboard.on('open', this.fetch)
      },
      stopListening (): void {
        window.Breadboard.off('f-error', this.onError)
        window.Breadboard.off('f-fetch', this.onFetch)
        window.Breadboard.off('open', this.fetch)
      }
    },
    computed: {
      useEfficientLoading (): boolean {
        return this.formName ? this.forms[this.formName].efficient : false
      },
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
        if (rName !== null && rName !== this.prevName) {
          console.log('name changed')
          this.$nextTick(this.makeResults)
          this.$nextTick(this.fetch)
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
        if (this.formName) {
          const form = this.forms[this.formName]
          if (this.memForm && form.efficient) {
            return this.memForm 
          } else if (form.page) {
            return form
          } else {
            return null
          }
        } else {
          return null
        }
      }
    }
  })
</script>

<style lang="sass">
  
</style>