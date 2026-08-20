/** Response.json() is typed `unknown`; tests assert on loose response shapes. */
export async function readJson<T = any>(res: Response): Promise<T> {
  return (await res.json()) as T;
}
