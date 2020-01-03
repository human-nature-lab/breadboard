export enum BlockType {
  MULTIPLE_SELECT = 'MULTIPLE_SELECT',
  MULTIPLE_CHOICE = 'MULTIPLE_CHOICE',
  HTML = 'HTML',
  TEXT = 'TEXT',
  SCALE = 'SCALE'
}

export interface KeyLabel {
  content: string
  value: string
}

export interface ScaleQuestion {
  type: BlockType.SCALE
  content: string
  scale: KeyLabel[]
  questions: KeyLabel[]
}

export interface MultipleChoiceQuestion {
  type: BlockType.MULTIPLE_CHOICE
  choices: KeyLabel[]
}

export interface MultipleSelectQuestion {
  type: BlockType.MULTIPLE_SELECT
  choices: KeyLabel[]
}

export interface HtmlQuestion {
  type: BlockType.HTML
  content: string
}

type Block = ScaleQuestion | MultipleChoiceQuestion | MultipleSelectQuestion | HtmlQuestion

export interface PlayerForm {
  useStepper: boolean
  nonLinear: boolean
  location: {
    index: number
    size: number
  },
  titles: string[]
  results: object[]
  page: {
    title: string
    sections: {
      blocks: Block[]
    }[]
  }
}

export interface PlayerWithForms {
  forms: {
    [key: string]: PlayerForm
  }
}