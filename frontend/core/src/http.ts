export class http {
  static get<T> (url: string, params = {}) {
    params = Object.assign({ method: 'GET' }, params)
    return fetch(url, params)
  }
}
