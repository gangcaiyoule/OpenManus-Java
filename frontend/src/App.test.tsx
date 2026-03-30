import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import App from './App';

describe('App', () => {
  it('renders main frontend shell', () => {
    render(<App />);
    expect(screen.getByText('OpenManus Frontend')).toBeInTheDocument();
    expect(screen.getByText('对话')).toBeInTheDocument();
  });
});
