import { describe, expect, it } from 'vitest';
import { initialWorkflowState, workflowReducer } from './workflowStore';

describe('workflowReducer', () => {
  it('adds user and assistant messages', () => {
    let state = workflowReducer(initialWorkflowState, {
      type: 'SEND_USER_MESSAGE',
      payload: { content: 'hello', time: '09:00' }
    });
    state = workflowReducer(state, {
      type: 'START_ASSISTANT_MESSAGE',
      payload: { time: '09:00' }
    });

    expect(state.messages.length).toBe(2);
    expect(state.messages[0].role).toBe('user');
    expect(state.messages[1].role).toBe('assistant');
  });

  it('handles result and marks idle', () => {
    let state = workflowReducer(initialWorkflowState, {
      type: 'SEND_USER_MESSAGE',
      payload: { content: 'task', time: '09:00' }
    });
    state = workflowReducer(state, {
      type: 'START_ASSISTANT_MESSAGE',
      payload: { time: '09:00' }
    });
    state = workflowReducer(state, {
      type: 'HANDLE_RESULT',
      payload: { result: 'done' }
    });

    expect(state.messages[1].content).toBe('done');
    expect(state.loading).toBe(false);
    expect(state.connectionStatus).toBe('idle');
  });

  it('collects tool output and error', () => {
    const state = workflowReducer(initialWorkflowState, {
      type: 'HANDLE_EVENT',
      payload: { eventType: 'ERROR', agentName: 'agent', output: 'x', error: 'boom' }
    });

    expect(state.error).toBe('boom');
  });
});
