import { describe, expect, it } from 'vitest';
import { extractApiError, extractRetryAfterSeconds } from './apiError';

describe('extractApiError', () => {
  it('prefers data.error, then data.message', () => {
    expect(extractApiError({ response: { data: { error: 'E', message: 'M' } } }, 'fb')).toBe('E');
    expect(extractApiError({ response: { data: { message: 'M' } } }, 'fb')).toBe('M');
  });

  it('ignores the raw err.message and returns the fallback', () => {
    expect(extractApiError(new Error('Request failed with status code 500'), 'fb')).toBe('fb');
    expect(extractApiError({ message: 'Network Error', response: { data: {} } }, 'fb')).toBe('fb');
    expect(extractApiError(undefined, 'fb')).toBe('fb');
  });
});

describe('extractRetryAfterSeconds', () => {
  it('reads the body, then the header, else null', () => {
    expect(extractRetryAfterSeconds({ response: { data: { retryAfterSeconds: 90 } } })).toBe(90);
    expect(extractRetryAfterSeconds({ response: { data: {}, headers: { 'retry-after': '30' } } })).toBe(30);
    expect(extractRetryAfterSeconds({ response: { data: {} } })).toBeNull();
  });
});
