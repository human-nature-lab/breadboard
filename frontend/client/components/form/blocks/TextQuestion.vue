<template>
  <v-flex>
    <HtmlBlock :block="block" />
    <v-text-field
      :disabled="disabled"
      :required="block.isRequired"
      :rules="validationRules"
      :type="inputType"
      @change="$emit('update', $event)"
      v-model="content" />
  </v-flex>
</template>

<script lang="ts">
  import Vue from 'vue'
  import HtmlBlock from './ChoiceQuestion.vue'
  import { isRequired } from './rules'

  export default Vue.extend({
    name: 'TextQuestion',
    components: { HtmlBlock },
    props: {
      block: {
        type: Object,
        required: true
      },
      disabled: {
        type: Boolean,
        default: false
      }
    },
    data () {
      return {
        content: ''
      }
    },
    computed: {
      validationRules (): Function[] {
        const rules = []
        if (this.block.isRequired) {
          rules.push(isRequired(this.block.name))
        }
        return rules
      },
      inputType (): string {
        return this.block.inputType || 'text'
      }
    }
  })
</script>