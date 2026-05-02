export type ConnectionStatus = 'idle' | 'connecting' | 'connected' | 'error';

export interface WebPreviewDiagnosticPayload {
  enabled: boolean;
  targetUrl: string;
  proxyUrl: string;
  state: string;
  reasonCode: string;
  reason: string;
  previewMode: 'web' | 'proxy' | 'vnc' | 'external';
  redirectLocation?: string;
  contentType?: string;
  previewableHtml: boolean;
  fallbackToVnc: boolean;
}

export interface StreamStartResponse {
  success: boolean;
  session_id?: string;
  sessionId?: string;
  topic?: string;
  error?: string;
  errorCode?: string;
}

export interface ExecutionEventPayload {
  session_id?: string;
  sessionId?: string;
  event_id?: string;
  eventId?: string;
  agent_name?: string;
  agentName?: string;
  agent_type?: string;
  agentType?: string;
  event_type?: string;
  eventType?: string;
  status?: string;
  input?: unknown;
  output?: unknown;
  error?: string;
  start_time?: string;
  end_time?: string;
  duration?: number;
  metadata?: Record<string, unknown>;
}

export interface ExecutionLogPayload {
  timestamp?: string;
  level?: string;
  thread?: string;
  logger?: string;
  message?: string;
}

export interface WorkflowResultPayload {
  session_id?: string;
  sessionId?: string;
  messageType?: string;
  user_input?: string;
  result?: string;
  status?: string;
  completed_time?: string;
  execution_time?: number;
}

export interface SessionInfoPayload {
  sessionId: string;
  sandboxVncUrl?: string;
  sandboxStatus?: string;
  sandboxCreatedAt?: string;
  sandboxAvailable?: boolean;
}

export interface WorkflowRequestPayload {
  input: string;
}
