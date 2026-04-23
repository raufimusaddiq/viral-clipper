import { render, screen, fireEvent } from '@testing-library/react';
import Home from '@/app/page';

beforeAll(() => {
  // DiscoveryPanel calls listCandidates on mount; page also polls jobs/videos.
  // Stub fetch so the tests don't hit the network.
  global.fetch = jest.fn(() =>
    Promise.resolve({
      ok: true,
      json: () => Promise.resolve({ status: 'ok', data: { videos: [] } }),
    })
  ) as unknown as typeof fetch;
});

describe('Home', () => {
  it('renders the page title', () => {
    render(<Home />);
    expect(screen.getByText('Viral Clipper')).toBeInTheDocument();
  });

  it('renders the page description', () => {
    render(<Home />);
    expect(screen.getByText(/AI-powered video clip maker/i)).toBeInTheDocument();
  });

  it('renders the three top-level tabs', () => {
    render(<Home />);
    expect(screen.getByRole('button', { name: /import & clips/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /discover/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /learning/i })).toBeInTheDocument();
  });

  it('renders the import form on the default tab', () => {
    render(<Home />);
    expect(screen.getByPlaceholderText('Paste YouTube URL...')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /import & process/i })).toBeInTheDocument();
  });

  it('renders the "Import Video" section header', () => {
    render(<Home />);
    expect(screen.getByText('Import Video')).toBeInTheDocument();
  });

  it('disables submit when input is empty', () => {
    render(<Home />);
    expect(screen.getByRole('button', { name: /import & process/i })).toBeDisabled();
  });

  it('does not show clips section before data loads', () => {
    render(<Home />);
    // The clips list only appears once videoGroups is populated; before that
    // the main column shows a loading spinner.
    expect(screen.queryByText(/generated clips/i)).not.toBeInTheDocument();
  });

  it('switches to the Discover tab and shows mode buttons + filter', () => {
    render(<Home />);
    fireEvent.click(screen.getByRole('button', { name: /discover/i }));
    // DiscoveryPanel mode toggles
    expect(screen.getByRole('button', { name: /^Search$/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^Trending$/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^Channel$/ })).toBeInTheDocument();
    // Status filter exposes QUEUED and IMPORTED buckets introduced by persistence
    expect(screen.getByRole('button', { name: 'QUEUED' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'IMPORTED' })).toBeInTheDocument();
  });
});
