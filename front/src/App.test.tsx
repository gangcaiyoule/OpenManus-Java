import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';

const mockStartWorkflow = vi.fn();
const mockGetSessionInfo = vi.fn();
const mockGetSessionEvents = vi.fn();
const mockStartSessionSandbox = vi.fn();
const mockInspectProxyPreview = vi.fn();
const mockDisconnect = vi.fn();
const mockConnect = vi.fn();
const mockSubscribe = vi.fn();

vi.mock('./api/agentApi', () => ({
  ApiError: class ApiError extends Error {},
  startWorkflow: (...args: unknown[]) => mockStartWorkflow(...args),
  getSessionInfo: (...args: unknown[]) => mockGetSessionInfo(...args),
  getSessionEvents: (...args: unknown[]) => mockGetSessionEvents(...args),
  startSessionSandbox: (...args: unknown[]) => mockStartSessionSandbox(...args),
  inspectProxyPreview: (...args: unknown[]) => mockInspectProxyPreview(...args)
}));

vi.mock('./ws/workflowSocketClient', () => ({
  WorkflowSocketClient: class MockWorkflowSocketClient {
    connect = mockConnect;
    disconnect = mockDisconnect;
    subscribe = mockSubscribe;
  }
}));

describe('App', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockConnect.mockResolvedValue(undefined);
    mockDisconnect.mockResolvedValue(undefined);
    mockStartWorkflow.mockResolvedValue({ success: true, sessionId: 's-1', topic: '/topic/s-1' });
    mockGetSessionInfo.mockResolvedValue({
      sessionId: 's-1',
      sandboxAvailable: true,
      sandboxVncUrl: 'https://vnc.local'
    });
    mockStartSessionSandbox.mockResolvedValue({
      sessionId: 's-1',
      sandboxAvailable: true,
      sandboxVncUrl: 'https://vnc.local'
    });
    mockInspectProxyPreview.mockResolvedValue({
      enabled: true,
      targetUrl: 'https://www.google.com/search?q=openai+docs',
      proxyUrl: '/api/proxy/web?url=abc',
      state: 'proxy-rendered',
      reasonCode: '',
      reason: '',
      previewMode: 'proxy',
      previewableHtml: true,
      fallbackToVnc: false
    });
    mockGetSessionEvents.mockResolvedValue([]);
    mockSubscribe.mockImplementation((_topic: string, handlers: Record<string, (...args: unknown[]) => void>) => {
      handlers.onEvent?.({
        eventType: 'SEARCH_STARTED',
        metadata: {
          query: 'openai docs',
          searchPageUrl: 'https://www.google.com/search?q=openai+docs',
          previewMode: 'web'
        }
      });
      handlers.onEvent?.({
        eventType: 'TOOL_CALL_END',
        agentName: 'Browser',
        output: 'tool output content'
      });
      handlers.onResult?.({
        result: 'assistant result',
        sessionId: 's-1'
      });
    });
  });

  it('renders main shell and panels', () => {
    render(<App />);
    expect(screen.getByText('OpenManus')).toBeInTheDocument();
    expect(screen.getByText('Agent workspace')).toBeInTheDocument();
    expect(screen.getByText('Conversation')).toBeInTheDocument();
    expect(screen.getByText('Tools')).toBeInTheDocument();
    expect(screen.getByText('Browser / Sandbox')).toBeInTheDocument();
  });

  it('supports ctrl/cmd + enter submit', async () => {
    render(<App />);
    const textarea = screen.getByPlaceholderText('Type a message, Ctrl/⌘+Enter to send');
    await userEvent.type(textarea, 'hello via hotkey');
    fireEvent.keyDown(textarea, { key: 'Enter', ctrlKey: true });

    await waitFor(() => {
      expect(mockStartWorkflow).toHaveBeenCalledTimes(1);
    });
  });

  it('shows reconnect error when no topic exists', async () => {
    render(<App />);
    await userEvent.click(screen.getByRole('button', { name: 'Reconnect' }));
    expect(screen.getByText('No reconnectable session found')).toBeInTheDocument();
  });

  it('switches between search and tool output tabs', async () => {
    render(<App />);
    expect(screen.getByText('No search events yet')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Web Status' }));
    expect(screen.getByText('No web status yet')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Tool Output' }));
    expect(screen.getByText('No tool output yet')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Search Trail' }));
    expect(screen.getByText('No search events yet')).toBeInTheDocument();
  });

  it('sends message and renders assistant result with tool output', async () => {
    render(<App />);
    await userEvent.type(screen.getByPlaceholderText('Type a message, Ctrl/⌘+Enter to send'), 'hello');
    await userEvent.click(screen.getByRole('button', { name: 'Send' }));

    await waitFor(() => {
      expect(mockStartWorkflow).toHaveBeenCalledTimes(1);
    });
    expect(mockStartSessionSandbox).toHaveBeenCalledWith('s-1');
    const assistantResultTexts = await screen.findAllByText('assistant result');
    expect(assistantResultTexts.length).toBeGreaterThan(0);

    await userEvent.click(screen.getByRole('button', { name: 'Tool Output' }));
    const toolOutputTexts = await screen.findAllByText('tool output content');
    expect(toolOutputTexts.length).toBeGreaterThan(0);
    await userEvent.click(screen.getByRole('button', { name: 'Search Trail' }));
    expect(screen.getByText('搜索已发起')).toBeInTheDocument();
  });

  it('clears prompt input', async () => {
    render(<App />);
    const textarea = screen.getByPlaceholderText('Type a message, Ctrl/⌘+Enter to send');
    await userEvent.type(textarea, 'to be cleared');
    expect(textarea).toHaveValue('to be cleared');
    await userEvent.click(screen.getByRole('button', { name: 'Clear' }));
    expect(textarea).toHaveValue('');
  });

  it('resets conversation when clicking new chat', async () => {
    render(<App />);
    await userEvent.type(screen.getByPlaceholderText('Type a message, Ctrl/⌘+Enter to send'), 'history task');
    await userEvent.click(screen.getByRole('button', { name: 'Send' }));
    await screen.findAllByText('assistant result');

    await userEvent.click(screen.getByRole('button', { name: 'New Chat' }));
    expect(screen.queryByText('history task')).not.toBeInTheDocument();
    expect(screen.getByText('Type a prompt to start the conversation')).toBeInTheDocument();
  });

  it('shows error banner when request fails', async () => {
    mockStartWorkflow.mockRejectedValueOnce(new Error('network error'));
    render(<App />);
    await userEvent.type(screen.getByPlaceholderText('Type a message, Ctrl/⌘+Enter to send'), 'trigger error');
    await userEvent.click(screen.getByRole('button', { name: 'Send' }));

    await waitFor(() => {
      expect(screen.getByText('network error')).toBeInTheDocument();
    });
  });

  it('enables vnc mode after sandbox url is available', async () => {
    render(<App />);
    const vncButton = screen.getByRole('button', { name: 'VNC' });
    expect(vncButton).toBeDisabled();

    await userEvent.type(screen.getByPlaceholderText('Type a message, Ctrl/⌘+Enter to send'), 'start');
    await userEvent.click(screen.getByRole('button', { name: 'Send' }));

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'VNC' })).toBeEnabled();
    });

    await userEvent.click(screen.getByRole('button', { name: 'VNC' }));
    const vncFrame = screen.getByTitle('vnc-preview');
    expect(vncFrame).toHaveAttribute('src', 'https://vnc.local');
  });

  it('auto switches to vnc when proxy preview is blocked', async () => {
    mockInspectProxyPreview.mockResolvedValueOnce({
      enabled: true,
      targetUrl: 'https://blocked.example.com',
      proxyUrl: '/api/proxy/web?url=blocked',
      state: 'fetch-failed',
      reasonCode: 'fetch-failed',
      reason: '代理抓取网页失败',
      previewMode: 'vnc',
      previewableHtml: false,
      fallbackToVnc: true
    });
    mockSubscribe.mockImplementation((_topic: string, handlers: Record<string, (...args: unknown[]) => void>) => {
      handlers.onEvent?.({
        eventType: 'BROWSER_URL_OPENED',
        metadata: {
          activeUrl: 'https://blocked.example.com',
          previewMode: 'proxy'
        }
      });
      handlers.onResult?.({
        result: 'assistant result',
        sessionId: 's-1'
      });
    });

    render(<App />);
    await userEvent.type(screen.getByPlaceholderText('Type a message, Ctrl/⌘+Enter to send'), 'open blocked');
    await userEvent.click(screen.getByRole('button', { name: 'Send' }));

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'VNC' })).toHaveClass('active');
    });
    expect(screen.getByText('真实浏览器正在展示该网页')).toBeInTheDocument();
    expect(screen.queryByText('fetch-failed')).not.toBeInTheDocument();
  });

  it('shows agent page snapshot in browser panel when web fetch returns html', async () => {
    mockSubscribe.mockImplementation((_topic: string, handlers: Record<string, (...args: unknown[]) => void>) => {
      handlers.onEvent?.({
        eventType: 'TOOL_CALL_END',
        agentName: 'WebFetchTool',
        output: JSON.stringify({
          url: 'https://example.com/page',
          path: '/workspace/.openmanus/web/web-snapshot.txt',
          preview: '<html><body><h1>Agent visible page</h1></body></html>'
        })
      });
      handlers.onResult?.({
        result: 'assistant result',
        sessionId: 's-1'
      });
    });

    render(<App />);
    await userEvent.type(screen.getByPlaceholderText('Type a message, Ctrl/⌘+Enter to send'), 'browse page');
    await userEvent.click(screen.getByRole('button', { name: 'Send' }));

    const snapshotFrame = await screen.findByTitle('snapshot-preview');
    expect(snapshotFrame).toHaveAttribute('srcdoc', expect.stringContaining('Agent visible page'));
    expect(screen.getByRole('button', { name: 'Snapshot' })).toHaveClass('active');
  });

  it('shows real browser workspace when openUrl reports vnc navigation', async () => {
    mockSubscribe.mockImplementation((_topic: string, handlers: Record<string, (...args: unknown[]) => void>) => {
      handlers.onEvent?.({
        eventType: 'BROWSER_URL_OPENED',
        metadata: {
          activeUrl: 'https://visible.example.com',
          previewMode: 'vnc',
          sandboxVncUrl: 'https://vnc.local'
        }
      });
      handlers.onResult?.({
        result: 'assistant result',
        sessionId: 's-1'
      });
    });

    render(<App />);
    await userEvent.type(screen.getByPlaceholderText('Type a message, Ctrl/⌘+Enter to send'), 'open visible');
    await userEvent.click(screen.getByRole('button', { name: 'Send' }));

    const vncFrame = await screen.findByTitle('vnc-preview');
    expect(vncFrame).toHaveAttribute('src', 'https://vnc.local');
    expect(screen.getByRole('button', { name: 'VNC' })).toHaveClass('active');
    expect(screen.queryByTitle('web-preview')).not.toBeInTheDocument();
  });

  it('falls back to monitoring events when websocket result is missed', async () => {
    mockSubscribe.mockImplementation((_topic: string, handlers: Record<string, (...args: unknown[]) => void>) => {
      handlers.onEvent?.({
        eventType: 'TOOL_CALL_END',
        agentName: 'Browser',
        output: 'tool output content'
      });
    });
    mockGetSessionEvents.mockResolvedValue([
      {
        eventType: 'AGENT_END',
        output: 'result from monitoring fallback',
        sessionId: 's-1'
      }
    ]);

    render(<App />);
    await userEvent.type(screen.getByPlaceholderText('Type a message, Ctrl/⌘+Enter to send'), 'hello fallback');
    await userEvent.click(screen.getByRole('button', { name: 'Send' }));

    await waitFor(() => {
      expect(screen.getByText('result from monitoring fallback')).toBeInTheDocument();
    });
    expect(mockGetSessionEvents).toHaveBeenCalledWith('s-1');
  });
});
