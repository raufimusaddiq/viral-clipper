import { render, screen } from '@testing-library/react';
import Home from '@/app/page';

describe('Home', () => {
  it('renders the page title', () => {
    render(<Home />);
    expect(screen.getByText('Viral Clipper')).toBeInTheDocument();
  });

  it('renders the import form', () => {
    render(<Home />);
    expect(screen.getByPlaceholderText('Paste YouTube URL...')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /import & process/i })).toBeInTheDocument();
  });

  it('shows initial job placeholder', () => {
    render(<Home />);
    expect(screen.getByText(/no active job/i)).toBeInTheDocument();
  });

  it('does not show clips section initially', () => {
    render(<Home />);
    expect(screen.queryByText(/generated clips/i)).not.toBeInTheDocument();
  });

  it('shows processing job section header', () => {
    render(<Home />);
    expect(screen.getByText('Processing Job')).toBeInTheDocument();
  });

  it('shows import video section header', () => {
    render(<Home />);
    expect(screen.getByText('Import Video')).toBeInTheDocument();
  });

  it('disables submit when input is empty', () => {
    render(<Home />);
    expect(screen.getByRole('button', { name: /import & process/i })).toBeDisabled();
  });

  it('renders the page description', () => {
    render(<Home />);
    expect(screen.getByText(/AI-powered video clip maker/i)).toBeInTheDocument();
  });
});
