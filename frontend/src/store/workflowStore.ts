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
  sandboxVncUrl: null
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
      const messages = state.messages.map((message) => ({ ...message, logs: message.logs.slice() }));
      const lastAssistantIndex = findLastAssistantIndex(messages);
      if (lastAssistantIndex < 0) {
        return state;
      }
      messages[lastAssistantIndex].logs.push(action.payload);

      const foundUrl = extractWebUrl(action.payload.message || '');
      return {
        ...state,
        messages,
        currentUrl: foundUrl || state.currentUrl
      };
    }
    case 'HANDLE_EVENT': {
      const outputAsString = normalizePayloadOutput(action.payload.output);
      const parsedSearch = extractSearchResults(outputAsString);
      const nextOutputs = state.toolOutputs.slice();

      if (action.payload.eventType === 'TOOL_CALL_END' && outputAsString.length > 0) {
        nextOutputs.unshift({
          id: randomId(),
          type: action.payload.agentName || '工具',
          content: outputAsString,
          time: formatTime()
        });
      }

      return {
        ...state,
        toolOutputs: nextOutputs,
        searchResults: parsedSearch.length > 0 ? parsedSearch : state.searchResults,
        error: action.payload.eventType === 'ERROR' ? action.payload.error || '执行异常' : state.error
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

function randomId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return 'id-' + Date.now() + '-' + Math.random();
}

function formatTime(): string {
  return new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
}
