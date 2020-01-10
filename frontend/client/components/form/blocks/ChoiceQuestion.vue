<template>
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
</template>

<script lang="ts">
  import Vue, { PropOptions } from 'vue'
  import HtmlBlock from './HtmlBlock.vue'
  import { QuestionResult, Prim } from '../form.types'

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
        value: this.block.multiple ? [] : null as boolean[] | Prim
      }
    },
    methods: {
      assign (index: number, value: string): void {
        let updateRes: string | string[] = value
        if (this.block.multiple && Array.isArray(this.value)) {
          this.value[index] = !this.value[index]
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
      }
    }
  })
</script>

<style lang="sass">
  
</style>