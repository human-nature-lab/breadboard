// Retry a function up to a certain number of times with a delay between attempts. Delay doubles each attempt.
export async function retry<T> (fn: () => Promise<T>, retries = 3, delay = 100): Promise<T> {
  try {
    return await fn()
  } catch (error) {
    console.error('Error in retry', error)
    if (retries > 0) {
      await new Promise(resolve => setTimeout(resolve, delay))
      return retry(fn, retries - 1, delay * 2)
    }
    throw error
  }
}
