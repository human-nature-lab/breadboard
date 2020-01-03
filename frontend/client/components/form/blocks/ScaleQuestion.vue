<template>
  <v-flex v-if="block">
    <v-layout column>
      <v-flex>
        {{block.content}}
      </v-flex>
      <table class="w-full padded">
        <tr>
          <th></th>
          <th v-for="rank in block.scale" :key="rank.value" class="centered">
            {{rank.content}}
          </th>
        </tr>
        <RadioRow 
          v-for="(question, index) in block.questions" 
          :key="index" 
          :label="question.content"
          :options="options"
          v-model="values[index]"
          />
      </table>
    </v-layout>
  </v-flex>
</template>

<script lang="ts">
  import Vue from 'vue'
  import { ScaleQuestion } from '../form.types'

  export default Vue.extend({
    name: 'ScaleQuestion',
    props: {
      block: {
        type: Object as () => ScaleQuestion,
        required: true
      }
    },
    data () {
      return {
        values: []
      }
    },
    watch: {
      'block.questions' () {
        this.values.length = this.block.questions.length
      }
    },
    computed: {
      options (): string[] {
        return this.block.scale.map(s => s.value)
      }
    }
  })
</script>

<style lang="sass">
  .padded
    padding: 4px 10px
  .centered
    text-align: center
</style>