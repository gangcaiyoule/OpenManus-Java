import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';

const mockStartWorkflow = vi.fn();
const mockGetSessionInfo = vi.fn();
const mockDisconnect = vi.fn();
const mockConnect = vi.fn();
const mockSubscribe = vi.fn();

vi.mock('./api/agentApi', () => ({
  ApiError: class ApiError extends Error {},
  startWorkflow: (...args: unknown[]) => mockStartWorkflow(...args),
  getSessionInfo: (...args: unknown[]) => mockGetSessionInfo(...args)
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
    mockSubscribe.mockImplementation((_topic: string, handlers: Record<string, (...args: unknown[]) => void>) => {
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
    expect(screen.getByText('OpenManus Frontend')).toBeInTheDocument();
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
    expect(screen.getByText('No search results yet')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Tool Output' }));
    expect(screen.getByText('No tool output yet')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Search Results' }));
    expect(screen.getByText('No search results yet')).toBeInTheDocument();
  });

  it('sends message and renders assistant result with tool output', async () => {
    render(<App />);
    await userEvent.type(screen.getByPlaceholderText('Type a message, Ctrl/⌘+Enter to send'), 'hello');
    await userEvent.click(screen.getByRole('button', { name: 'Send' }));

    await waitFor(() => {
      expect(mockStartWorkflow).toHaveBeenCalledTimes(1);
    });
    const assistantResultTexts = await screen.findAllByText('assistant result');
    expect(assistantResultTexts.length).toBeGreaterThan(0);

    await userEvent.click(screen.getByRole('button', { name: 'Tool Output' }));
    const toolOutputTexts = await screen.findAllByText('tool output content');
    expect(toolOutputTexts.length).toBeGreaterThan(0);
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
});
