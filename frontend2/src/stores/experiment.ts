import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface Experiment {
  id: string
  name: string
  description?: string
  createdAt: Date
  updatedAt: Date
}

export const useExperimentStore = defineStore('experiment', () => {
  const experiments = ref<Experiment[]>([])
  const currentExperiment = ref<Experiment | null>(null)

  const setExperiments = (newExperiments: Experiment[]) => {
    experiments.value = newExperiments
  }

  const setCurrentExperiment = (experiment: Experiment | null) => {
    currentExperiment.value = experiment
  }

  const addExperiment = (experiment: Experiment) => {
    experiments.value.push(experiment)
  }

  const removeExperiment = (id: string) => {
    experiments.value = experiments.value.filter(exp => exp.id !== id)
    if (currentExperiment.value?.id === id) {
      currentExperiment.value = null
    }
  }

  return {
    experiments,
    currentExperiment,
    setExperiments,
    setCurrentExperiment,
    addExperiment,
    removeExperiment
  }
}) 