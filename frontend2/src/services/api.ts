import { JsonApi } from '@/lib/http'

export const api = new JsonApi(import.meta.env.VITE_API_ROOT, {
  credentials: 'include',
  mode: 'cors',
})

export type LoginRequest = {
  email: string
  password: string
}

export type LoginResponse = {
  uid: string
  email: string
  juid: string
}

export type StateResponse = {
  uid: string
  juid: string
  email: string
  connectSocket: string // websocket url for the instance
}

export const login = async (req: LoginRequest) => {
  const response = await api.post<LoginRequest, LoginResponse>('/login', req)
  return response
}