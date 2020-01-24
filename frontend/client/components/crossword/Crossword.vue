<template>
  <div class="crossword h-full" v-if="value !== null" ref="container">  
    <div class="grid m-auto " v-resize="updateCellSize" :style="{ width: width + 'px', height: width + 'px' }">
      <template v-for="(row, rowIndex) in crossword.layout">
        <template v-for="(cellType, colIndex) in row">
          <Cell ref="cells"
              :class="{clearfix: colIndex === row.length - 1 && rowIndex === crossword.layout.length - 1}"
              :key="rowIndex + '-' + colIndex"
              :size="cellSize"
              :type="cellType"
              :label="labels[rowIndex][colIndex]"
              :direction="direction"
              :active="active.row === rowIndex && active.col === colIndex"
              @keyup="moveCursor"
              @blur="active.row = -1; active.col = -1;"
              @focus="setActive(rowIndex, colIndex)"
              @change="onCellChange"
              v-model="value[rowIndex][colIndex]" />
          </template>
      </template>
    </div>
  </div>
</template>

<script lang="ts">
  import Vue from 'vue'
  import { Crossword, DIRECTION, CURSOR, CELL_TYPE, Position } from './crossword.types'

  export default Vue.extend({
    name: 'Crossword',
    props: {
      crossword: {
        type: Object as () => Crossword,
        required: true
      },
      conflicts: {
        type: Array
      },
      value: {
        type: Array as () => string[][],
        required: true
      },
      direction: {
        type: String as () => DIRECTION,
        default: DIRECTION.ACROSS
      },
      active: {
        type: Object as () => Position,
        default: () => ({
          row: -1,
          col: -1
        })
      }
    },
    data () {
      const labels: (string | null)[][] = []
      for (let row = 0; row < this.crossword.size.rows; row++) {
        const rowArr = []
        for (let col = 0; col < this.crossword.size.cols; col++) {
          rowArr[col] = null
        }
        labels.push(rowArr)
      }
      for (const label of this.crossword.labels) {
        labels[label.row][label.col] = label.text
      }
      return {
        cellSize: 20,
        labels,
        DIRECTION,
        CELL_TYPE,
        width: 200
      }
    },
    mounted () {
      console.log('value', this.value)
      this.updateCellSize()
    },
    watch: {
      active: {
        handler () {
          console.log('active watch')
          this.setActive(this.active.row, this.active.col)
        },
        deep: true
      }
    },
    methods: {
      getNextCell () {
        if (this.direction === DIRECTION.ACROSS) {
          this.moveRight()
        } else {
          this.moveDown()
        }
      },
      setActive (row: number, col: number) {
        if (row >= this.crossword.size.rows || row < 0 || col >= this.crossword.size.cols || col < 0 || this.crossword.layout[row][col] !== CELL_TYPE.EDITABLE) return
        console.log('set active', row, col)
        if (this.active.row !== row || this.active.col !== col) {
          this.active.row = row
          this.active.col = col
        }
        if (Array.isArray(this.$refs.cells)) {
          // @ts-ignore
          this.$refs.cells[row * this.crossword.size.cols + col].focus()
        }
      },
      onCellChange (val: string) {
        console.log('update val', val, this.active)
        this.$emit('update:cell', val, this.active)
        if (!val) return
        this.getNextCell()
      },
      updateCellSize () {
        const c = this.$refs.container
        if (c instanceof Element) {
          const box = c.getBoundingClientRect()
          const min = Math.min(box.width, box.height)
          this.width = min
          this.cellSize = min / this.crossword.size.cols
        }
      },
      moveUp () {
        const col = this.active.col
        for (let row = this.active.row - 1; row >= 0; row--) {
          if (this.crossword.layout[row][col] === CELL_TYPE.EDITABLE) {
            return this.setActive(row, col)
          }
        }
      },
      moveRight () {
        const row = this.active.row
        for (let col = this.active.col + 1; col < this.crossword.size.cols; col++) {
          if (this.crossword.layout[row][col] === CELL_TYPE.EDITABLE) {
            return this.setActive(row, col)
          }
        }
      },
      moveDown () {
        const col = this.active.col
        for (let row = this.active.row + 1; row < this.crossword.size.rows; row++) {
          if (this.crossword.layout[row][col] === CELL_TYPE.EDITABLE) {
            return this.setActive(row, col)
          }
        }
      },
      moveLeft () {
        const row = this.active.row
        for (let col = this.active.col - 1; col >= 0; col--) {
          if (this.crossword.layout[row][col] === CELL_TYPE.EDITABLE) {
            return this.setActive(row, col)
          }
        }
      },
      moveCursor (event: KeyboardEvent) {
        console.log('key', event.keyCode)
        if (!CURSOR[event.keyCode]) return
        if (event.keyCode === CURSOR.UP) {
          if (this.direction === DIRECTION.ACROSS) {
            this.$emit('update:direction', DIRECTION.DOWN)
          } else {
            this.moveUp()
          }
        } else if (event.keyCode === CURSOR.DOWN) {
          if (this.direction === DIRECTION.ACROSS) {
            this.$emit('update:direction', DIRECTION.DOWN)
          } else {
            this.moveDown()
          }
        } else if (event.keyCode === CURSOR.LEFT) {
          if (this.direction === DIRECTION.DOWN) {
            this.$emit('update:direction', DIRECTION.ACROSS)
          } else {
            this.moveLeft()
          }
        } else if (event.keyCode === CURSOR.RIGHT) {
          if (this.direction === DIRECTION.DOWN) {
            this.$emit('update:direction', DIRECTION.ACROSS)
          } else {
            this.moveRight()
          }
        }
      }
    }
  })
</script>

<style lang="sass" scoped>
  .grid
    // width: 100%
    border-bottom: 1px solid grey
    border-right: 1px solid grey
    overflow: visible
    box-sizing: content-box
    .clearfix::after 
      content: ""
      clear: both
      display: table
</style>
