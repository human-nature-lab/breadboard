<template>
  <v-btn
    @click="onClick"
    v-on="{...$on, click: null }"
    v-bind="{...$attrs, disabled: disabled || touched}">
    <slot v-for="(_, name) in $slots" :name="name" :slot="name" />
    <template v-for="(_, name) in $scopedSlots" :slot="name" slot-scope="slotData">
      <slot :name="name" v-bind="slotData" />
    </template>
  </v-btn>
</template>

<script lang="ts">
  import Vue from 'vue'

  export default Vue.extend({
    name: 'TimeoutButton',
    props: {
      disabled: Boolean,
      duration: {
        type: Number,
        default: 3000
      }
    },
    data () {
      return {
        touched: false
      }
    },
    methods: {
      onClick (ev: any) {
        this.touched = true
        setTimeout(() => {
          this.touched = false
        }, this.duration)
        this.$emit('click', ev)
      }
    }
  })
</script>

<style lang="sass">
  
</style>