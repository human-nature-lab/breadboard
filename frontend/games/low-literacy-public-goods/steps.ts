export enum Step {
  Decision = 'Decision',
  Loading = 'Loading',
  PostDecision = 'PostDecision',
  Distributing = 'Distributing',
  Results = 'Results',
  Complete = 'Complete'
}

export type Transform = {
  x: number
  y: number
  scale?: number
}
type StepState = {
  flags: {
    showGroup: boolean
    showBox: boolean
    showPlayerItems: boolean
    isEnvelope: boolean
    showContributing: boolean
    showPending: boolean
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
    showPending: true
  },
  transforms: {
    box: { x: 40, y: 40, scale: 1 },
    contributing: { x: 10, y: 0, scale: 1 },
    keeping: { x: -10, y: 0, scale: 1 },
    pending: { x: 40, y: -20 }
  }
}

const PostDecision: StepState = {
  flags: {
    showGroup: true,
    showBox: true,
    isEnvelope: true,
    showPlayerItems: true,
    showContributing: false,
    showPending: false
  },
  transforms: {
    ...Decision.transforms,
    contributing: { ...Decision.transforms.box, scale: .3 },
    keeping: { x: 0, y: 70, scale: .5 }
  }
}

const Distributing: StepState = {
  flags: {
    showGroup: true,
    showBox: true,
    isEnvelope: false,
    showPlayerItems: true,
    showContributing: false,
    showPending: false
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
  Results: PostDecision,
  Distributing
}