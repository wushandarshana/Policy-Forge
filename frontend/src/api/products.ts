import { requestJson } from './http';
import type { Product } from './types';

/** C1: public catalog list. */
export function listProducts(): Promise<Product[]> {
  return requestJson<Product[]>('/api/products');
}
