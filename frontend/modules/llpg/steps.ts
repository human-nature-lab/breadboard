export enum Step {
  Decision = 'Decision',
  Loading = 'Loading',
  PostDecision = 'PostDecision',
  Distributing = 'Distributing',
  Results = 'Results',
  Complete = 'Complete',
  Distributed = 'Distributed'
}

export type Transform = {
  x: number
  y: number
  scale?: number
  zIndex?: number
}
type StepState = {
  flags: {
    showGroup: boolean
    showBox: boolean
    showPlayerItems: boolean
    isEnvelope: boolean
    showContributing: boolean
    showPending: boolean
    doubleBox: boolean
    boxOpen: boolean
    showBoxValue: boolean
  },
  transforms: {
    box: Transform
    contributing: Transform
    keeping: Transform
    pending: Transform
  }
}

const Decision: StepState = {
  flags: {
    showGroup: false,
    showBox: false,
    showPlayerItems: true,
    isEnvelope: true,
    showContributing: true,
    showPending: true,
    doubleBox: false,
    boxOpen: true,
    showBoxValue: false
  },
  transforms: {
    box: { x: 50, y: 50, scale: 1 },
    contributing: { x: 25, y: 20, scale: .75 },
    keeping: { x: 75, y: 25, scale: .75 },
    pending: { x: 50, y: 60 }
  }
}

const PostDecision: StepState = {
  flags: {
    showGroup: true,
    showBox: true,
    isEnvelope: true,
    showPlayerItems: true,
    showContributing: false,
    showPending: false,
    doubleBox: false,
    boxOpen: true,
    showBoxValue: false
  },
  transforms: {
    ...Decision.transforms,
    contributing: { ...Decision.transforms.box, scale: .3 },
    keeping: { x: 67, y: 93, scale: .5 }
  }
}

const Distributing: StepState = {
  flags: {
    showGroup: true,
    showBox: true,
    isEnvelope: false,
    showPlayerItems: false,
    showContributing: false,
    showPending: false,
    doubleBox: true,
    boxOpen: false,
    showBoxValue: true
  },
  transforms: {
    ...PostDecision.transforms
  }
}

const Results: StepState = {
  flags: {
    showGroup: true,
    showBox: true,
    isEnvelope: false,
    showPlayerItems: false,
    showContributing: false,
    showPending: false,
    doubleBox: false,
    boxOpen: false,
    showBoxValue: true
 },
  transforms: {
    ...PostDecision.transforms
  }
}

const Distributed: StepState = {
  flags: {
    showGroup: true,
    showBox: true,
    isEnvelope: false,
    showPlayerItems: true,
    showContributing: false,
    showPending: false,
    doubleBox: true,
    boxOpen: true,
    showBoxValue: false
  },
  transforms: {
    ...PostDecision.transforms
  }
}

export const steps: { [key: string]: StepState } = {
  Loading: Decision,
  Complete: Decision,
  Decision,
  PostDecision,
  Results,
  Distributing,
  Distributed
}