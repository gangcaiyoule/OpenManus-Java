import { FormEvent, useEffect, useMemo, useReducer, useRef, useState } from 'react';
import { marked } from 'marked';
import {
  ApiError,
  getSessionEvents,
  getSessionInfo,
  inspectProxyPreview,
  startSessionSandbox,
  startWorkflow
} from './api/agentApi';
import {
  initialWorkflowState,
  workflowReducer,
  type BrowserStatus,
  type ChatMessage,
  type TimelineEntry,
  type ToolOutput
} from './store/workflowStore';
import { WorkflowSocketClient } from './ws/workflowSocketClient';

marked.setOptions({ breaks: true, gfm: true });

export default function App(): JSX.Element {
  const [state, dispatch] = useReducer(workflowReducer, initialWorkflowState);
  const [input, setInput] = useState('');
  const [browserMode, setBrowserMode] = useState<'web' | 'snapshot' | 'vnc'>('web');
  const [useProxy, setUseProxy] = useState(true);
  const [showToolPanel, setShowToolPanel] = useState(true);
  const [activeToolTab, setActiveToolTab] = useState<'search' | 'status' | 'output'>('search');
  const [iframeBlockedReason, setIframeBlockedReason] = useState<string | null>(null);
  const socketRef = useRef<WorkflowSocketClient | null>(null);
  const currentExecutionRef = useRef<{ sessionId: string | null; completed: boolean }>({
    sessionId: null,
    completed: false
  });

  const statusText = useMemo(() => {
    switch (state.connectionStatus) {
      case 'connecting':
        return 'Connecting';
      case 'connected':
        return 'Connected';
      case 'error':
        return 'Connection Error';
      default:
        return 'Idle';
    }
  }, [state.connectionStatus]);

  useEffect(() => {
    return () => {
      void disconnectSocket();
    };
  }, []);

  useEffect(() => {
    if ((state.browserStatus === 'auto-switch-vnc' || state.previewMode === 'vnc') && state.sandboxVncUrl) {
      setBrowserMode('vnc');
    }
  }, [state.browserStatus, state.previewMode, state.sandboxVncUrl]);

  useEffect(() => {
    if (state.snapshotPreview && (state.browserStatus === 'proxy-rendered' || state.previewBlockedReason)) {
      setBrowserMode('snapshot');
    }
  }, [state.browserStatus, state.previewBlockedReason, state.snapshotPreview]);

  useEffect(() => {
    if (state.currentUrl.length === 0 || useProxy === false) {
      return;
    }
    if (state.previewMode === 'vnc' && state.sandboxVncUrl) {
      return;
    }
    let cancelled = false;
    void inspectCurrentUrl(() => cancelled);
    return () => {
      cancelled = true;
    };
  }, [state.currentUrl, useProxy, state.sandboxVncUrl]);

  async function sendMessage(event?: FormEvent): Promise<void> {
    if (event) {
      event.preventDefault();
    }
    const content = input.trim();
    if (content.length === 0 || state.loading) {
      return;
    }

    dispatch({ type: 'SEND_USER_MESSAGE', payload: { content, time: nowTime() } });
    dispatch({ type: 'START_ASSISTANT_MESSAGE', payload: { time: nowTime() } });
    setInput('');

    try {
      await disconnectSocket();
      dispatch({ type: 'SET_STATUS', payload: 'connecting' });

      // 1. 先建立 WebSocket 连接
      socketRef.current = new WorkflowSocketClient();
      await socketRef.current.connect();

      // 2. 发送 API 请求获取 sessionId 和 topic
      const startData = await startWorkflow({ input: content });
      const sessionId = startData.session_id || startData.sessionId || '';
      const topic = startData.topic || '';
      if (sessionId.length === 0 || topic.length === 0) {
        throw new ApiError('Backend did not return a subscribable topic');
      }

      currentExecutionRef.current = { sessionId, completed: false };
      dispatch({ type: 'SET_STREAM', payload: { sessionId, topic } });
      dispatch({ type: 'SET_STATUS', payload: 'connected' });
      void bootstrapSandbox(sessionId);

      // 3. 订阅之后再发送 API 请求，确保不会错过任何消息
      socketRef.current.subscribe(topic, {
        onLog: (payload) => dispatch({ type: 'APPEND_LOG', payload }),
        onEvent: (payload) => {
          dispatch({ type: 'HANDLE_EVENT', payload });
          if (getPayloadEventType(payload) === 'ERROR') {
            void completeExecutionWithError(payload.error || '执行异常');
          }
        },
        onResult: async (payload) => {
          await completeExecutionWithResult(sessionId, payload.result || '已完成');
        },
        onSocketError: (message) => {
          dispatch({ type: 'SET_ERROR', payload: message });
          dispatch({ type: 'SET_LOADING', payload: false });
          dispatch({ type: 'SET_STATUS', payload: 'error' });
        }
      });

      void monitorExecutionCompletion(sessionId);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Send failed';
      dispatch({ type: 'SET_ERROR', payload: message });
      dispatch({ type: 'SET_LOADING', payload: false });
      dispatch({ type: 'SET_STATUS', payload: 'error' });
      await disconnectSocket();
    }
  }

  async function pollSandbox(sessionId: string): Promise<void> {
    for (let i = 0; i < 20; i += 1) {
      try {
        const sessionData = await getSessionInfo(sessionId);
        if (sessionData.sandboxAvailable && sessionData.sandboxVncUrl) {
          dispatch({ type: 'SET_VNC_URL', payload: sessionData.sandboxVncUrl });
          return;
        }
      } catch {
        // Ignore intermittent polling failure.
      }
      await sleep(3000);
    }
  }

  async function bootstrapSandbox(sessionId: string): Promise<void> {
    try {
      const sessionData = await startSessionSandbox(sessionId);
      if (sessionData.sandboxAvailable && sessionData.sandboxVncUrl) {
        dispatch({ type: 'SET_VNC_URL', payload: sessionData.sandboxVncUrl });
        return;
      }
    } catch {
      // Ignore bootstrap failure and fall back to polling.
    }
    await pollSandbox(sessionId);
  }

  async function inspectCurrentUrl(isCancelled: () => boolean): Promise<void> {
    try {
      const diagnostic = await inspectProxyPreview(state.currentUrl);
      if (isCancelled()) {
        return;
      }
      if (diagnostic.previewableHtml) {
        setIframeBlockedReason(null);
        return;
      }
      dispatch({
        type: 'HANDLE_EVENT',
        payload: {
          eventType: 'WEB_PREVIEW_BLOCKED',
          metadata: {
            activeUrl: diagnostic.targetUrl,
            previewMode: diagnostic.previewMode,
            blockReason: diagnostic.reasonCode || diagnostic.state,
            detail: diagnostic.reason,
            autoSwitchedToVnc: diagnostic.fallbackToVnc
          }
        }
      });
      setIframeBlockedReason(diagnostic.reason || diagnostic.reasonCode || '正在切换真实浏览器');
      if (state.snapshotPreview) {
        setBrowserMode('snapshot');
      } else if (diagnostic.fallbackToVnc && state.sandboxVncUrl) {
        setBrowserMode('vnc');
      }
    } catch (error) {
      if (isCancelled()) {
        return;
      }
      setIframeBlockedReason(error instanceof Error ? error.message : '网页预览诊断失败');
    }
  }

  async function monitorExecutionCompletion(sessionId: string): Promise<void> {
    for (let attempt = 0; attempt < 120; attempt += 1) {
      if (isExecutionCompleted(sessionId)) {
        return;
      }
      try {
        const events = await getSessionEvents(sessionId);
        const terminalEvent = [...events].reverse().find((event) => isTerminalEvent(getPayloadEventType(event)));
        if (terminalEvent) {
          if (getPayloadEventType(terminalEvent) === 'ERROR') {
            await completeExecutionWithError(terminalEvent.error || '执行异常', sessionId);
            return;
          }
          await completeExecutionWithResult(sessionId, normalizeResultText(terminalEvent.output, '已完成'));
          return;
        }
      } catch {
        // Ignore transient monitoring polling errors.
      }
      await sleep(500);
    }
  }

  async function completeExecutionWithResult(sessionId: string, result: string): Promise<void> {
    if (markExecutionCompleted(sessionId) === false) {
      return;
    }
    dispatch({
      type: 'HANDLE_RESULT',
      payload: {
        sessionId,
        result
      }
    });
    await pollSandbox(sessionId);
    await disconnectSocket();
  }

  async function completeExecutionWithError(message: string, sessionId?: string): Promise<void> {
    const targetSessionId = sessionId || currentExecutionRef.current.sessionId;
    if (targetSessionId && markExecutionCompleted(targetSessionId) === false) {
      return;
    }
    dispatch({ type: 'SET_ERROR', payload: message });
    dispatch({ type: 'SET_LOADING', payload: false });
    dispatch({ type: 'SET_STATUS', payload: 'error' });
    await disconnectSocket();
  }

  function isExecutionCompleted(sessionId: string): boolean {
    return currentExecutionRef.current.sessionId === sessionId && currentExecutionRef.current.completed;
  }

  function markExecutionCompleted(sessionId: string): boolean {
    if (currentExecutionRef.current.sessionId !== sessionId || currentExecutionRef.current.completed) {
      return false;
    }
    currentExecutionRef.current = { sessionId, completed: true };
    return true;
  }

  async function disconnectSocket(): Promise<void> {
    if (socketRef.current !== null) {
      await socketRef.current.disconnect();
      socketRef.current = null;
    }
  }

  async function reconnect(): Promise<void> {
    if (state.topic === null || state.topic.length === 0) {
      dispatch({ type: 'SET_ERROR', payload: 'No reconnectable session found' });
      return;
    }
    try {
      await disconnectSocket();
      dispatch({ type: 'SET_STATUS', payload: 'connecting' });
      socketRef.current = new WorkflowSocketClient();
      await socketRef.current.connect();
      dispatch({ type: 'SET_STATUS', payload: 'connected' });
      if (state.sessionId !== null) {
        currentExecutionRef.current = { sessionId: state.sessionId, completed: false };
      }
      socketRef.current.subscribe(state.topic, {
        onLog: (payload) => dispatch({ type: 'APPEND_LOG', payload }),
        onEvent: (payload) => {
          dispatch({ type: 'HANDLE_EVENT', payload });
          if (getPayloadEventType(payload) === 'ERROR') {
            void completeExecutionWithError(payload.error || '执行异常');
          }
        },
        onResult: async (payload) => {
          if (state.sessionId !== null) {
            await completeExecutionWithResult(state.sessionId, payload.result || '已完成');
          }
        },
        onSocketError: (message) => {
          dispatch({ type: 'SET_ERROR', payload: message });
          dispatch({ type: 'SET_LOADING', payload: false });
          dispatch({ type: 'SET_STATUS', payload: 'error' });
        }
      });
      if (state.sessionId !== null) {
        void monitorExecutionCompletion(state.sessionId);
      }
    } catch (error) {
      dispatch({ type: 'SET_STATUS', payload: 'error' });
      dispatch({ type: 'SET_ERROR', payload: error instanceof Error ? error.message : 'Reconnection failed' });
    }
  }

  async function cancelStreaming(): Promise<void> {
    await disconnectSocket();
    dispatch({ type: 'SET_LOADING', payload: false });
    dispatch({ type: 'SET_STATUS', payload: 'idle' });
  }

  function resetConversation(): void {
    dispatch({ type: 'RESET' });
    setIframeBlockedReason(null);
    void disconnectSocket();
  }

  function onUrlChange(url: string): void {
    dispatch({ type: 'SET_CURRENT_URL', payload: url });
  }

  return (
    <div className="app-shell">
      <header className="top-nav">
        <div className="brand">OpenManus Frontend</div>
        <div className="nav-actions">
          <span className="status-tag">{statusText}</span>
          <button className="btn secondary" onClick={() => void reconnect()}>
            Reconnect
          </button>
          <button className="btn secondary" onClick={() => void cancelStreaming()} disabled={state.loading === false}>
            Cancel Stream
          </button>
          <button className="btn" onClick={resetConversation}>
            New Chat
          </button>
        </div>
      </header>

      <main className="workspace">
        <section className="panel chat-panel">
          <div className="panel-title">Conversation</div>
          <div className="messages">
            {state.messages.length === 0 ? <div className="empty">Type a prompt to start the conversation</div> : null}
            {state.messages.map((message) => (
              <MessageCard key={message.id} message={message} />
            ))}
            {state.error ? <div className="error-banner">{state.error}</div> : null}
          </div>
          <form className="input-form" onSubmit={(event) => void sendMessage(event)}>
            <textarea
              rows={3}
              value={input}
              onChange={(event) => setInput(event.target.value)}
              onKeyDown={(event) => {
                if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
                  event.preventDefault();
                  void sendMessage();
                }
              }}
              placeholder="Type a message, Ctrl/⌘+Enter to send"
            />
            <div className="input-actions">
              <button
                className="btn secondary"
                type="button"
                onClick={() => setInput('')}
                disabled={state.loading}
              >
                Clear
              </button>
              <button className="btn" type="submit" disabled={state.loading}>
                {state.loading ? 'Processing...' : 'Send'}
              </button>
            </div>
          </form>
        </section>

        <section className={'panel tool-panel ' + (showToolPanel ? '' : 'collapsed')}>
          <div className="panel-title with-action">
            <span>Tools</span>
            <button className="btn tiny secondary" onClick={() => setShowToolPanel(!showToolPanel)}>
              {showToolPanel ? 'Hide' : 'Show'}
            </button>
          </div>
          <div className="tool-tabs">
            <button
              className={activeToolTab === 'search' ? 'active' : ''}
              onClick={() => setActiveToolTab('search')}
            >
              Search Trail
            </button>
            <button
              className={activeToolTab === 'status' ? 'active' : ''}
              onClick={() => setActiveToolTab('status')}
            >
              Web Status
            </button>
            <button
              className={activeToolTab === 'output' ? 'active' : ''}
              onClick={() => setActiveToolTab('output')}
            >
              Tool Output
            </button>
          </div>
          <div className="tool-body">
            {activeToolTab === 'search' ? (
              <>
                <TimelineSection title="Search Timeline" items={state.searchTimeline} />
                {state.searchResults.map((item) => (
                  <article key={item.url} className="card">
                    <h4>{item.title}</h4>
                    <a href={item.url} target="_blank" rel="noreferrer">
                      {item.url}
                    </a>
                    <p>{item.snippet}</p>
                  </article>
                ))}
              </>
            ) : null}
            {activeToolTab === 'status' ? (
              <>
                <article className="card">
                  <h4>Browser State</h4>
                  <p className="muted">Status: {statusLabel(state.browserStatus, state.previewMode)}</p>
                  <p className="muted">Preview: {state.previewMode}</p>
                  {state.previewBlockedReason || iframeBlockedReason ? (
                    <p>{state.previewBlockedReason || iframeBlockedReason}</p>
                  ) : null}
                  {state.snapshotPath ? <p className="muted">Snapshot: {state.snapshotPath}</p> : null}
                  {state.snapshotPreview ? <pre>{state.snapshotPreview}</pre> : null}
                </article>
                <TimelineSection title="Web Timeline" items={state.webTimeline} />
              </>
            ) : null}
            {activeToolTab === 'output'
              ? state.toolOutputs.map((output) => <OutputCard key={output.id} output={output} />)
              : null}
            {activeToolTab === 'search' && state.searchResults.length === 0 ? (
              <div className="empty">No search events yet</div>
            ) : null}
            {activeToolTab === 'status' && state.webTimeline.length === 0 ? (
              <div className="empty">No web status yet</div>
            ) : null}
            {activeToolTab === 'output' && state.toolOutputs.length === 0 ? (
              <div className="empty">No tool output yet</div>
            ) : null}
          </div>
        </section>

        <section className="panel browser-panel">
          <div className="panel-title">Browser / Sandbox</div>
          <div className="browser-toolbar">
            <input
              value={state.currentUrl}
              onChange={(event) => onUrlChange(event.target.value)}
              onBlur={() => onUrlChange(normalizeUrl(state.currentUrl))}
              placeholder="Enter URL"
            />
            <label className="toggle">
              <input
                type="checkbox"
                checked={useProxy}
                onChange={(event) => setUseProxy(event.target.checked)}
              />
              Proxy
            </label>
            <button className={browserMode === 'web' ? 'active' : ''} onClick={() => setBrowserMode('web')}>
              Web
            </button>
            <button
              className={browserMode === 'snapshot' ? 'active' : ''}
              onClick={() => setBrowserMode('snapshot')}
              disabled={!state.snapshotPreview}
            >
              Snapshot
            </button>
            <button
              className={browserMode === 'vnc' ? 'active' : ''}
              onClick={() => setBrowserMode('vnc')}
              disabled={state.sandboxVncUrl === null}
            >
              VNC
            </button>
            {state.currentUrl ? (
              <a className="btn tiny secondary" href={state.currentUrl} target="_blank" rel="noreferrer">
                Open External
              </a>
            ) : null}
          </div>
          <div className="panel-title with-action">
            <span>{statusLabel(state.browserStatus, state.previewMode)}</span>
            <span>
              {state.previewMode === 'vnc' && state.sandboxVncUrl
                ? '真实浏览器正在展示该网页'
                : state.currentUrl || iframeBlockedReason || '等待网页事件'}
            </span>
          </div>
          <div className="browser-frame">
            {browserMode === 'web' ? (
              state.currentUrl.length > 0 ? (
                <iframe
                  src={proxyUrl(state.currentUrl, useProxy)}
                  title="web-preview"
                  onLoad={() => {
                    if (state.previewBlockedReason === null) {
                      setIframeBlockedReason(null);
                    }
                  }}
                  onError={() => {
                    const reason = '当前站点可能拒绝 iframe 嵌入，或代理后的子资源加载失败';
                    setIframeBlockedReason(reason);
                    dispatch({
                      type: 'HANDLE_EVENT',
                      payload: {
                        eventType: 'WEB_PREVIEW_BLOCKED',
                        metadata: {
                          activeUrl: state.currentUrl,
                          previewMode: state.sandboxVncUrl ? 'vnc' : 'external',
                          blockReason: 'iframe-blocked',
                          detail: reason,
                          autoSwitchedToVnc: state.sandboxVncUrl !== null
                        }
                      }
                    });
                    if (state.snapshotPreview) {
                      setBrowserMode('snapshot');
                    } else if (state.sandboxVncUrl) {
                      setBrowserMode('vnc');
                    }
                  }}
                />
              ) : (
                <div className="empty">Enter a URL to start browsing</div>
              )
            ) : browserMode === 'snapshot' ? (
              state.snapshotPreview ? (
                <iframe
                  srcDoc={buildSnapshotDocument(state.snapshotPreview, state.currentUrl)}
                  title="snapshot-preview"
                  sandbox="allow-forms allow-popups allow-popups-to-escape-sandbox allow-same-origin"
                />
              ) : (
                <div className="empty">No page snapshot yet</div>
              )
            ) : state.sandboxVncUrl ? (
              <iframe src={state.sandboxVncUrl} title="vnc-preview" />
            ) : (
              <div className="empty">正在启动真实浏览器工作区...</div>
            )}
          </div>
        </section>
      </main>
    </div>
  );
}

function TimelineSection({ title, items }: { title: string; items: TimelineEntry[] }): JSX.Element {
  return (
    <>
      <article className="card">
        <h4>{title}</h4>
        <p className="muted">{items.length > 0 ? `Latest ${items.length} events` : 'Waiting for events'}</p>
      </article>
      {items.map((item) => (
        <article key={item.id} className="card">
          <h4>{item.title}</h4>
          <p className="muted">{item.time}</p>
          <p>{item.description}</p>
          {item.url ? (
            <a href={item.url} target="_blank" rel="noreferrer">
              {item.url}
            </a>
          ) : null}
        </article>
      ))}
    </>
  );
}

function MessageCard({ message }: { message: ChatMessage }): JSX.Element {
  return (
    <article className={'message-card ' + message.role}>
      <header>
        <strong>{message.role === 'user' ? 'User' : 'AI'}</strong>
        <span>{message.time}</span>
      </header>
      <div
        className="markdown"
        dangerouslySetInnerHTML={{ __html: marked.parse(message.content || '') as string }}
      />
      {message.logs.length > 0 ? (
        <details>
          <summary>Reasoning trace ({message.logs.length})</summary>
          <div className="logs">
            {message.logs.map((log, index) => (
              <div key={String(index) + '-' + (log.timestamp || '')} className="log-line">
                <span>{log.timestamp || '--:--:--'}</span>
                <span>{log.message || ''}</span>
              </div>
            ))}
          </div>
        </details>
      ) : null}
    </article>
  );
}

function OutputCard({ output }: { output: ToolOutput }): JSX.Element {
  return (
    <article className="card">
      <h4>{output.type}</h4>
      <p className="muted">{output.time}</p>
      <pre>{output.content}</pre>
    </article>
  );
}

function proxyUrl(url: string, useProxy: boolean): string {
  if (useProxy === false || url.length === 0) {
    return url;
  }
  const encoded = btoa(unescape(encodeURIComponent(url))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return '/api/proxy/web?url=' + encoded;
}

function normalizeUrl(url: string): string {
  const value = url.trim();
  if (value.length === 0) {
    return '';
  }
  if (value.startsWith('http://') || value.startsWith('https://')) {
    return value;
  }
  return 'https://' + value;
}

function buildSnapshotDocument(snapshot: string, currentUrl: string): string {
  const trimmed = snapshot.trim();
  if (/<html[\s>]/i.test(trimmed) || /<!doctype html/i.test(trimmed)) {
    return injectSnapshotBase(trimmed, currentUrl);
  }
  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <base href="${escapeHtml(currentUrl)}">
  <style>
    body { margin: 0; padding: 18px; font: 14px/1.6 -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; color: #111827; background: #fff; }
    pre { white-space: pre-wrap; word-break: break-word; margin: 0; }
  </style>
</head>
<body><pre>${escapeHtml(snapshot)}</pre></body>
</html>`;
}

function injectSnapshotBase(documentHtml: string, currentUrl: string): string {
  if (currentUrl.length === 0 || /<base[\s>]/i.test(documentHtml)) {
    return documentHtml;
  }
  const baseTag = `<base href="${escapeHtml(currentUrl)}">`;
  const headMatch = /<head[^>]*>/i.exec(documentHtml);
  if (headMatch?.index !== undefined) {
    const insertAt = headMatch.index + headMatch[0].length;
    return documentHtml.slice(0, insertAt) + baseTag + documentHtml.slice(insertAt);
  }
  const htmlMatch = /<html[^>]*>/i.exec(documentHtml);
  if (htmlMatch?.index !== undefined) {
    const insertAt = htmlMatch.index + htmlMatch[0].length;
    return documentHtml.slice(0, insertAt) + `<head>${baseTag}</head>` + documentHtml.slice(insertAt);
  }
  return `<!doctype html><html><head>${baseTag}</head><body>${documentHtml}</body></html>`;
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function nowTime(): string {
  return new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });
}

function statusLabel(status: BrowserStatus, previewMode: string): string {
  const labels: Record<BrowserStatus, string> = {
    idle: 'Idle',
    searching: 'Searching',
    'opening-search-page': 'Opening Search Page',
    'results-ready': 'Results Ready',
    'opening-target-page': 'Opening Target Page',
    'proxy-rendered': 'Proxy Rendered',
    'proxy-blocked': 'Proxy Blocked',
    'auto-switch-vnc': 'Auto Switch VNC',
    loading: 'Loading',
    proxying: 'Proxying',
    rendered: 'Rendered',
    blocked: 'Blocked',
    'fallback-to-vnc': 'Fallback to VNC',
    'open-external': 'Open External'
  };
  return `${labels[status]} · ${previewMode}`;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

function isTerminalEvent(eventType?: string): boolean {
  return eventType === 'AGENT_END' || eventType === 'ERROR';
}

function getPayloadEventType(payload: { event_type?: string; eventType?: string }): string | undefined {
  return payload.event_type || payload.eventType;
}

function normalizeResultText(output: unknown, fallback: string): string {
  if (typeof output === 'string' && output.length > 0) {
    return output;
  }
  if (output === null || output === undefined) {
    return fallback;
  }
  try {
    return JSON.stringify(output, null, 2);
  } catch {
    return String(output);
  }
}
