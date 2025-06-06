export type User = {
  email: string
  defaultLanguage: Language
  experiments: Experiment[]
  selectedExperiment: string
  currentScript: string
}

export type ExperimentEventData = {
  id: number
  name: string
  value: string
}

export type ExperimentEvent = {
  id: number
  name: string
  datetime: string
  eventData: ExperimentEventData[]
}

export type ExperimentInstance = {
  id: number
  createTime: string
  status: string
  name: string
  hitId: string
  hits: string[]
  data: string[]
  events: ExperimentEvent[]
}

export type Step = {
  id: number
  name: string
  source: string
}

export type Language = {
  id: number
  code: string
  name: string
}

export type Content = {
  // TODO
}

export type Parameter = {
  // TODO
}

export type Image = {
  // TODO
}

export type Experiment = {
  id: number
  name: string
  uid: string
  fileMode: boolean
  steps: Step[]
  languages: Language[]
  content: Content[]
  parameters: Parameter[]
  instances: ExperimentInstance[]
  images: Image[]
  style: string
  clientGraphHash: number
  clientHtmlHash: number
}