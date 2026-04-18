import { ApiClient } from '../api';

describe('ApiClient', () => {
  let api: ApiClient;

  beforeEach(() => {
    api = new ApiClient('http://localhost:0');
  });

  describe('getPreviewUrl', () => {
    it('returns preview URL for clip ID', () => {
      expect(api.getPreviewUrl('clip-123')).toBe('http://localhost:0/api/clips/clip-123/preview');
    });
  });

  describe('getExportUrl', () => {
    it('returns export URL for clip ID', () => {
      expect(api.getExportUrl('clip-456')).toBe('http://localhost:0/api/clips/clip-456/export');
    });
  });

  describe('constructor', () => {
    it('uses custom base URL when provided', () => {
      const custom = new ApiClient('http://custom:9999');
      expect(custom.getPreviewUrl('x')).toBe('http://custom:9999/api/clips/x/preview');
    });
  });
});
