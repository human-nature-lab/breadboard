export enum BlockType {
  CHOICE = 'CHOICE',
  HTML = 'HTML',
  TEXT = 'TEXT',
  SCALE = 'SCALE'
}

export type Prim = string | number | boolean | null

export interface FormError {
  error: string
}

export interface QuestionResult<T> {
  value: T
  updatedAt: Date
  createdAt: Date
}

export interface KeyLabel {
  content: string
  value: string
}

export interface BaseBlock {
  type: BlockType
  isRequired: boolean
  name: string
}

export interface ScaleQuestion extends BaseBlock {
  type: BlockType.SCALE
  content: string
  choices: KeyLabel[]
  items: KeyLabel[]
}

export interface ChoiceQuestion extends BaseBlock {
  type: BlockType.CHOICE
  multiple?: boolean
  choices: KeyLabel[]
}

export interface HtmlQuestion extends BaseBlock {
  type: BlockType.HTML
  content: string
}

type Block = ScaleQuestion | ChoiceQuestion | HtmlQuestion

export interface FormPage {
  title: string
  sections: {
    blocks: Block[]
  }[]
}

export interface PlayerForm {
  useStepper: boolean
  nonLinear: boolean
  efficient: boolean
  location: {
    index: number
    size: number
  },
  pages: {
    index: number
    title: string
  }[]
  page: FormPage
}

export interface PlayerWithForms {
  forms: {
    [key: string]: PlayerForm
  }
}