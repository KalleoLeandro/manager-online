import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { transpileModule, ModuleKind, ScriptTarget } from 'typescript';

const source = readFileSync(new URL('../src/app/utils/match-narration.ts', import.meta.url), 'utf8');
const { outputText } = transpileModule(source, { compilerOptions: { module: ModuleKind.ES2022, target: ScriptTarget.ES2022 } });
const { narrationRoster, narrationParts } = await import('data:text/javascript;base64,' + Buffer.from(outputText).toString('base64'));
const roster = (home, away) => narrationRoster(
  { players: home.map(([id, name]) => ({ id, name })) },
  { players: away.map(([id, name]) => ({ id, name })) },
);
function parse(text, index, playerId = null, relatedPlayerId = null) {
  const parts = narrationParts({ text, playerId, relatedPlayerId }, index);
  assert.equal(parts.map(part => part.text).join(''), text, 'The original narration must remain intact');
  return parts.filter(part => part.side).map(part => [part.text, part.side]);
}

test('all mentions are coloured by their own club, including opponents and repeated names', () => {
  const index = roster([[1, 'João Gomes'], [2, 'Gustavo Gómez']], [[3, 'Murilo']]);
  assert.deepEqual(parse('João Gomes encontra Gustavo Gómez. Murilo desarma João Gomes!', index), [
    ['João Gomes', 'home'], ['Gustavo Gómez', 'home'], ['Murilo', 'away'], ['João Gomes', 'home'],
  ]);
});

test('longest names win and nicknames never match inside other words', () => {
  const index = roster([[1, 'Rony'], [2, 'Rony Silva']], [[3, 'Leo']]);
  assert.deepEqual(parse('Rony Silva e Rony enfrentam Ronyzinho, Leonardo e Leo.', index), [
    ['Rony Silva', 'home'], ['Rony', 'home'], ['Leo', 'away'],
  ]);
});

test('punctuation and regular expression characters in names are literal', () => {
  assert.deepEqual(parse('D. Costa passa para Jean (Jr.). A+B intercepta!', roster([[1, 'D. Costa'], [2, 'Jean (Jr.)']], [[3, 'A+B']])), [
    ['D. Costa', 'home'], ['Jean (Jr.)', 'home'], ['A+B', 'away'],
  ]);
});

test('identical names on opposing clubs use event IDs or remain neutral when ambiguous', () => {
  const index = roster([[1, 'Gabriel']], [[2, 'Gabriel']]);
  assert.deepEqual(parse('Gabriel finaliza.', index, 1), [['Gabriel', 'home']]);
  assert.deepEqual(parse('Gabriel defende.', index, 2), [['Gabriel', 'away']]);
  assert.deepEqual(parse('Gabriel enfrenta Gabriel.', index, 1, 2), []);
});

test('repeated parsing, changed fixtures and empty rosters preserve correct ownership', () => {
  const index = roster([[1, 'João']], []);
  assert.deepEqual(parse('João, João!', index), [['João', 'home'], ['João', 'home']]);
  assert.deepEqual(parse('João, João!', index), [['João', 'home'], ['João', 'home']]);
  assert.deepEqual(parse('João finaliza.', roster([], [[1, 'João']])), [['João', 'away']]);
  assert.deepEqual(parse('Sem nomes aqui.', roster([], [])), []);
});

test('HTML-like content remains literal text and is never converted to markup', () => {
  assert.deepEqual(parse('Passe de <b>João</b>. <img src=x onerror=alert(1)>', roster([[1, 'João']], [])), [['João', 'home']]);
});
