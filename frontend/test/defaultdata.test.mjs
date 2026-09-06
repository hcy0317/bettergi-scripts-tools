import assert from 'node:assert/strict';
import test from 'node:test';
import { countryListDefault, domainsDefault } from '../src/utils/defaultdata.js';

test('默认国家和秘境包含保留的 dev 数据', () => {
    assert.ok(countryListDefault().includes('至冬'));
    for (const [name, type, ordered] of [
        ['荒坠的圣迹', '天赋', true],
        ['妄念的创痕', '武器', true],
        ['山风的荆冕', '圣遗物', false],
        ['逆悬的冰河', '圣遗物', false],
    ]) {
        const matches = domainsDefault.filter(domain => domain.name === name);
        assert.equal(matches.length, 1, name);
        assert.equal(matches[0].type, type, name);
        assert.equal(matches[0].hasOrder, ordered, name);
        assert.ok(matches[0].list.length >= 2, name);
    }
});
