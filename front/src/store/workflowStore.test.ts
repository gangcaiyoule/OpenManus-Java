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

  it('surfaces key execution events in tool output panel', () => {
    let state = workflowReducer(initialWorkflowState, {
      type: 'HANDLE_EVENT',
      payload: { eventType: 'AGENT_START', agentName: 'execution_coordinator', input: 'hello' }
    });
    state = workflowReducer(state, {
      type: 'HANDLE_EVENT',
      payload: { eventType: 'LLM_RESPONSE', agentName: 'llm', output: '{"content":"ok"}' }
    });
    state = workflowReducer(state, {
      type: 'HANDLE_EVENT',
      payload: {
        eventType: 'TOOL_CALL_START',
        agentName: 'search_web',
        input: '{"query":"openai"}',
        metadata: { toolName: 'search_web' }
      }
    });
    state = workflowReducer(state, {
      type: 'HANDLE_EVENT',
      payload: {
        eventType: 'TOOL_CALL_END',
        agentName: 'search_web',
        output: '搜索结果: openai',
        metadata: { toolName: 'search_web' }
      }
    });

    expect(state.toolOutputs.map((item) => item.type)).toEqual([
      'search_web 结果',
      'search_web 参数',
      'AI Message',
      '用户请求'
    ]);
    expect(state.toolOutputs[0].content).toContain('搜索结果');
  });

  it('updates browser state from structured search events', () => {
    let state = workflowReducer(initialWorkflowState, {
      type: 'HANDLE_EVENT',
      payload: {
        eventType: 'SEARCH_STARTED',
        metadata: {
          query: 'openai docs',
          searchPageUrl: 'https://www.google.com/search?q=openai+docs',
          previewMode: 'web'
        }
      }
    });

    state = workflowReducer(state, {
      type: 'HANDLE_EVENT',
      payload: {
        eventType: 'SEARCH_RESULTS_READY',
        metadata: {
          resultItems: [
            {
              title: 'OpenAI Docs',
              url: 'https://platform.openai.com/docs',
              snippet: 'Official docs'
            }
          ]
        }
      }
    });

    expect(state.searchQuery).toBe('openai docs');
    expect(state.browserStatus).toBe('results-ready');
    expect(state.currentUrl).toBe('');
    expect(state.searchResults[0].url).toBe('https://platform.openai.com/docs');
    expect(state.searchTimeline.length).toBeGreaterThan(0);
    expect(state.webTimeline).toHaveLength(0);
  });

  it('marks auto switch vnc when preview is blocked and sandbox exists', () => {
    const state = workflowReducer({
      ...initialWorkflowState,
      sandboxVncUrl: 'https://vnc.local'
    }, {
      type: 'HANDLE_EVENT',
      payload: {
        eventType: 'WEB_PREVIEW_BLOCKED',
        metadata: {
          activeUrl: 'https://blocked.example.com',
          previewMode: 'vnc',
          blockReason: 'fetch-failed',
          detail: '代理抓取网页失败',
          autoSwitchedToVnc: true
        }
      }
    });

    expect(state.browserStatus).toBe('auto-switch-vnc');
    expect(state.autoSwitchedToVnc).toBe(true);
    expect(state.previewBlockedReason).toContain('fetch-failed');
  });

  it('stores vnc url when browser opened event comes from real browser', () => {
    const state = workflowReducer(initialWorkflowState, {
      type: 'HANDLE_EVENT',
      payload: {
        eventType: 'BROWSER_URL_OPENED',
        metadata: {
          activeUrl: 'https://example.com',
          previewMode: 'vnc',
          sandboxVncUrl: 'https://vnc.local'
        }
      }
    });

    expect(state.currentUrl).toBe('https://example.com');
    expect(state.previewMode).toBe('vnc');
    expect(state.sandboxVncUrl).toBe('https://vnc.local');
  });

  it('loads snapshot into fresh workflow state', () => {
    const state = workflowReducer(initialWorkflowState, {
      type: 'LOAD_SNAPSHOT',
      payload: {
        messages: [{ id: 'm1', role: 'user', content: 'hi', time: '10:00', logs: [] }],
        searchResults: [],
        toolOutputs: [{ id: 'o1', type: 'Tool', content: 'ok', time: '10:01' }],
        currentUrl: 'https://example.com',
        sandboxVncUrl: 'https://vnc.local',
        browserStatus: 'rendered',
        previewMode: 'proxy'
      }
    });

    expect(state.messages.length).toBe(1);
    expect(state.messages[0].content).toBe('hi');
    expect(state.toolOutputs.length).toBe(1);
    expect(state.currentUrl).toBe('https://example.com');
    expect(state.sessionId).toBeNull();
    expect(state.browserStatus).toBe('rendered');
  });

  it('restores web snapshot from tool output when structured event is missed', () => {
    const state = workflowReducer(initialWorkflowState, {
      type: 'HANDLE_EVENT',
      payload: {
        eventType: 'TOOL_CALL_END',
        agentName: 'WebFetchTool',
        output: JSON.stringify({
          url: 'https://example.com/article',
          path: '/workspace/.openmanus/web/web-snapshot.txt',
          preview: '<html><body>Agent page</body></html>'
        })
      }
    });

    expect(state.currentUrl).toBe('https://example.com/article');
    expect(state.snapshotPath).toContain('web-snapshot.txt');
    expect(state.snapshotPreview).toContain('Agent page');
    expect(state.browserStatus).toBe('proxy-rendered');
    expect(state.previewMode).toBe('proxy');
    expect(state.webTimeline[0].title).toBe('网页快照已生成');
  });
});
