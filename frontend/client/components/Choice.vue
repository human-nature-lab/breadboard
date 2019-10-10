<template>
  <v-flex>
    <div v-if="choice.custom" v-html="choice.custom" class="custom-choice"/>
    <v-btn :disabled="disabled" @click="$emit('click', $event)">
      <slot name="prepend" :choice="choice" :disabled="disabled"/>
      <slot name="label" :choice="choice" :disabled="disabled">
        {{choice.name}}
      </slot>
      <slot name="append" :choice="choice" :disabled="disabled"/>
    </v-btn>
  </v-flex>
</template>

<script lang="ts">
  import Vue from 'vue'

  export default Vue.extend({
    name: 'Choice',
    props: {
      choice: Object,
      disabled: {
        type: Boolean,
        default: false
      }
    }
  })
</script>
