export interface SearchResultItem {
  title: string;
  url: string;
  snippet: string;
}

export function extractSearchResults(content: string): SearchResultItem[] {
  if (content.length === 0) {
    return [];
  }

  // 支持多种格式：emoji + 纯文本标题 + Markdown 格式
  // 格式1: "🔍 搜索结果:" 或 "Search Results:"
  // 格式2: "1. **Title**" (Markdown)
  // 格式3: "1. Title" 或 "1 Title" (纯文本)
  // 格式4: "- [Title](url)" (Markdown link)
  // 格式5: "Title\nURL: ..." (简单格式)

  const searchMarkers = ['🔍 搜索结果:', '🔍 Search Results:', '搜索结果', 'Search Results', '搜索:'];
  const hasSearchMarker = searchMarkers.some(marker => content.includes(marker));

  // 如果没有搜索标记，尝试从内容中提取搜索结果（自助方式）
  if (!hasSearchMarker) {
    return extractSearchResultsFallback(content);
  }

  const lines = content.split('\n');
  const parsed: SearchResultItem[] = [];
  let current: SearchResultItem | null = null;

  for (const rawLine of lines) {
    const line = rawLine.trim();
    if (line.length === 0) {
      continue;
    }

    // 跳过标题行
    if (searchMarkers.some(m => line.includes(m))) {
      continue;
    }

    // 格式1: Markdown 标题 "1. **Title**" 或 "1. Title"
    const mdTitleMatch = line.match(/^\d+\.\s*\*(.+)\*\*$/);
    // 格式2: 带括号的 Markdown 链接 "[Title](url)"
    const mdLinkMatch = line.match(/^\[([^\]]+)\]\(([^)]+)\)/);
    // 格式3: 纯数字开头 "1. Title"
    const plainTitleMatch = line.match(/^\d+\.\s+(.+)$/);
    // 格式4: "Title - URL" 或 "Title | URL"
    const titleUrlMatch = line.match(/^(.+?)\s*[-|]\s*(https?:\/\/\S+)$/);

    if (mdTitleMatch || plainTitleMatch) {
      const title = mdTitleMatch ? mdTitleMatch[1] : (plainTitleMatch ? plainTitleMatch[1] : '');
      if (current !== null && current.title.length > 0 && current.url.length > 0) {
        parsed.push(current);
      }
      current = { title: title.replace(/[*#]/g, '').trim(), url: '', snippet: '' };
      continue;
    }

    if (mdLinkMatch && current !== null) {
      current.title = mdLinkMatch[1].replace(/[*#]/g, '').trim();
      current.url = mdLinkMatch[2];
      continue;
    }

    if (titleUrlMatch && current !== null) {
      if (!current.url) {
        current.title = titleUrlMatch[1].replace(/[*#]/g, '').trim();
        current.url = titleUrlMatch[2];
      }
      continue;
    }

    // URL 行 (支持多种 emoji 或无 emoji)
    if (line.match(/^🔗\s*(.+)/) || line.match(/^Link:\s*(.+)/i) || line.startsWith('http')) {
      const url = line.replace(/^🔗\s*/, '').replace(/^Link:\s*/i, '').trim();
      if (current !== null) {
        current.url = url;
      }
      continue;
    }

    // Snippet 行
    if (line.startsWith('📝') || line.startsWith('Snippet:') || line.startsWith('描述:')) {
      const snippet = line.replace(/^📝\s*/, '').replace(/^Snippet:\s*/i, '').replace(/^描述:\s*/, '').trim();
      if (current !== null) {
        current.snippet = snippet;
      }
    }
  }

  if (current !== null && current.title.length > 0 && current.url.length > 0) {
    parsed.push(current);
  }

  return parsed;
}

function extractSearchResultsFallback(content: string): SearchResultItem[] {
  // 自助方式：从内容中提取可能的搜索结果
  const parsed: SearchResultItem[] = [];

  // 匹配 Markdown 链接格式: [Title](url)
  const mdLinks = content.matchAll(/\[([^\]]+)\]\((https?:\/\/[^\)]+)\)/g);
  for (const match of mdLinks) {
    parsed.push({
      title: match[1].replace(/[*#]/g, '').trim(),
      url: match[2],
      snippet: ''
    });
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
