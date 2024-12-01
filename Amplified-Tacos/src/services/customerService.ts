import { Customer } from '@/models/models'
import http from './api'

export default {
    endpoint: 'customer',

    async createOrUpdateCustomer(customer: Customer) {
        const result = await http.post<string>(`${this.endpoint}`, {
            data: customer,
          })
        return result.data
    }
}