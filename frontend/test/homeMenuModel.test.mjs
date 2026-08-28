import test from 'node:test'
import assert from 'node:assert/strict'

import {
  resolveMenuEnvironment,
  routeGroupOrder,
  visibleRouteGroups,
} from '../src/features/home/menuModel.js'

const groups = new Map([
  ['系统', {order: 1, env: ['prod', 'dev']}],
  ['JS扩展功能', {order: 3, env: ['prod', 'dev']}],
  ['演示', {order: 4, env: ['dev']}],
])

test('home menu falls back to the current Vite runtime environment', () => {
  assert.equal(resolveMenuEnvironment('', true), 'dev')
  assert.equal(resolveMenuEnvironment(undefined, false), 'prod')
  assert.equal(resolveMenuEnvironment('prod', true), 'prod')
})

test('home menu retains known groups and safely handles unknown groups', () => {
  assert.deepEqual(visibleRouteGroups(['系统', 'JS扩展功能', '演示'], groups, 'prod'), [
    '系统', 'JS扩展功能',
  ])
  assert.equal(routeGroupOrder(groups, '系统'), 1)
  assert.equal(routeGroupOrder(groups, '未分组'), 999)
})
