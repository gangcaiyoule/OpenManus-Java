import { describe, expect, it, vi } from 'vitest';
import { WorkflowSocketClient } from './workflowSocketClient';

type Callback = (message: { body: string }) => void;

class FakeStompClient {
  onConnect?: () => void;
  onStompError?: (frame: { headers?: Record<string, string>; body?: string }) => void;
  onWebSocketError?: (_event: Event) => void;
  callbacks: Record<string, Callback> = {};
  unsubscribedCount = 0;

  activate(): void {
    if (this.onConnect) {
      this.onConnect();
    }
  }

  async deactivate(): Promise<void> {
    return Promise.resolve();
  }

  subscribe(destination: string, callback: Callback): { unsubscribe: () => void } {
    this.callbacks[destination] = callback;
    return {
      unsubscribe: () => {
        this.unsubscribedCount += 1;
      }
    };
  }
}

describe('WorkflowSocketClient', () => {
  it('subscribes and routes parsed payloads', async () => {
    const fake = new FakeStompClient();
    const client = new WorkflowSocketClient(() => fake);

    await client.connect();

    const onLog = vi.fn();
    const onEvent = vi.fn();
    const onResult = vi.fn();
    const onSocketError = vi.fn();

    client.subscribe('/topic/executions/s-1', {
      onLog,
      onEvent,
      onResult,
      onSocketError
    });

    fake.callbacks['/topic/executions/s-1/logs']({ body: JSON.stringify({ message: 'log' }) });
    fake.callbacks['/topic/executions/s-1']({ body: JSON.stringify({ eventType: 'TOOL_CALL_END' }) });
    fake.callbacks['/topic/executions/s-1/result']({ body: JSON.stringify({ result: 'done' }) });

    expect(onLog).toHaveBeenCalledTimes(1);
    expect(onEvent).toHaveBeenCalledTimes(1);
    expect(onResult).toHaveBeenCalledTimes(1);
    expect(onSocketError).toHaveBeenCalledTimes(0);

    await client.disconnect();
    expect(fake.unsubscribedCount).toBe(3);
  });

  it('reports parse error via onSocketError', async () => {
    const fake = new FakeStompClient();
    const client = new WorkflowSocketClient(() => fake);
    await client.connect();

    const onSocketError = vi.fn();
    client.subscribe('/topic/executions/s-2', {
      onLog: vi.fn(),
      onEvent: vi.fn(),
      onResult: vi.fn(),
      onSocketError
    });

    fake.callbacks['/topic/executions/s-2/result']({ body: '{bad json}' });
    expect(onSocketError).toHaveBeenCalledTimes(1);
  });
});
