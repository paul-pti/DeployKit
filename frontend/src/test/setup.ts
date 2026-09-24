import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'
import '@testing-library/jest-dom/vitest'

// vitest.config sets `globals: false`, so Testing Library's own auto-cleanup (which only registers itself
// when it finds a global `afterEach`) never kicks in. Do it explicitly instead, for every test file.
afterEach(cleanup)
