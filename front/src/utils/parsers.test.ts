import { describe, expect, it } from 'vitest';
import { extractSearchResults, extractWebUrl } from './parsers';

describe('parsers', () => {
  it('extracts search result rows', () => {
    const content = [
      '🔍 搜索结果:',
      '1. **A title**',
      '🔗 https://example.com',
      '📝 snippet'
    ].join('\n');

    const parsed = extractSearchResults(content);
    expect(parsed.length).toBe(1);
    expect(parsed[0].url).toBe('https://example.com');
  });

  it('extracts url from browser logs', () => {
    const url = extractWebUrl('正在访问: https://openai.com/docs');
    expect(url).toBe('https://openai.com/docs');
  });
});
