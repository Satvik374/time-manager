/*
 * PIN hashing via WebCrypto PBKDF2 — the browser counterpart of core/PinHasher.kt.
 *
 * A 4–8 digit PIN has a tiny keyspace, so the iteration count is the only thing standing between
 * a dumped localStorage entry and the PIN. This protects the PIN value; it is not encryption of
 * the stored schedule, which is exactly what the UI tells the user.
 */

export const PIN_MIN_LENGTH = 4
export const PIN_MAX_LENGTH = 8
export const PBKDF2_ITERATIONS = 120_000
const KEY_LENGTH_BITS = 256
const SALT_BYTES = 16
const ALGORITHM = 'PBKDF2-SHA256'

export function isValidPin(pin: string): boolean {
  return (
    pin.length >= PIN_MIN_LENGTH &&
    pin.length <= PIN_MAX_LENGTH &&
    /^\d+$/.test(pin)
  )
}

export interface PinDigest {
  algorithm: string
  iterations: number
  salt: string
  hash: string
}

export function serializeDigest(digest: PinDigest): string {
  return [digest.algorithm, String(digest.iterations), digest.salt, digest.hash].join('$')
}

export function parseDigest(raw: string | null | undefined): PinDigest | null {
  if (!raw) return null
  const parts = raw.split('$')
  if (parts.length !== 4) return null
  const iterations = Number(parts[1])
  if (!Number.isInteger(iterations) || iterations <= 0) return null
  return { algorithm: parts[0], iterations, salt: parts[2], hash: parts[3] }
}

async function derive(pin: string, salt: Uint8Array, iterations: number): Promise<ArrayBuffer> {
  const key = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(pin),
    'PBKDF2',
    false,
    ['deriveBits'],
  )
  return crypto.subtle.deriveBits(
    { name: 'PBKDF2', salt: salt as unknown as BufferSource, iterations, hash: 'SHA-256' },
    key,
    KEY_LENGTH_BITS,
  )
}

export async function hashPin(pin: string): Promise<PinDigest> {
  const salt = crypto.getRandomValues(new Uint8Array(SALT_BYTES))
  const bits = await derive(pin, salt, PBKDF2_ITERATIONS)
  return {
    algorithm: ALGORITHM,
    iterations: PBKDF2_ITERATIONS,
    salt: toBase64(salt),
    hash: toBase64(new Uint8Array(bits)),
  }
}

export async function verifyPin(pin: string, digest: PinDigest): Promise<boolean> {
  let salt: Uint8Array
  let expected: Uint8Array
  try {
    salt = fromBase64(digest.salt)
    expected = fromBase64(digest.hash)
  } catch {
    return false
  }
  try {
    const bits = new Uint8Array(await derive(pin, salt, digest.iterations))
    return constantTimeEquals(expected, bits)
  } catch {
    return false
  }
}

/** Compares without an early exit, so timing can't leak how much of the digest matched. */
function constantTimeEquals(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) return false
  let difference = 0
  for (let index = 0; index < a.length; index++) difference |= a[index] ^ b[index]
  return difference === 0
}

function toBase64(bytes: Uint8Array): string {
  let binary = ''
  for (const byte of bytes) binary += String.fromCharCode(byte)
  return btoa(binary)
}

function fromBase64(value: string): Uint8Array {
  const binary = atob(value)
  const bytes = new Uint8Array(binary.length)
  for (let index = 0; index < binary.length; index++) bytes[index] = binary.charCodeAt(index)
  return bytes
}
