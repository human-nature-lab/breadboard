import { describe, it, expect } from 'vitest'
import { isEqual } from '../src/isEqual'

describe('isEqual', () => {
  it('compares primitives by strict equality', () => {
    expect(isEqual(1, 1)).toBe(true)
    expect(isEqual('a', 'a')).toBe(true)
    expect(isEqual(1, 2)).toBe(false)
    expect(isEqual(1, '1')).toBe(false)
  })

  it('uses loose (==) equality for null/undefined', () => {
    expect(isEqual(null, null)).toBe(true)
    expect(isEqual(null, undefined)).toBe(true)
    expect(isEqual(null, 0)).toBe(false)
    expect(isEqual({ a: 1 }, null)).toBe(false)
  })

  it('compares arrays element-wise', () => {
    expect(isEqual([1, 2, 3], [1, 2, 3])).toBe(true)
    expect(isEqual([1, 2, 3], [1, 2, 4])).toBe(false)
    // Documents a known asymmetry: the comparison only iterates the indices of
    // the FIRST argument, so a longer second array with matching prefix passes,
    // while a shorter one fails on the undefined mismatch.
    expect(isEqual([1, 2], [1, 2, 3])).toBe(true)
    expect(isEqual([1, 2, 3], [1, 2])).toBe(false)
  })

  it('compares nested objects by keys and values', () => {
    expect(isEqual({ a: 1, b: { c: 2 } }, { a: 1, b: { c: 2 } })).toBe(true)
    expect(isEqual({ a: 1 }, { a: 2 })).toBe(false)
    expect(isEqual({ a: 1 }, { a: 1, b: 2 })).toBe(false)
  })

  it('skips keys listed in ignoredKeys', () => {
    expect(isEqual({ a: 1, id: 'x' }, { a: 1, id: 'y' }, ['id'])).toBe(true)
    expect(isEqual({ a: 1, id: 'x' }, { a: 2, id: 'x' }, ['id'])).toBe(false)
  })
})
