<template>
  <div class="choice">
    <div v-if="choice.custom" v-html="choice.custom" class="custom-choice"/>
    <v-btn :disabled="disabled" class="default-choice" @click="sendClick">
      <!-- Add something before the label -->
      <slot name="prepend" :choice="choice" :disabled="disabled"/>
      <!-- Replace the default label with your own label -->
      <slot :choice="choice" :disabled="disabled">
        {{choice.name}}
      </slot>
      <!-- Add something after the label -->
      <slot name="append" :choice="choice" :disabled="disabled"/>
    </v-btn>
  </div>
</template>

<script lang="ts">
  import Vue from 'vue'

  /**
   * Choices in Breadboard show up as buttons. This component makes it trivial to modify the contents of the button
   * without changing the default functionality.
   * @displayName Choice
   */
  export default Vue.extend({
    name: 'Choice',
    props: {
      /**
       * The choice data sent by breadboard
       */
      choice: Object,
      /**
       * Disable the choice
       */
      disabled: {
        type: Boolean,
        default: false
      }
    },
    methods: {
      /**
       * @private
       * @param e
       */
      sendClick (e: MouseEvent) {
        /**
         * Emit the click event when this choice is clicked
         * @event click
         * @property {MouseEvent} e
         */
        if (e.isTrusted) {
          this.$emit('click', e)
        }
      }
    },
    computed: {
      choiceAttrs (): any {
        const res: any = {}
        if (this.choice.class) {
          res.class = this.choice.class
        }
        return res
      }
    }
  })
</script>
