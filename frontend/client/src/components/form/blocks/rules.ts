type ArrayLike = { length: number }

export function isRequired (fieldName: string) {
  const msg = `${fieldName} is required`
  return function (value: any) {
    if (typeof value === 'string') {
      return value.length > 0 || msg
    } else if (Array.isArray(value)) {
      return value.filter(v => v).length > 0 || msg
    }
    return value !== null && value !== undefined || msg
  }
}

export function minSelected (min: number, msg?: string) {
  if (!msg) {
    msg = `Must select ${min} or more`
  }
  return function (val: ArrayLike) {
    return val.length >= min || msg
  }
}

export function maxSelected (max: number, msg?: string) {
  if (!msg) {
    msg = `Must select ${max} or fewer`
  }
  return function (val: ArrayLike) {
    return val.length <= max || msg
  }
}

export function clampSelected (min: number, max: number, msg?: string) {
  if (!msg) {
    msg = `Must enter between ${min} and ${max}`
  }
  return function (val: ArrayLike) {
    return val.length <= max && val.length >= min || msg
  }
}

export function minText (min: number, msg?: string) {
  if (!msg) {
    msg = `Enter ${min} characters or more`
  }
  return function (val: ArrayLike) {
    return val.length >= min || msg
  }
}

export function maxText (max: number, msg?: string) {
  if (!msg) {
    msg = `Enter ${max} characters or fewer`
  }
  return function (val: ArrayLike) {
    return val.length <= max || msg
  }
}