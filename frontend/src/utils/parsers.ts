export interface SearchResultItem {
  title: string;
  url: string;
  snippet: string;
}

export function extractSearchResults(content: string): SearchResultItem[] {
  if (content.length === 0 || content.includes('🔍 搜索结果:') === false) {
    return [];
  }

  const lines = content.split('\n');
  const parsed: SearchResultItem[] = [];
  let current: SearchResultItem | null = null;

  for (const rawLine of lines) {
    const line = rawLine.trim();
    if (line.length === 0) {
      continue;
    }

    const titleMatch = line.match(/^\d+\.\s*\*\*(.+)\*\*$/);
    if (titleMatch) {
      if (current !== null && current.title.length > 0 && current.url.length > 0) {
        parsed.push(current);
      }
      current = { title: titleMatch[1], url: '', snippet: '' };
      continue;
    }

    if (current !== null && line.startsWith('🔗')) {
      current.url = line.replace(/^🔗\s*/, '').trim();
      continue;
    }

    if (current !== null && line.startsWith('📝')) {
      current.snippet = line.replace(/^📝\s*/, '').trim();
    }
  }

  if (current !== null && current.title.length > 0 && current.url.length > 0) {
    parsed.push(current);
  }

  return parsed;
}

export function extractWebUrl(content: string): string | null {
  const match = content.match(/(?:正在访问|搜索页面)[:：\s]*(https?:\/\/[^\s\)]+)/i);
  if (match === null || match[1] === undefined) {
    return null;
  }
  return match[1];
}
