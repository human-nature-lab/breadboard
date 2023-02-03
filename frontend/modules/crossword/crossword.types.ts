export interface Crossword {
  size: {
    cols: number
    rows: number
  }
  lastBatchId: number
  layout: CELL_TYPE[][] // cell indices within the grid which are editable
  labels: Label[]
  clues: Clue[]
  words?: Word[]
  solution?: string[][]
}

export interface Word {
  id: string
  rows: number | [number, number]
  cols: number | [number, number]
}

export interface Clue {
  label: string
  type: DIRECTION
  text: string
}

export interface Label {
  col: number
  row: number
  text: string
}

export enum CELL_TYPE {
  EMPTY = -1,
  EDITABLE = 0,
  BLOCKED = 1
}

export enum DIRECTION {
  DOWN = 'down',
  ACROSS = 'across'
}

export enum CURSOR {
  UP = 38,
  DOWN = 40,
  LEFT = 37,
  RIGHT = 39
}

export enum KEYS {
  BACKSPACE = 8,
  HOME = 36,
  END = 35
}

export interface Position {
  row: number
  col: number
}