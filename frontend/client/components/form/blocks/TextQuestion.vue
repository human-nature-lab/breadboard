<template>
  <v-flex>
    <HtmlBlock :block="block" />
    <v-text-field
      :disabled="disabled"
      :required="block.isRequired"
      @change="$emit('update', $event)"
      v-model="content" />
  </v-flex>
</template>

<script lang="ts">
  import Vue from 'vue'
  import HtmlBlock from './ChoiceQuestion.vue'

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
    }
  })
</script>