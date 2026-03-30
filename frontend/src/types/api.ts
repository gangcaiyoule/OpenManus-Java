export type ConnectionStatus = 'idle' | 'connecting' | 'connected' | 'error';

export interface StreamStartResponse {
  success: boolean;
  sessionId?: string;
  topic?: string;
  error?: string;
  errorCode?: string;
}

export interface ExecutionEventPayload {
  sessionId?: string;
  eventId?: string;
  agentName?: string;
  agentType?: string;
  eventType?: string;
  status?: string;
  input?: unknown;
  output?: unknown;
  error?: string;
  startTime?: string;
  endTime?: string;
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
  sessionId?: string;
  messageType?: string;
  userInput?: string;
  result?: string;
  status?: string;
  completedTime?: string;
  executionTime?: number;
}

export interface SessionInfoPayload {
  sessionId: string;
  sandboxVncUrl?: string;
  sandboxContainerId?: string;
  sandboxStatus?: string;
  sandboxCreatedAt?: string;
  sandboxAvailable?: boolean;
}

export interface WorkflowRequestPayload {
  input: string;
}
