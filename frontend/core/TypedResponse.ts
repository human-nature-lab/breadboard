export interface TypedResponse<T = any> extends Response {
  json (): Promise<T>
}
