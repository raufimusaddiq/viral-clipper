const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

export class ApiClient {
  private baseUrl: string;

  constructor(baseUrl: string = API_BASE) {
    this.baseUrl = baseUrl;
  }

  private async request<T>(path: string, options?: RequestInit): Promise<T> {
    const res = await fetch(`${this.baseUrl}${path}`, {
      headers: { 'Content-Type': 'application/json' },
      ...options,
    });
    const json = await res.json();
    if (json.status === 'error') {
      throw new Error(json.error || 'API error');
    }
    return json.data as T;
  }

  async importVideo(url?: string, localPath?: string) {
    return this.request('/api/import', {
      method: 'POST',
      body: JSON.stringify({ url, localPath }),
    });
  }

  async startProcessing(videoId: string) {
    return this.request('/api/process', {
      method: 'POST',
      body: JSON.stringify({ videoId }),
    });
  }

  async getJob(jobId: string) {
    return this.request(`/api/jobs/${jobId}`);
  }

  async listJobs() {
    return this.request('/api/jobs');
  }

  async listClips(videoId: string) {
    return this.request(`/api/videos/${videoId}/clips`);
  }

  async getClip(clipId: string) {
    return this.request(`/api/clips/${clipId}`);
  }

  async exportClips(clipIds: string[]) {
    return this.request('/api/clips/export', {
      method: 'POST',
      body: JSON.stringify({ clipIds }),
    });
  }

  async listVideos() {
    return this.request('/api/videos');
  }

  getPreviewUrl(clipId: string) {
    return `${this.baseUrl}/api/clips/${clipId}/preview`;
  }

  getExportUrl(clipId: string) {
    return `${this.baseUrl}/api/clips/${clipId}/export`;
  }
}
