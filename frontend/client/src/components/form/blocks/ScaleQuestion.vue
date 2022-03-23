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
          v-for="question in block.items" 
          :key="question.value"
          :label="question.content"
          :options="options"
          :value="value[question.value]"
          :disabled="disabled"
          @input="updateVal(question.value, $event)" />
      </v-simple-table>
    </v-input>
  </v-flex>
</template>

<script lang="ts">
  import Vue from 'vue'
  import HtmlBLock from './HtmlBlock.vue'
  import { ScaleQuestion, QuestionResult, KeyLabel, Prim } from '../form.types'
  import { isRequired } from './rules'

  type ScaleValue = { [key: string]: string|number|null, [key: number]: string|number|null}

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
        value: {} as ScaleValue
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
          rules.push((value: ScaleValue) => {
            for (const question of this.block.items) {
              if (!value[question.value]) {
                return `${this.block.name} is required.`
              }
            }
            return true
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
        const d: ScaleValue = {}
        for (const question of this.block.items) {
          d[question.value] = null
        }
        this.value = d
        this.prevBlockName = this.block.name
      },
      updateVal (key: string|number, value: string) {
        this.isDirty = true
        this.$set(this.value, key, this.value[key] !== value ? value : null)
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