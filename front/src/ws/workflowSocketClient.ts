import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import type {
  ExecutionEventPayload,
  ExecutionLogPayload,
  WorkflowResultPayload
} from '../types/api';

export interface WorkflowSubscriptionHandlers {
  onLog: (payload: ExecutionLogPayload) => void;
  onEvent: (payload: ExecutionEventPayload) => void;
  onResult: (payload: WorkflowResultPayload) => void;
  onSocketError: (message: string) => void;
}

interface Subscription {
  unsubscribe: () => void;
}

interface StompLike {
  activate: () => void;
  deactivate: () => Promise<void>;
  subscribe: (destination: string, callback: (message: { body: string }) => void) => Subscription;
  onConnect?: (_frame?: unknown) => void;
  onStompError?: (frame: { headers?: Record<string, string>; body?: string }) => void;
  onWebSocketError?: (_event: Event) => void;
}

export class WorkflowSocketClient {
  private client: StompLike | null = null;
  private subscriptions: Subscription[] = [];

  constructor(private readonly factory: () => StompLike = defaultClientFactory) {}

  connect(): Promise<void> {
    if (this.client !== null) {
      return Promise.resolve();
    }

    this.client = this.factory();
    const currentClient = this.client;

    return new Promise((resolve, reject) => {
      currentClient.onConnect = () => {
        resolve();
      };
      currentClient.onStompError = (frame) => {
        reject(new Error(frame.headers?.message || frame.body || 'STOMP 连接失败'));
      };
      currentClient.onWebSocketError = () => {
        reject(new Error('WebSocket 连接失败'));
      };
      currentClient.activate();
    });
  }

  subscribe(topic: string, handlers: WorkflowSubscriptionHandlers): void {
    if (this.client === null) {
      throw new Error('WebSocket 未连接');
    }

    this.subscriptions.push(
      this.client.subscribe(topic + '/logs', (message) => {
        try {
          handlers.onLog(JSON.parse(message.body) as ExecutionLogPayload);
        } catch {
          handlers.onSocketError('日志解析失败');
        }
      })
    );

    this.subscriptions.push(
      this.client.subscribe(topic, (message) => {
        try {
          handlers.onEvent(JSON.parse(message.body) as ExecutionEventPayload);
        } catch {
          handlers.onSocketError('事件解析失败');
        }
      })
    );

    this.subscriptions.push(
      this.client.subscribe(topic + '/result', (message) => {
        try {
          handlers.onResult(JSON.parse(message.body) as WorkflowResultPayload);
        } catch {
          handlers.onSocketError('结果解析失败');
        }
      })
    );
  }

  async disconnect(): Promise<void> {
    this.subscriptions.forEach((subscription) => subscription.unsubscribe());
    this.subscriptions = [];

    if (this.client !== null) {
      await this.client.deactivate();
      this.client = null;
    }
  }
}

function defaultClientFactory(): StompLike {
  const client = new Client({
    webSocketFactory: () => new SockJS('/ws'),
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    debug: () => {
      // silent
    }
  });
  return client as unknown as StompLike;
}
