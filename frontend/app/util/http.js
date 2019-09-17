export const http = {
  async get (url, params) {
    params = Object.assign({ method: 'GET' }, params)
    return fetch(url, params)
  }
}
