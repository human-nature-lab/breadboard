<template>
  <v-input :errorMessages="errorMessages">
    <v-layout column v-if="block">
      <HtmlBlock :block="block" />
        <v-flex v-for="(choice, index) in block.choices" :key="choice.value">
          <v-checkbox
            v-bind="selectIcons"
            :label="'' + choice.content"
            @change="assign(index, choice.value)" 
            :value="isAssigned(index, choice.value)" />
        </v-flex>
    </v-layout>
  </v-input>
</template>

<script lang="ts">
  import Vue, { PropOptions } from 'vue'
  import HtmlBlock from './HtmlBlock.vue'
  import { QuestionResult, Prim } from '../form.types'
  import { isRequired } from './rules'

  export default Vue.extend({
    name: 'ChoiceQuestion',
    components: { HtmlBlock },
    props: {
      block: {
        type: Object,
        required: true
      }
    },
    data () {
      return {
        isDirty: false,
        value: this.block.multiple ? [] : null as boolean[] | Prim
      }
    },
    methods: {
      assign (index: number, value: string): void {
        this.isDirty = true
        let updateRes: string | string[] = value
        if (this.block.multiple && Array.isArray(this.value)) {
          this.$set(this.value, index, !this.value[index])
          updateRes = []
          for (let i = 0; i < this.value.length; i++) {
            if (this.value[i]) {
              updateRes.push(this.block.choices[i].value)
            }
          }
        } else {
          this.value = this.value !== value ? value : null
        }
        this.$emit('update', updateRes)
      },
      isAssigned (index: number, value: string): boolean {
        if (this.block.multiple && Array.isArray(this.value)) {
          return this.value[index]
        } 
        return this.value === value
      }
    },
    computed: {
      selectIcons (): object {
        return this.block.multiple ? {} : {
          onIcon: 'mdi-radiobox-marked',
          offIcon: 'mdi-radiobox-blank'
        }
      },
      validationRules (): Function[] {
        const rules = []
        if (this.block.isRequired) {
          rules.push(isRequired(this.block.name))
        }
        return rules
      },
      errorMessages (): string[] {
        const messages = []
        for (const rule of this.validationRules) {
          const r: string | true = rule(this.value)
          if (this.isDirty && r !== true) {
            messages.push(r)
          }
        }
        return messages
      }
    }
  })
</script>

<style lang="sass">
  
</style>