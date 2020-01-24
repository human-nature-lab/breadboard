<template>
  <v-flex v-if="block">
    <HtmlBLock :block="block" />
    <v-input :errorMessages="errorMessages">
      <v-simple-table class="w-full padded">
        <tr>
          <th></th>
          <th v-for="choice in block.choices" :key="choice.value" class="centered">
            {{choice.content}}
          </th>
        </tr>
        <RadioRow 
          v-for="(question, index) in block.items" 
          :key="index"
          :label="question.content"
          :options="options"
          :value="value[index]"
          :disabled="disabled"
          @input="updateVal(index, $event)" />
      </v-simple-table>
    </v-input>
    
  </v-flex>
</template>

<script lang="ts">
  import Vue from 'vue'
  import HtmlBLock from './HtmlBlock.vue'
  import { ScaleQuestion, QuestionResult, KeyLabel, Prim } from '../form.types'
  import { isRequired } from './rules'

  export default Vue.extend({
    name: 'ScaleQuestion',
    components: { HtmlBLock },
    props: {
      block: {
        type: Object as () => ScaleQuestion,
        required: true
      },
      disabled: {
        type: Boolean,
        default: false
      }
    },
    data () {
      return {
        prevBlockName: '',
        isDirty: false,
        value: [] as (string | number | null)[]
      }
    },
    watch: {
      'block.items' (newItems: KeyLabel[]) {
        this.initValue()
      }
    },
    created () {
      this.initValue()
    },
    computed: {
      options (): string[] {
        return this.block.choices.map(s => s.value)
      },
      rules (): Function[] {
        const rules = []
        if (this.block.isRequired) {
          rules.push((value: Prim[]) => {
            return !!value.length && value.filter(v => v !== null).length === this.block.items.length || `${this.block.name} is required.`
          })
        }
        return rules
      },
      errorMessages (): string[] {
        const messages = []
        for (const rule of this.rules) {
          const res = rule(this.value)
          if (res !== true) {
            messages.push(res)
          }
        }
        return this.isDirty ? messages : []
      }
    },
    methods: {
      initValue () {
        if (this.block && this.block.name === this.prevBlockName) return
        this.isDirty = false
        this.value = Array.from( { length: this.block.items.length }).fill(null) as null[]
        this.prevBlockName = this.block.name
      },
      updateVal (index: number, value: string) {
        this.isDirty = true
        this.$set(this.value, index, this.value[index] !== value ? value : null)
        this.$emit('update', this.value)
      }
    }
  })
</script>

<style lang="sass">
  .padded
    padding: 4px 10px
  // .centered
  //   text-align: center
</style>