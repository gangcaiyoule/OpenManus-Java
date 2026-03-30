import type { SessionInfoPayload, StreamStartResponse, WorkflowRequestPayload } from '../types/api';

export class ApiError extends Error {
  readonly code?: string;
  readonly status?: number;

  constructor(message: string, options?: { code?: string; status?: number }) {
    super(message);
    this.code = options?.code;
    this.status = options?.status;
  }
}

export async function startWorkflow(payload: WorkflowRequestPayload): Promise<StreamStartResponse> {
  const response = await fetch('/api/agent/workflow-stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  });

  const data = (await response.json()) as StreamStartResponse;
  if (response.ok === false || data.success === false) {
    throw new ApiError(data.error || '无法启动工作流', {
      code: data.errorCode,
      status: response.status
    });
  }

  return data;
}

export async function getSessionInfo(sessionId: string): Promise<SessionInfoPayload> {
  const response = await fetch('/api/agent/session/' + encodeURIComponent(sessionId));
  if (response.ok === false) {
    throw new ApiError('会话信息获取失败', { status: response.status });
  }
  return (await response.json()) as SessionInfoPayload;
}
