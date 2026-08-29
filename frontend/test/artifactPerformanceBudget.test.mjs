import test from 'node:test'
import assert from 'node:assert/strict'
import {existsSync, readFileSync, readdirSync, statSync} from 'node:fs'
import path from 'node:path'

const frontendRoot = path.resolve(import.meta.dirname, '..')
const optimizedBackground = path.join(frontendRoot, 'src/assets/MHY_XTLL.webp')

const sourceFiles = directory => readdirSync(directory, {withFileTypes: true}).flatMap(entry => {
  const target = path.join(directory, entry.name)
  if (entry.isDirectory()) return sourceFiles(target)
  return /\.(css|vue)$/.test(entry.name) ? [target] : []
})

test('shared page background stays within the browser memory budget', () => {
  assert.equal(existsSync(optimizedBackground), true)
  assert.ok(statSync(optimizedBackground).size <= 1_500_000)

  const staleReferences = sourceFiles(path.join(frontendRoot, 'src'))
    .filter(file => readFileSync(file, 'utf8').includes('MHY_XTLL.png'))
  assert.deepEqual(staleReferences, [])

  const fixedBackgrounds = sourceFiles(path.join(frontendRoot, 'src'))
    .filter(file => {
      const source = readFileSync(file, 'utf8')
      return source.includes('MHY_XTLL.webp') && /background(?:-attachment)?[^;]*\bfixed\b/.test(source)
    })
  assert.deepEqual(fixedBackgrounds, [])

  const mainStyles = readFileSync(path.join(frontendRoot, 'src/assets/style/css/main.css'), 'utf8')
  assert.equal(mainStyles.includes('backdrop-filter: blur(24px)'), false)
})
