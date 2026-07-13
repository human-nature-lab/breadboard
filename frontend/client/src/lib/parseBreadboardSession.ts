export function parseBreadboardSession () {
  const path = window.location.pathname
  // path has pattern of /game/:experimentId/:instanceId/:playerId/connected
  const [_, __, experimentId, instanceId, playerId, ___] = path.split('/')
  return {
    playerId,
    experimentId: parseInt(experimentId),
    instanceId: parseInt(instanceId),
  }
}
