export class HttpApi {
  private baseUrl: string
  private baseOpts: RequestInit
  private timeout: number = 10000
  private abortController = new AbortController()

  constructor(baseUrl: string, baseOpts?: RequestInit) {
    this.baseUrl = baseUrl
    this.baseOpts = baseOpts || {}
  }

  private mergeOpts(opts: RequestInit): RequestInit {
    // This merges headers correctly, combining baseOpts.headers and opts.headers if both exist.
    const mergedOpts = {
      ...this.baseOpts,
      ...opts,
      headers: {
        ...(this.baseOpts.headers || {}),
        ...(opts?.headers || {})
      }
    } as RequestInit
    // Add a signal to the request if it's not already present
    if (!mergedOpts.signal) {
      if (this.timeout > 0) {
        mergedOpts.signal = AbortSignal.any([this.abortController.signal, AbortSignal.timeout(this.timeout)])
      } else {
        mergedOpts.signal = this.abortController.signal
      }
    }
    return mergedOpts
  }

  async abortAll() {
    this.abortController.abort()
    this.abortController = new AbortController()
  }

  do(path: string, opts: RequestInit = {}) {
    const url = new URL(path, this.baseUrl)
    opts = this.mergeOpts(opts)
    return fetch(url.toString(), opts)
  }

  async delete(path: string, opts: RequestInit = {}) {
    opts.method = 'DELETE'
    return this.do(path, opts)
  }

  async get(path: string, opts: RequestInit = {}) {
    opts.method = 'GET'
    return this.do(path, opts)
  }

  async post(path: string, body?: BodyInit, opts: RequestInit = {}) {
    opts.method = 'POST'
    opts.body = body
    return this.do(path, opts)
  }

  async put(path: string, body?: BodyInit, opts: RequestInit = {}) {
    opts.method = 'PUT'
    opts.body = body
    return this.do(path, opts)
  }

  async patch(path: string, body?: BodyInit, opts: RequestInit = {}) {
    opts.method = 'PATCH'
    opts.body = body
    return this.do(path, opts)
  }

  async head(path: string, opts: RequestInit = {}) {
    opts.method = 'HEAD'
    return this.do(path, opts)
  }
}

export class JsonApi extends HttpApi {
  constructor(baseUrl: string, baseOpts: RequestInit = {}) {
    baseOpts.headers = {
      ...baseOpts.headers,
      'Content-Type': 'application/json'
    }
    super(baseUrl, baseOpts)
  }

  async get<R>(path: string, opts: RequestInit = {}) {
    const response = await super.get(path, opts)
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }
    return response.json() as Promise<R>
  }

  async post<B, R>(path: string, body?: B, opts: RequestInit = {}) {
    const response = await super.post(path, JSON.stringify(body), opts)
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }
    return response.json() as Promise<R>
  }

  async put<B, R>(path: string, body?: B, opts: RequestInit = {}) {
    const response = await super.put(path, JSON.stringify(body), opts)
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }
    return response.json() as Promise<R>
  }

  async patch<B, R>(path: string, body?: B, opts: RequestInit = {}) {
    const response = await super.patch(path, JSON.stringify(body), opts)
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }
    return response.json() as Promise<R>
  }

  async delete<R>(path: string, opts: RequestInit = {}) {
    const response = await super.delete(path, opts)
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }
    return response.json() as Promise<R>
  }

  async head<R>(path: string, opts: RequestInit = {}) {
    const response = await super.head(path, opts)
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }
    return response.json() as Promise<R>
  }
}