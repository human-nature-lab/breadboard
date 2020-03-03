<template>
  <div
      class="cell"
      :class="{block: type === CELL_TYPE.BLOCKED, input: type === CELL_TYPE.EDITABLE, empty: type === CELL_TYPE.EMPTY}"
      @click="handleFocus"
      :style="{width: size + 'px', height: size + 'px'}">
      <div v-if="label" class="label" :style="{fontSize: size / 3.5 + 'px'}">{{label}}</div>
      <input
        v-if="editable"
        ref="input" 
        type="text" 
        v-model="val"
        @keyup="$emit('keyup', $event)"
        @focus="handleFocus"
        @blur="$emit('blur')"
        :style="{fontSize: 3 * size / 4 + 'px', lineHeight: size + 'px'}" />
      <v-icon v-if="editable" v-show="active"
        class="arrow" 
        :class="{across: direction === DIRECTION.ACROSS, down: direction === DIRECTION.DOWN}">
        {{direction === DIRECTION.ACROSS ? 'mdi-arrow-right' : 'mdi-arrow-down'}}
      </v-icon>
  </div>
</template>

<script lang="ts">
  import Vue from 'vue'
  import { DIRECTION, CELL_TYPE } from './crossword.types'

  export default Vue.extend({
    name: 'Cell',
    props: {
      value: {
        type: String
      },
      size: {
        type: Number,
        default: 10
      },
      type: {
        type: Number as () => CELL_TYPE,
        required: true
      },
      label: {
        type: [String, Number]
      },
      direction: {
        type: String as () => DIRECTION
      },
      active: {
        type: Boolean
      }
    },
    data () {
      return {
        DIRECTION,
        CELL_TYPE
      }
    },
    computed: {
      val: {
        get (): string {
          return this.value
        },
        set (newVal: string) {
          const val = newVal.slice(-1).toUpperCase()
          this.$emit('input', val)
          this.$emit('change', val)
        }
      },
      editable (): boolean {
        return this.type === CELL_TYPE.EDITABLE
      }
    },
    methods: {
      handleFocus () {
        this.$emit('focus')
        this.focus()
      },
      focus () {
        // Select all of the existing characters to overwrite them
        if (this.value && this.$refs.input instanceof HTMLInputElement) {
          this.$refs.input.select()
        }
        // Actually focus the input if we haven't already
        if (!this.isFocused() && this.$refs.input instanceof HTMLInputElement) {
          this.$refs.input.focus()
        }
      },
      isFocused (): boolean {
        return this.$refs.input === document.activeElement
      }
    }
  })
</script>

<style lang="sass" scoped>
  $size: 24px
  $border: grey
  .cell
    position: relative
    box-sizing: border-box
    float: left
    overflow: visible
  .block
    background: black
  input
    width: 100%
    height: 100%
    text-align: center
    cursor: pointer
  .input
    cursor: pointer
    border: 1px solid $border
    // border-left: 1px solid $border
    // border
    &:hover
      background: lightgrey
  .label
    user-select: none
    padding-top: 1%
    padding-left: 3%
    position: absolute
    z-index: 0
    top: 0
    left: 0
  .arrow
    position: absolute
    &.across
      top: calc(50% - #{$size / 2})
      right: -$size
    &.down
      bottom: -$size
      left: calc(50% - #{$size / 2})
</style>
