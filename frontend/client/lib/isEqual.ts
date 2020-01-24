export function isEqual (a: any, b: any, ignoredKeys: string[] = []): boolean {
  if (a == null || b == null) {
    return a == b
  } else if (Array.isArray(a)) {
    for (let i = 0; i < a.length; i++) {
      if (!isEqual(a[i], b[i], ignoredKeys)) {
        return false
      }
    }
    return true
  } else if (typeof a === 'object') {
    const aKeys = Object.keys(a).filter(k => !ignoredKeys.includes(k))
    const bKeys = Object.keys(b).filter(k => !ignoredKeys.includes(k))
    if (aKeys.length !== bKeys.length) {
      return false
    }
    for (const key of aKeys) {
      // console.log(a[key], b[key], key)
      if (!isEqual(a[key], b[key], ignoredKeys)) {
        return false
      }
    }
    return true
  } else {
    return a === b
  }
}

// @ts-ignore
window.isEqual = isEqual