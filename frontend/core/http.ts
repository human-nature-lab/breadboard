import { TypedResponse } from './TypedResponse'

export class http {
  static get<T> (url: string, params = {}): Promise<TypedResponse<T>> {
    params = Object.assign({ method: 'GET' }, params)
    return fetch(url, params)
  }
}
