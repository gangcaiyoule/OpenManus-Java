import type {
  ConnectionStatus,
  ExecutionEventPayload,
  ExecutionLogPayload,
  WorkflowResultPayload
} from '../types/api';
import { extractSearchResults, extractWebUrl, type SearchResultItem } from '../utils/parsers';

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  time: string;
  logs: ExecutionLogPayload[];
}

export interface ToolOutput {
  id: string;
  type: string;
  content: string;
  time: string;
}

export type BrowserStatus =
  | 'idle'
  | 'searching'
  | 'opening-search-page'
  | 'results-ready'
  | 'opening-target-page'
  | 'proxy-rendered'
  | 'proxy-blocked'
  | 'auto-switch-vnc'
  | 'loading'
  | 'proxying'
  | 'rendered'
  | 'blocked'
  | 'fallback-to-vnc'
  | 'open-external';

export interface TimelineEntry {
  id: string;
  type: string;
  title: string;
  description: string;
  time: string;
  url?: string;
  detail?: string;
}

export interface WorkflowState {
  connectionStatus: ConnectionStatus;
  loading: boolean;
  error: string | null;
  sessionId: string | null;
  topic: string | null;
  messages: ChatMessage[];
  searchResults: SearchResultItem[];
  toolOutputs: ToolOutput[];
  currentUrl: string;
  sandboxVncUrl: string | null;
  searchQuery: string;
  searchPageUrl: string;
  browserStatus: BrowserStatus;
  previewMode: 'web' | 'proxy' | 'vnc' | 'external';
  previewBlockedReason: string | null;
  snapshotPath: string | null;
  snapshotPreview: string | null;
  autoSwitchedToVnc: boolean;
  searchTimeline: TimelineEntry[];
  webTimeline: TimelineEntry[];
}

export interface WorkflowSnapshot {
  messages: ChatMessage[];
  searchResults: SearchResultItem[];
  toolOutputs: ToolOutput[];
  currentUrl: string;
  sandboxVncUrl: string | null;
  searchQuery?: string;
  searchPageUrl?: string;
  browserStatus?: BrowserStatus;
  previewMode?: 'web' | 'proxy' | 'vnc' | 'external';
  previewBlockedReason?: string | null;
  snapshotPath?: string | null;
  snapshotPreview?: string | null;
  autoSwitchedToVnc?: boolean;
  searchTimeline?: TimelineEntry[];
  webTimeline?: TimelineEntry[];
}

export type WorkflowAction =
  | { type: 'SEND_USER_MESSAGE'; payload: { content: string; time: string } }
  | { type: 'START_ASSISTANT_MESSAGE'; payload: { time: string } }
  | { type: 'APPEND_LOG'; payload: ExecutionLogPayload }
  | { type: 'HANDLE_EVENT'; payload: ExecutionEventPayload }
  | { type: 'HANDLE_RESULT'; payload: WorkflowResultPayload }
  | { type: 'SET_STATUS'; payload: ConnectionStatus }
  | { type: 'SET_ERROR'; payload: string | null }
  | { type: 'SET_LOADING'; payload: boolean }
  | { type: 'SET_STREAM'; payload: { sessionId: string; topic: string } }
  | { type: 'SET_VNC_URL'; payload: string | null }
  | { type: 'SET_CURRENT_URL'; payload: string }
  | { type: 'LOAD_SNAPSHOT'; payload: WorkflowSnapshot }
  | { type: 'RESET' };

export const initialWorkflowState: WorkflowState = {
  connectionStatus: 'idle',
  loading: false,
  error: null,
  sessionId: null,
  topic: null,
  messages: [],
  searchResults: [],
  toolOutputs: [],
  currentUrl: '',
  sandboxVncUrl: null,
  searchQuery: '',
  searchPageUrl: '',
  browserStatus: 'idle',
  previewMode: 'web',
  previewBlockedReason: null,
  snapshotPath: null,
  snapshotPreview: null,
  autoSwitchedToVnc: false,
  searchTimeline: [],
  webTimeline: []
};

export function workflowReducer(state: WorkflowState, action: WorkflowAction): WorkflowState {
  switch (action.type) {
    case 'SEND_USER_MESSAGE':
      return {
        ...state,
        messages: state.messages.concat({
          id: randomId(),
          role: 'user',
          content: action.payload.content,
          time: action.payload.time,
          logs: []
        }),
        loading: true,
        error: null
      };
    case 'START_ASSISTANT_MESSAGE':
      return {
        ...state,
        messages: state.messages.concat({
          id: randomId(),
          role: 'assistant',
          content: '',
          time: action.payload.time,
          logs: []
        })
      };
    case 'APPEND_LOG': {
      const lastAssistantIndex = findLastAssistantIndex(state.messages);
      if (lastAssistantIndex < 0) {
        // 没有助手消息时，创建一个新的
        return {
          ...state,
          messages: [...state.messages, {
            id: randomId(),
            role: 'assistant',
            content: '',
            time: action.payload.timestamp || formatTime(),
            logs: [action.payload]
          }],
          currentUrl: extractWebUrl(action.payload.message || '') || state.currentUrl
        };
      }

      const messages = state.messages.map((message, index) =>
        index === lastAssistantIndex
          ? { ...message, logs: [...message.logs, action.payload] }
          : message
      );

      const foundUrl = extractWebUrl(action.payload.message || '');
      return {
        ...state,
        messages,
        currentUrl: foundUrl || state.currentUrl
      };
    }
    case 'HANDLE_EVENT': {
      const eventType = getEventType(action.payload);
      const metadata = action.payload.metadata || {};
      const outputAsString = normalizePayloadOutput(action.payload.output);
      const nextOutputs = state.toolOutputs.slice();
      const fallbackSearchResults = extractSearchResults(outputAsString);
      const fallbackWebSnapshot = parseWebSnapshotOutput(outputAsString);
      const patch: Partial<WorkflowState> = {};

      if (eventType === 'TOOL_CALL_END' && outputAsString.length > 0) {
        nextOutputs.unshift({
          id: randomId(),
          type: action.payload.agent_name || action.payload.agentName || '工具',
          content: outputAsString,
          time: formatTime()
        });
      }

      applyStructuredBrowserEvent(patch, state, eventType, metadata);

      if (patch.searchResults === undefined && fallbackSearchResults.length > 0) {
        patch.searchResults = fallbackSearchResults;
      }
      if ((patch.currentUrl === undefined || patch.currentUrl.length === 0) && state.currentUrl.length === 0) {
        const fallbackUrl = extractWebUrl(outputAsString);
        if (fallbackUrl) {
          patch.currentUrl = fallbackUrl;
        }
      }
      if (fallbackWebSnapshot) {
        patch.currentUrl = fallbackWebSnapshot.url || patch.currentUrl || state.currentUrl;
        patch.snapshotPath = fallbackWebSnapshot.path || state.snapshotPath;
        patch.snapshotPreview = fallbackWebSnapshot.preview || state.snapshotPreview;
        patch.browserStatus = 'proxy-rendered';
        patch.previewMode = 'proxy';
        patch.previewBlockedReason = null;
        patch.webTimeline = prependTimeline(state.webTimeline, {
          type: 'WEB_FETCH_SNAPSHOT_READY',
          title: '网页快照已生成',
          description: fallbackWebSnapshot.path || '已从工具输出恢复网页快照',
          url: fallbackWebSnapshot.url || undefined
        });
      }

      return {
        ...state,
        toolOutputs: nextOutputs,
        error: eventType === 'ERROR' ? action.payload.error || '执行异常' : state.error,
        ...patch
      };
    }
    case 'HANDLE_RESULT': {
      const messages = state.messages.map((message) => ({ ...message, logs: message.logs.slice() }));
      const lastAssistantIndex = findLastAssistantIndex(messages);
      if (lastAssistantIndex >= 0) {
        messages[lastAssistantIndex].content = action.payload.result || '已完成';
      }
      return {
        ...state,
        messages,
        loading: false,
        connectionStatus: 'idle'
      };
    }
    case 'SET_STATUS':
      return { ...state, connectionStatus: action.payload };
    case 'SET_ERROR':
      return { ...state, error: action.payload };
    case 'SET_LOADING':
      return { ...state, loading: action.payload };
    case 'SET_STREAM':
      return { ...state, sessionId: action.payload.sessionId, topic: action.payload.topic };
    case 'SET_VNC_URL':
      return { ...state, sandboxVncUrl: action.payload };
    case 'SET_CURRENT_URL':
      return { ...state, currentUrl: action.payload };
    case 'LOAD_SNAPSHOT':
      return {
        ...initialWorkflowState,
        messages: action.payload.messages,
        searchResults: action.payload.searchResults,
        toolOutputs: action.payload.toolOutputs,
        currentUrl: action.payload.currentUrl,
        sandboxVncUrl: action.payload.sandboxVncUrl,
        searchQuery: action.payload.searchQuery || '',
        searchPageUrl: action.payload.searchPageUrl || '',
        browserStatus: action.payload.browserStatus || 'idle',
        previewMode: action.payload.previewMode || 'web',
        previewBlockedReason: action.payload.previewBlockedReason || null,
        snapshotPath: action.payload.snapshotPath || null,
        snapshotPreview: action.payload.snapshotPreview || null,
        autoSwitchedToVnc: action.payload.autoSwitchedToVnc || false,
        searchTimeline: action.payload.searchTimeline || [],
        webTimeline: action.payload.webTimeline || []
      };
    case 'RESET':
      return { ...initialWorkflowState };
    default:
      return state;
  }
}

function findLastAssistantIndex(messages: ChatMessage[]): number {
  for (let i = messages.length - 1; i >= 0; i -= 1) {
    if (messages[i].role === 'assistant') {
      return i;
    }
  }
  return -1;
}

function normalizePayloadOutput(output: unknown): string {
  if (typeof output === 'string') {
    return output;
  }
  if (output === null || output === undefined) {
    return '';
  }
  try {
    return JSON.stringify(output, null, 2);
  } catch {
    return String(output);
  }
}

function getEventType(event: ExecutionEventPayload): string {
  return event.event_type || event.eventType || '';
}

function applyStructuredBrowserEvent(patch: Partial<WorkflowState>,
                                     state: WorkflowState,
                                     eventType: string,
                                     metadata: Record<string, unknown>): void {
  const activeUrl = asString(metadata.activeUrl);
  const searchPageUrl = asString(metadata.searchPageUrl);
  const previewMode = normalizePreviewMode(asString(metadata.previewMode));
  const blockReason = firstNonEmpty(asString(metadata.blockReason), asString(metadata.detail));
  const snapshotPath = asString(metadata.snapshotPath);
  const snapshotPreview = asString(metadata.snapshotPreview);
  const sandboxVncUrl = asString(metadata.sandboxVncUrl);
  const autoSwitchedToVnc = Boolean(metadata.autoSwitchedToVnc);
  const query = asString(metadata.query);
  const resultItems = parseSearchResultItems(metadata.resultItems);

  switch (eventType) {
    case 'SEARCH_STARTED':
      patch.searchQuery = query;
      patch.searchPageUrl = searchPageUrl;
      patch.browserStatus = 'searching';
      patch.previewMode = previewMode;
      patch.previewBlockedReason = null;
      patch.autoSwitchedToVnc = false;
      patch.searchTimeline = prependTimeline(state.searchTimeline, {
        type: eventType,
        title: '搜索已发起',
        description: query || '正在准备搜索页面',
        url: searchPageUrl || undefined
      });
      return;
    case 'SEARCH_RESULTS_READY':
      patch.searchResults = resultItems.length > 0 ? resultItems : state.searchResults;
      patch.browserStatus = 'results-ready';
      patch.searchTimeline = prependTimeline(state.searchTimeline, {
        type: eventType,
        title: '搜索结果已返回',
        description: resultItems.length > 0 ? `共 ${resultItems.length} 条结构化结果` : '结果已返回，可打开搜索页继续查看',
        url: searchPageUrl || state.searchPageUrl || undefined,
        detail: blockReason || undefined
      });
      return;
    case 'BROWSER_URL_OPENED':
      patch.currentUrl = activeUrl || state.currentUrl;
      patch.sandboxVncUrl = sandboxVncUrl || state.sandboxVncUrl;
      patch.browserStatus = 'opening-target-page';
      patch.previewMode = previewMode;
      patch.previewBlockedReason = null;
      patch.autoSwitchedToVnc = false;
      patch.webTimeline = prependTimeline(state.webTimeline, {
        type: eventType,
        title: '网页已打开',
        description: activeUrl || '浏览器已切换到目标页',
        url: activeUrl || undefined
      });
      return;
    case 'WEB_FETCH_STARTED':
      patch.currentUrl = activeUrl || state.currentUrl;
      patch.browserStatus = previewMode === 'proxy' ? 'proxying' : 'loading';
      patch.previewMode = previewMode;
      patch.previewBlockedReason = null;
      patch.webTimeline = prependTimeline(state.webTimeline, {
        type: eventType,
        title: '网页抓取中',
        description: activeUrl || '正在抓取网页内容',
        url: activeUrl || undefined
      });
      return;
    case 'WEB_FETCH_SNAPSHOT_READY':
      patch.currentUrl = activeUrl || state.currentUrl;
      patch.snapshotPath = snapshotPath || state.snapshotPath;
      patch.snapshotPreview = snapshotPreview || state.snapshotPreview;
      patch.browserStatus = 'proxy-rendered';
      patch.previewMode = previewMode;
      patch.webTimeline = prependTimeline(state.webTimeline, {
        type: eventType,
        title: '网页快照已生成',
        description: snapshotPath || '快照已生成',
        url: activeUrl || undefined
      });
      return;
    case 'WEB_PREVIEW_BLOCKED':
      patch.currentUrl = activeUrl || state.currentUrl;
      patch.browserStatus = state.sandboxVncUrl ? 'auto-switch-vnc' : 'proxy-blocked';
      patch.previewMode = previewMode;
      patch.previewBlockedReason = blockReason || '正在切换真实浏览器';
      patch.autoSwitchedToVnc = autoSwitchedToVnc || state.sandboxVncUrl !== null;
      patch.webTimeline = prependTimeline(state.webTimeline, {
        type: eventType,
        title: '切换真实浏览器',
        description: blockReason || '代理预览不可作为最终展示，改用真实浏览器',
        url: activeUrl || undefined
      });
      return;
    case 'VNC_READY':
      patch.sandboxVncUrl = asString(metadata.sandboxVncUrl) || state.sandboxVncUrl;
      patch.webTimeline = prependTimeline(state.webTimeline, {
        type: eventType,
        title: 'VNC 已就绪',
        description: '可切换到 VNC 继续查看网页或执行过程'
      });
      return;
    default:
  }
}

function prependTimeline(items: TimelineEntry[], item: Omit<TimelineEntry, 'id' | 'time'>): TimelineEntry[] {
  return [{
    id: randomId(),
    time: formatTime(),
    ...item
  }, ...items].slice(0, 30);
}

function parseSearchResultItems(value: unknown): SearchResultItem[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value
    .map((item) => {
      if (item === null || typeof item !== 'object') {
        return null;
      }
      const record = item as Record<string, unknown>;
      const title = asString(record.title);
      const url = asString(record.url);
      const snippet = asString(record.snippet);
      if (title.length === 0 || url.length === 0) {
        return null;
      }
      return { title, url, snippet };
    })
    .filter((item): item is SearchResultItem => item !== null);
}

function parseWebSnapshotOutput(output: string): { url: string; path: string; preview: string } | null {
  if (output.length === 0 || output.trim().startsWith('{') === false) {
    return null;
  }
  try {
    const parsed = JSON.parse(output) as Record<string, unknown>;
    const url = asString(parsed.url);
    const path = asString(parsed.path);
    const preview = asString(parsed.preview);
    if (url.length === 0 || preview.length === 0) {
      return null;
    }
    return { url, path, preview };
  } catch {
    return null;
  }
}

function asString(value: unknown): string {
  return typeof value === 'string' ? value : '';
}

function firstNonEmpty(...values: string[]): string {
  return values.find((value) => value.length > 0) || '';
}

function normalizePreviewMode(value: string): 'web' | 'proxy' | 'vnc' | 'external' {
  if (value === 'proxy' || value === 'vnc' || value === 'external') {
    return value;
  }
  return 'web';
}

function randomId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return 'id-' + Date.now() + '-' + Math.random();
}

function formatTime(): string {
  return new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
}
