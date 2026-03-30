import { FormEvent, useEffect, useMemo, useReducer, useRef, useState } from 'react';
import { marked } from 'marked';
import { ApiError, getSessionInfo, startWorkflow } from './api/agentApi';
import {
  initialWorkflowState,
  workflowReducer,
  type ChatMessage,
  type ToolOutput
} from './store/workflowStore';
import { WorkflowSocketClient } from './ws/workflowSocketClient';

marked.setOptions({ breaks: true, gfm: true });

export default function App(): JSX.Element {
  const [state, dispatch] = useReducer(workflowReducer, initialWorkflowState);
  const [input, setInput] = useState('');
  const [browserMode, setBrowserMode] = useState<'web' | 'vnc'>('web');
  const [useProxy, setUseProxy] = useState(true);
  const [showToolPanel, setShowToolPanel] = useState(true);
  const [activeToolTab, setActiveToolTab] = useState<'search' | 'output'>('search');
  const socketRef = useRef<WorkflowSocketClient | null>(null);

  const statusText = useMemo(() => {
    switch (state.connectionStatus) {
      case 'connecting':
        return '连接中';
      case 'connected':
        return '已连接';
      case 'error':
        return '连接异常';
      default:
        return '待命';
    }
  }, [state.connectionStatus]);

  useEffect(() => {
    return () => {
      void disconnectSocket();
    };
  }, []);

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

      const startData = await startWorkflow({ input: content });
      const sessionId = startData.sessionId || '';
      const topic = startData.topic || '';
      if (sessionId.length === 0 || topic.length === 0) {
        throw new ApiError('后端未返回可订阅主题');
      }

      dispatch({ type: 'SET_STREAM', payload: { sessionId, topic } });
      socketRef.current = new WorkflowSocketClient();
      await socketRef.current.connect();
      dispatch({ type: 'SET_STATUS', payload: 'connected' });

      socketRef.current.subscribe(topic, {
        onLog: (payload) => dispatch({ type: 'APPEND_LOG', payload }),
        onEvent: (payload) => {
          dispatch({ type: 'HANDLE_EVENT', payload });
          if (payload.eventType === 'ERROR') {
            dispatch({ type: 'SET_LOADING', payload: false });
            dispatch({ type: 'SET_STATUS', payload: 'error' });
            void disconnectSocket();
          }
        },
        onResult: async (payload) => {
          dispatch({ type: 'HANDLE_RESULT', payload });
          await pollSandbox(sessionId);
          await disconnectSocket();
        },
        onSocketError: (message) => {
          dispatch({ type: 'SET_ERROR', payload: message });
          dispatch({ type: 'SET_LOADING', payload: false });
          dispatch({ type: 'SET_STATUS', payload: 'error' });
        }
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : '发送失败';
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

  async function disconnectSocket(): Promise<void> {
    if (socketRef.current !== null) {
      await socketRef.current.disconnect();
      socketRef.current = null;
    }
  }

  async function reconnect(): Promise<void> {
    if (state.topic === null || state.topic.length === 0) {
      dispatch({ type: 'SET_ERROR', payload: '没有可重连的会话' });
      return;
    }
    try {
      await disconnectSocket();
      dispatch({ type: 'SET_STATUS', payload: 'connecting' });
      socketRef.current = new WorkflowSocketClient();
      await socketRef.current.connect();
      dispatch({ type: 'SET_STATUS', payload: 'connected' });
      socketRef.current.subscribe(state.topic, {
        onLog: (payload) => dispatch({ type: 'APPEND_LOG', payload }),
        onEvent: (payload) => {
          dispatch({ type: 'HANDLE_EVENT', payload });
          if (payload.eventType === 'ERROR') {
            dispatch({ type: 'SET_LOADING', payload: false });
            dispatch({ type: 'SET_STATUS', payload: 'error' });
            void disconnectSocket();
          }
        },
        onResult: async (payload) => {
          dispatch({ type: 'HANDLE_RESULT', payload });
          if (state.sessionId !== null) {
            await pollSandbox(state.sessionId);
          }
          await disconnectSocket();
        },
        onSocketError: (message) => {
          dispatch({ type: 'SET_ERROR', payload: message });
          dispatch({ type: 'SET_LOADING', payload: false });
          dispatch({ type: 'SET_STATUS', payload: 'error' });
        }
      });
    } catch (error) {
      dispatch({ type: 'SET_STATUS', payload: 'error' });
      dispatch({ type: 'SET_ERROR', payload: error instanceof Error ? error.message : '重连失败' });
    }
  }

  async function cancelStreaming(): Promise<void> {
    await disconnectSocket();
    dispatch({ type: 'SET_LOADING', payload: false });
    dispatch({ type: 'SET_STATUS', payload: 'idle' });
  }

  function resetConversation(): void {
    dispatch({ type: 'RESET' });
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
            重连
          </button>
          <button className="btn secondary" onClick={() => void cancelStreaming()} disabled={state.loading === false}>
            取消接收
          </button>
          <button className="btn" onClick={resetConversation}>
            新对话
          </button>
        </div>
      </header>

      <main className="workspace">
        <section className="panel chat-panel">
          <div className="panel-title">对话</div>
          <div className="messages">
            {state.messages.length === 0 ? <div className="empty">输入问题开始对话</div> : null}
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
              placeholder="输入消息，Ctrl/⌘+Enter 发送"
            />
            <div className="input-actions">
              <button
                className="btn secondary"
                type="button"
                onClick={() => setInput('')}
                disabled={state.loading}
              >
                清空
              </button>
              <button className="btn" type="submit" disabled={state.loading}>
                {state.loading ? '处理中...' : '发送'}
              </button>
            </div>
          </form>
        </section>

        <section className={'panel tool-panel ' + (showToolPanel ? '' : 'collapsed')}>
          <div className="panel-title with-action">
            <span>工具</span>
            <button className="btn tiny secondary" onClick={() => setShowToolPanel(!showToolPanel)}>
              {showToolPanel ? '隐藏' : '显示'}
            </button>
          </div>
          <div className="tool-tabs">
            <button
              className={activeToolTab === 'search' ? 'active' : ''}
              onClick={() => setActiveToolTab('search')}
            >
              搜索结果
            </button>
            <button
              className={activeToolTab === 'output' ? 'active' : ''}
              onClick={() => setActiveToolTab('output')}
            >
              工具输出
            </button>
          </div>
          <div className="tool-body">
            {activeToolTab === 'search'
              ? state.searchResults.map((item) => (
                  <article key={item.url} className="card">
                    <h4>{item.title}</h4>
                    <a href={item.url} target="_blank" rel="noreferrer">
                      {item.url}
                    </a>
                    <p>{item.snippet}</p>
                  </article>
                ))
              : state.toolOutputs.map((output) => <OutputCard key={output.id} output={output} />)}
            {activeToolTab === 'search' && state.searchResults.length === 0 ? (
              <div className="empty">暂无搜索结果</div>
            ) : null}
            {activeToolTab === 'output' && state.toolOutputs.length === 0 ? (
              <div className="empty">暂无工具输出</div>
            ) : null}
          </div>
        </section>

        <section className="panel browser-panel">
          <div className="panel-title">浏览器 / 沙箱</div>
          <div className="browser-toolbar">
            <input
              value={state.currentUrl}
              onChange={(event) => onUrlChange(event.target.value)}
              onBlur={() => onUrlChange(normalizeUrl(state.currentUrl))}
              placeholder="输入网址"
            />
            <label className="toggle">
              <input
                type="checkbox"
                checked={useProxy}
                onChange={(event) => setUseProxy(event.target.checked)}
              />
              代理
            </label>
            <button className={browserMode === 'web' ? 'active' : ''} onClick={() => setBrowserMode('web')}>
              网页
            </button>
            <button
              className={browserMode === 'vnc' ? 'active' : ''}
              onClick={() => setBrowserMode('vnc')}
              disabled={state.sandboxVncUrl === null}
            >
              VNC
            </button>
          </div>
          <div className="browser-frame">
            {browserMode === 'web' ? (
              state.currentUrl.length > 0 ? (
                <iframe src={proxyUrl(state.currentUrl, useProxy)} title="web-preview" />
              ) : (
                <div className="empty">输入网址开始浏览</div>
              )
            ) : state.sandboxVncUrl ? (
              <iframe src={state.sandboxVncUrl} title="vnc-preview" />
            ) : (
              <div className="empty">VNC 尚未就绪</div>
            )}
          </div>
        </section>
      </main>
    </div>
  );
}

function MessageCard({ message }: { message: ChatMessage }): JSX.Element {
  return (
    <article className={'message-card ' + message.role}>
      <header>
        <strong>{message.role === 'user' ? '你' : 'AI'}</strong>
        <span>{message.time}</span>
      </header>
      <div
        className="markdown"
        dangerouslySetInnerHTML={{ __html: marked.parse(message.content || '') as string }}
      />
      {message.logs.length > 0 ? (
        <details>
          <summary>思考过程 ({message.logs.length})</summary>
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
  const encoded = btoa(encodeURIComponent(url)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
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

function nowTime(): string {
  return new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}
