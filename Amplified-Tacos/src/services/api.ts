import axios from "axios"
import type { AxiosRequestConfig, AxiosRequestTransformer } from 'axios'

export const api = () =>{
    const apiHost = import.meta.env.VITE_API_HOST

    const ax = axios.create({
        baseURL: apiHost
    })

    const http = {
        get: async function<R>(url: string, updatedConfig?: AxiosRequestConfig) {
            const rsp = await ax.get<R>(url, updatedConfig)
            return rsp
          },

          post: async function<R, B = unknown>(url: string, updatedConfig?: AxiosRequestConfig<B>) {
            return await ax.post<R>(url, updatedConfig?.data)

          },
    }

    return http
}

export default api()