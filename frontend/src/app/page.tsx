'use client';

import { useState, useCallback, useRef, useMemo, useEffect } from 'react';
import { ApiClient } from '@/lib/api';

type Video = {
  id: string;
  sourceType: string;
  sourceUrl: string | null;
  title: string | null;
  filePath: string | null;
  createdAt: string;
};

type Job = {
  id: string;
  videoId: string;
  status: string;
  currentStage: string;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
};

type StageStatus = {
  id: string;
  jobId: string;
  stage: string;
  status: string;
  startedAt: string | null;
  completedAt: string | null;
  outputPath?: string;
};

type Clip = {
  id: string;
  videoId: string;
  rankPos: number | null;
  score: number | null;
  tier: string;
  startTime: number;
  endTime: number;
  durationSec: number;
  textContent: string | null;
  title: string | null;
  description: string | null;
  renderStatus: string;
  renderPath: string | null;
  exportStatus: string;
  exportPath: string | null;
  createdAt: string;
};

type ClipScore = {
  hookStrength: number | null;
  keywordTrigger: number | null;
  novelty: number | null;
  clarity: number | null;
  emotionalEnergy: number | null;
  textSentiment: number | null;
  pauseStructure: number | null;
  facePresence: number | null;
  sceneChange: number | null;
  topicFit: number | null;
  historyScore: number | null;
  boostTotal: number;
  penaltyTotal: number;
};

type JobDetail = {
  job: Job;
  stages: StageStatus[];
};

type ClipDetail = {
  clip: Clip;
  scoreBreakdown?: ClipScore;
};

type VideoGroup = {
  video: Video;
  job: Job | null;
  jobDetail: JobDetail | null;
  clips: Clip[];
  expanded: boolean;
};

const STAGE_LABELS: Record<string, string> = {
  IMPORT: 'Import Video',
  AUDIO_EXTRACT: 'Extract Audio',
  TRANSCRIBE: 'Transcribe',
  SEGMENT: 'Segment',
  SCORE: 'Score',
  RENDER: 'Render Clips',
  SUBTITLE: 'Add Subtitles',
  VARIATION: 'Generate Variations',
  ANALYTICS: 'Analytics',
};

const TIER_COLORS: Record<string, string> = {
  PRIMARY: 'bg-green-600/20 text-green-400 border-green-600/40',
  BACKUP: 'bg-yellow-600/20 text-yellow-400 border-yellow-600/40',
  SKIP: 'bg-red-600/20 text-red-400 border-red-600/40',
};

const STAGE_ORDER = [
  'IMPORT', 'AUDIO_EXTRACT', 'TRANSCRIBE', 'SEGMENT',
  'SCORE', 'RENDER', 'SUBTITLE', 'VARIATION', 'ANALYTICS',
];

const STATUS_COLORS: Record<string, string> = {
  COMPLETED: 'bg-green-400',
  FAILED: 'bg-red-400',
  RUNNING: 'bg-blue-400 animate-pulse',
  PENDING: 'bg-zinc-500',
  IN_PROGRESS: 'bg-blue-400 animate-pulse',
  QUEUED: 'bg-yellow-400',
  CANCELLED: 'bg-orange-400',
};

function formatTime(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${s.toString().padStart(2, '0')}`;
}

function formatDuration(seconds: number): string {
  return `${seconds.toFixed(1)}s`;
}

function timeAgo(dateStr: string): string {
  try {
    const d = new Date(dateStr);
    const now = new Date();
    const diff = Math.floor((now.getTime() - d.getTime()) / 1000);
    if (diff < 60) return 'just now';
    if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
    if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
    return `${Math.floor(diff / 86400)}d ago`;
  } catch (_) { return '';
  }
}

function tierLabel(tier: string): string {
  switch (tier) {
    case 'PRIMARY': return 'VIRAL';
    case 'BACKUP': return 'POTENTIAL';
    case 'SKIP': return 'SKIP';
    default: return tier;
  }
}

function videoLabel(video: Video): string {
  if (video.sourceUrl && video.sourceUrl.startsWith('http')) {
    try {
      const url = new URL(video.sourceUrl);
      return url.hostname + url.pathname.substring(0, 30);
    } catch (_) { return video.sourceUrl.substring(0, 40);
    }
  }
  return video.sourceUrl || video.id.substring(0, 8);
}

function ProgressBar({ stages }: { stages: StageStatus[] }) {
  const total = STAGE_ORDER.length;
  const completed = stages.filter(s => s.status === 'COMPLETED').length;
  const current = stages.find(s => s.status === 'RUNNING' || s.status === 'IN_PROGRESS');
  const pct = Math.round((completed / total) * 100);
  const renderStage = stages.find(s => s.stage === 'RENDER' && (s.status === 'RUNNING' || s.status === 'IN_PROGRESS'));

  return (
    <div className="space-y-2">
      <div className="flex justify-between text-xs text-zinc-400">
        <span>{current ? STAGE_LABELS[current.stage] || current.stage : 'Waiting...'}</span>
        <span>{pct}%</span>
      </div>
      <div className="h-2 bg-zinc-800 rounded-full overflow-hidden">
        <div className="h-full bg-blue-500 rounded-full transition-all duration-500" style={{ width: `${pct}%` }} />
      </div>
      {renderStage?.outputPath && (
        <p className="text-xs text-blue-400">{renderStage.outputPath}</p>
      )}
      <div className="grid grid-cols-3 gap-1 mt-2">
        {STAGE_ORDER.map((stage) => {
          const stageStatus = stages.find(s => s.stage === stage);
          const status = stageStatus?.status || 'PENDING';
          const isCurrent = status === 'RUNNING' || status === 'IN_PROGRESS';
          const isDone = status === 'COMPLETED';
          const isFailed = status === 'FAILED';
          return (
            <div key={stage} className={`text-xs px-1 py-0.5 rounded truncate ${
              isDone ? 'text-green-400 bg-green-900/30' :
              isCurrent ? 'text-blue-400 bg-blue-900/30 animate-pulse' :
              isFailed ? 'text-red-400 bg-red-900/30' : 'text-zinc-600'
            }`} title={STAGE_LABELS[stage] || stage}>
              {isDone ? '\u2713' : isFailed ? '\u2717' : isCurrent ? '\u25cf' : '\u25cb'} {STAGE_LABELS[stage] || stage}
            </div>
          );
        })}
      </div>
    </div>
  );
}

function ScoreBadge({ score, label }: { score: number | null; label: string }) {
  if (score === null) return null;
  const color = score >= 0.7 ? 'text-green-400' : score >= 0.4 ? 'text-yellow-400' : 'text-red-400';
  return (
    <div className="flex justify-between items-center text-xs">
      <span className="text-zinc-400">{label}</span>
      <span className={`font-mono ${color}`}>{(score * 100).toFixed(0)}%</span>
    </div>
  );
}

function TranscriptView({ clips, filterTier }: { clips: Clip[]; filterTier: string }) {
  const filtered = filterTier === 'ALL' ? clips : clips.filter(c => c.tier === filterTier);
  if (filtered.length === 0) return <p className="text-zinc-500 text-sm py-4">No clips to show.</p>;

  return (
    <div className="space-y-3">
      {filtered.map(clip => (
        <div key={clip.id} className="bg-zinc-800/50 rounded-lg p-4 border border-zinc-700/50">
          <div className="flex items-center gap-2 mb-2">
            <span className="text-sm font-bold text-zinc-200">#{clip.rankPos || '-'}</span>
            <span className={`px-2 py-0.5 rounded text-xs font-medium border ${TIER_COLORS[clip.tier] || ''}`}>
              {tierLabel(clip.tier)}
            </span>
            <span className="text-xs text-zinc-500">
              {formatTime(clip.startTime)} \u2192 {formatTime(clip.endTime)} ({formatDuration(clip.durationSec)})
            </span>
            {clip.score !== null && (
              <span className="ml-auto text-sm font-bold text-zinc-200">{(clip.score * 100).toFixed(0)}%</span>
            )}
          </div>
          {clip.textContent ? (
            <p className="text-sm text-zinc-300 leading-relaxed whitespace-pre-wrap">{clip.textContent}</p>
          ) : (
            <p className="text-sm text-zinc-600 italic">No transcript available</p>
          )}
        </div>
      ))}
    </div>
  );
}

function ClipCard({
  clip, score, previewing, onPreview, onExport, api,
  selectable, selected, onSelect,
}: {
  clip: Clip; score?: ClipScore; previewing: boolean;
  onPreview: () => void; onExport: () => void; api: ApiClient;
  selectable?: boolean; selected?: boolean; onSelect?: () => void;
}) {
  const [showScore, setShowScore] = useState(false);
  const [showFeedback, setShowFeedback] = useState(false);
  const [showMeta, setShowMeta] = useState(false);
  const [feedback, setFeedback] = useState({ views: '', likes: '', comments: '', shares: '', saves: '' });
  const [submitting, setSubmitting] = useState(false);
  const [viralScore, setViralScore] = useState<number | null>(null);
  const [copied, setCopied] = useState<string | null>(null);

  const handleCopy = async (text: string, label: string) => {
    await navigator.clipboard.writeText(text);
    setCopied(label);
    setTimeout(() => setCopied(null), 1500);
  };

  const handleSubmitFeedback = async () => {
    setSubmitting(true);
    try {
      const result = await api.submitFeedback(clip.id, {
        views: parseInt(feedback.views) || 0,
        likes: parseInt(feedback.likes) || 0,
        comments: parseInt(feedback.comments) || 0,
        shares: parseInt(feedback.shares) || 0,
        saves: parseInt(feedback.saves) || 0,
      });
      const data = result as { viralScore: number };
      setViralScore(data.viralScore);
    } catch (_) { /* noop */ } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className={`bg-zinc-800/50 rounded-lg overflow-hidden border transition-colors ${
      selected ? 'border-blue-500 ring-1 ring-blue-500/30' : 'border-zinc-700/50 hover:border-zinc-600'
    }`}>
      <div className="p-4">
        <div className="flex items-start justify-between mb-2">
          <div className="flex items-center gap-2">
            {selectable && (
              <button
                onClick={(e) => { e.stopPropagation(); onSelect?.(); }}
                className={`w-5 h-5 rounded flex items-center justify-center border transition-colors flex-shrink-0 ${
                  selected
                    ? 'bg-blue-600 border-blue-500 text-white'
                    : 'bg-zinc-900 border-zinc-600 hover:border-zinc-400'
                }`}
                aria-label="Select clip"
              >
                {selected && (
                  <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" />
                  </svg>
                )}
              </button>
            )}
            <span className="text-lg font-bold text-zinc-100">#{clip.rankPos || '-'}</span>
            <span className={`px-2 py-0.5 rounded text-xs font-medium border ${TIER_COLORS[clip.tier] || ''}`}>
              {tierLabel(clip.tier)}
            </span>
          </div>
          <div className="text-right">
            <div className="text-2xl font-bold text-zinc-100">{clip.score ? (clip.score * 100).toFixed(0) : '-'}</div>
            <div className="text-xs text-zinc-500">score</div>
          </div>
        </div>
        {clip.title && (
          <p className="text-sm font-medium text-zinc-200 mb-1 line-clamp-2">{clip.title}</p>
        )}
        <div className="flex gap-4 text-xs text-zinc-400 mb-3">
          <span>{formatTime(clip.startTime)} &#8594; {formatTime(clip.endTime)}</span>
          <span>&#8226;</span>
          <span>{formatDuration(clip.durationSec)}</span>
        </div>
        {clip.textContent && (
          <p className="text-sm text-zinc-300 line-clamp-3 mb-3">{clip.textContent}</p>
        )}
        <div className="flex gap-2">
          {clip.renderPath && (
            <button onClick={onPreview} className={`flex-1 py-1.5 rounded text-xs font-medium transition-colors ${
              previewing ? 'bg-blue-600 text-white' : 'bg-zinc-700 text-zinc-300 hover:bg-zinc-600'
            }`}>{previewing ? 'Playing...' : 'Preview'}</button>
          )}
          {score && (
            <button onClick={() => setShowScore(!showScore)} className="flex-1 py-1.5 rounded text-xs font-medium bg-zinc-700 text-zinc-300 hover:bg-zinc-600 transition-colors">
              {showScore ? 'Hide' : 'Score'}
            </button>
          )}
          {(clip.title || clip.description) && (
            <button onClick={() => setShowMeta(!showMeta)} className="flex-1 py-1.5 rounded text-xs font-medium bg-purple-700 text-zinc-200 hover:bg-purple-600 transition-colors">
              {showMeta ? 'Hide' : 'Copy'}
            </button>
          )}
          {clip.exportPath && (
            <a href={api.getExportUrl(clip.id)} className="flex-1 py-1.5 rounded text-xs font-medium bg-green-700 text-white hover:bg-green-600 transition-colors text-center">Download</a>
          )}
          {!clip.exportPath && clip.renderStatus === 'COMPLETED' && (
            <button onClick={onExport} className="flex-1 py-1.5 rounded text-xs font-medium bg-zinc-700 text-zinc-300 hover:bg-zinc-600 transition-colors">Export</button>
          )}
          <button onClick={() => setShowFeedback(!showFeedback)} className="py-1.5 px-2 rounded text-xs font-medium bg-zinc-700 text-zinc-400 hover:bg-zinc-600 transition-colors">
            {viralScore !== null ? `${(viralScore * 100).toFixed(0)}%` : 'FB'}
          </button>
        </div>
      </div>
      {showMeta && (clip.title || clip.description) && (
        <div className="border-t border-zinc-700/50 px-4 py-3 space-y-2">
          {clip.title && (
            <div>
              <div className="flex items-center justify-between mb-1">
                <span className="text-xs text-zinc-500 font-medium">Title</span>
                <button onClick={() => handleCopy(clip.title!, 'title')} className="text-xs text-blue-400 hover:text-blue-300">
                  {copied === 'title' ? 'Copied!' : 'Copy'}
                </button>
              </div>
              <p className="text-sm text-zinc-200 bg-zinc-900/50 rounded p-2">{clip.title}</p>
            </div>
          )}
          {clip.description && (
            <div>
              <div className="flex items-center justify-between mb-1">
                <span className="text-xs text-zinc-500 font-medium">Description</span>
                <button onClick={() => handleCopy(clip.description!, 'desc')} className="text-xs text-blue-400 hover:text-blue-300">
                  {copied === 'desc' ? 'Copied!' : 'Copy'}
                </button>
              </div>
              <p className="text-sm text-zinc-300 bg-zinc-900/50 rounded p-2 whitespace-pre-wrap">{clip.description}</p>
            </div>
          )}
        </div>
      )}
      {showFeedback && (
        <div className="border-t border-zinc-700/50 px-4 py-3 space-y-2">
          <span className="text-xs text-zinc-500 font-medium">TikTok Performance</span>
          <div className="grid grid-cols-5 gap-1">
            <input type="number" placeholder="Views" value={feedback.views} onChange={e => setFeedback(f => ({...f, views: e.target.value}))}
              className="bg-zinc-900 border border-zinc-700 rounded px-2 py-1 text-xs text-zinc-200 w-full" />
            <input type="number" placeholder="Likes" value={feedback.likes} onChange={e => setFeedback(f => ({...f, likes: e.target.value}))}
              className="bg-zinc-900 border border-zinc-700 rounded px-2 py-1 text-xs text-zinc-200 w-full" />
            <input type="number" placeholder="Comments" value={feedback.comments} onChange={e => setFeedback(f => ({...f, comments: e.target.value}))}
              className="bg-zinc-900 border border-zinc-700 rounded px-2 py-1 text-xs text-zinc-200 w-full" />
            <input type="number" placeholder="Shares" value={feedback.shares} onChange={e => setFeedback(f => ({...f, shares: e.target.value}))}
              className="bg-zinc-900 border border-zinc-700 rounded px-2 py-1 text-xs text-zinc-200 w-full" />
            <input type="number" placeholder="Saves" value={feedback.saves} onChange={e => setFeedback(f => ({...f, saves: e.target.value}))}
              className="bg-zinc-900 border border-zinc-700 rounded px-2 py-1 text-xs text-zinc-200 w-full" />
          </div>
          <div className="flex items-center gap-2">
            <button onClick={handleSubmitFeedback} disabled={submitting}
              className="px-3 py-1 rounded text-xs font-medium bg-blue-600 text-white hover:bg-blue-500 disabled:bg-zinc-700 disabled:text-zinc-500 transition-colors">
              {submitting ? 'Saving...' : 'Submit'}
            </button>
            {viralScore !== null && (
              <span className="text-xs text-green-400">Viral Score: {(viralScore * 100).toFixed(1)}%</span>
            )}
          </div>
        </div>
      )}
      {showScore && score && (
        <div className="border-t border-zinc-700/50 px-4 py-3 space-y-1">
          <ScoreBadge score={score.hookStrength} label="Hook" />
          <ScoreBadge score={score.keywordTrigger} label="Keywords" />
          <ScoreBadge score={score.novelty} label="Novelty" />
          <ScoreBadge score={score.clarity} label="Clarity" />
          <ScoreBadge score={score.emotionalEnergy} label="Energy" />
          <ScoreBadge score={score.textSentiment} label="Sentiment" />
          <ScoreBadge score={score.pauseStructure} label="Pauses" />
          <ScoreBadge score={score.facePresence} label="Face" />
          <ScoreBadge score={score.sceneChange} label="Scenes" />
          <ScoreBadge score={score.topicFit} label="Topic Fit" />
          <div className="border-t border-zinc-700/50 pt-1 mt-1">
            <div className="flex justify-between text-xs">
              <span className="text-green-400">Boost +{(score.boostTotal * 100).toFixed(0)}%</span>
              <span className="text-red-400">Penalty -{(score.penaltyTotal * 100).toFixed(0)}%</span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

type DiscoveredVideo = {
  videoId: string;
  title: string;
  url: string;
  duration: number;
  channel: string;
  viewCount: number | null;
  uploadDate: string;
  relevanceScore: number;
};

type DiscoveryMode = 'search' | 'trending' | 'channel';
type MainTab = 'import' | 'discovery' | 'learning';
type TabKey = 'clips' | 'transcript';

function formatDurationSec(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${m}:${s.toString().padStart(2, '0')}`;
}

function DiscoveryPanel({ api, onImport }: { api: ApiClient; onImport: (url: string) => Promise<void> }) {
  const [mode, setMode] = useState<DiscoveryMode>('search');
  const [query, setQuery] = useState('');
  const [channelUrl, setChannelUrl] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [results, setResults] = useState<DiscoveredVideo[]>([]);
  const [importingIds, setImportingIds] = useState<Set<string>>(new Set());

  const handleSearch = async () => {
    if (mode === 'search' && !query.trim()) return;
    if (mode === 'channel' && !channelUrl.trim()) return;
    setLoading(true);
    setError(null);
    setResults([]);
    try {
      let data;
      if (mode === 'search') {
        data = await api.discoverSearch(query.trim()) as { videos: DiscoveredVideo[] };
      } else if (mode === 'trending') {
        data = await api.discoverTrending() as { videos: DiscoveredVideo[] };
      } else {
        data = await api.discoverChannel(channelUrl.trim()) as { videos: DiscoveredVideo[] };
      }
      setResults(data.videos || []);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Discovery failed');
    } finally {
      setLoading(false);
    }
  };

  const handleImportOne = async (video: DiscoveredVideo) => {
    setImportingIds(prev => new Set([...prev, video.videoId]));
    try {
      await onImport(video.url);
    } catch (_) { /* noop */ }
    setImportingIds(prev => { const n = new Set(prev); n.delete(video.videoId); return n; });
  };

  const handleImportTopN = async (n: number) => {
    const top = results.filter(v => v.relevanceScore >= 0.6).slice(0, n);
    for (const v of top) {
      setImportingIds(prev => new Set([...prev, v.videoId]));
      try { await onImport(v.url); } catch (_) { /* noop */ }
      setImportingIds(prev => { const next = new Set(prev); next.delete(v.videoId); return next; });
    }
  };

  const highRelevance = results.filter(v => v.relevanceScore >= 0.6).length;

  return (
    <div className="space-y-4">
      <div className="bg-zinc-900 rounded-lg p-6">
        <div className="flex gap-2 mb-4">
          {(['search', 'trending', 'channel'] as DiscoveryMode[]).map(m => (
            <button key={m} onClick={() => setMode(m)} className={`px-4 py-2 rounded text-sm font-medium transition-colors ${
              mode === m ? 'bg-blue-600 text-white' : 'bg-zinc-800 text-zinc-400 hover:bg-zinc-700'
            }`}>{m.charAt(0).toUpperCase() + m.slice(1)}</button>
          ))}
        </div>

        <div className="flex gap-3">
          {mode === 'search' && (
            <input type="text" value={query} onChange={e => setQuery(e.target.value)}
              placeholder="Search keywords (e.g. rahasia penting indonesia)..."
              onKeyDown={e => e.key === 'Enter' && handleSearch()}
              className="flex-1 bg-zinc-800 border border-zinc-700 rounded px-3 py-2 text-sm focus:outline-none focus:border-blue-500" />
          )}
          {mode === 'channel' && (
            <input type="text" value={channelUrl} onChange={e => setChannelUrl(e.target.value)}
              placeholder="YouTube channel URL (e.g. https://youtube.com/@channel)"
              onKeyDown={e => e.key === 'Enter' && handleSearch()}
              className="flex-1 bg-zinc-800 border border-zinc-700 rounded px-3 py-2 text-sm focus:outline-none focus:border-blue-500" />
          )}
          {mode === 'trending' && (
            <div className="flex-1 text-sm text-zinc-400 py-2">Fetches current Indonesian trending videos</div>
          )}
          <button onClick={handleSearch} disabled={loading}
            className="px-6 bg-blue-600 hover:bg-blue-700 disabled:bg-zinc-700 disabled:text-zinc-500 text-white rounded py-2 text-sm font-medium transition-colors whitespace-nowrap">
            {loading ? 'Searching...' : 'Discover'}
          </button>
        </div>
        {error && <p className="text-red-400 text-xs mt-2">{error}</p>}
      </div>

      {results.length > 0 && (
        <div className="flex items-center gap-3">
          <span className="text-sm text-zinc-400">{results.length} videos found</span>
          {highRelevance > 0 && (
            <>
              <span className="text-green-400 text-sm">{highRelevance} high relevance</span>
              <button onClick={() => handleImportTopN(5)} disabled={loading}
                className="px-4 py-1.5 rounded text-xs font-medium bg-green-700 text-white hover:bg-green-600 disabled:bg-zinc-700 transition-colors">
                Import Top 5
              </button>
            </>
          )}
        </div>
      )}

      {loading && results.length === 0 && (
        <div className="text-center py-8 text-zinc-500">
          <div className="animate-spin inline-block w-6 h-6 border-2 border-blue-500 border-t-transparent rounded-full mb-2" />
          <p>Discovering videos...</p>
        </div>
      )}

      {!loading && results.length === 0 && error === null && (
        <div className="text-center py-8 text-zinc-500">
          <p>Use the search box above to discover viral-worthy Indonesian videos.</p>
        </div>
      )}

      {results.length > 0 && (
        <div className="space-y-2">
          {results.map(video => {
            const isImporting = importingIds.has(video.videoId);
            const scoreColor = video.relevanceScore >= 0.6 ? 'text-green-400' : video.relevanceScore >= 0.3 ? 'text-yellow-400' : 'text-zinc-500';
            const scoreBg = video.relevanceScore >= 0.6 ? 'bg-green-600/20 border-green-600/40' : video.relevanceScore >= 0.3 ? 'bg-yellow-600/20 border-yellow-600/40' : 'bg-zinc-800 border-zinc-700';
            return (
              <div key={video.videoId} className={`rounded-lg p-4 border flex items-start gap-4 ${scoreBg}`}>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-zinc-200 truncate">{video.title || video.videoId}</p>
                  <div className="flex items-center gap-3 mt-1 text-xs text-zinc-400">
                    {video.channel && <span>{video.channel}</span>}
                    {video.duration > 0 && (
                      <>{video.duration > 0 && <span>{formatDurationSec(video.duration)}</span>}</>
                    )}
                    {video.viewCount !== null && video.viewCount > 0 && (
                      <span>{(video.viewCount / 1000).toFixed(1)}K views</span>
                    )}
                  </div>
                </div>
                <div className="flex items-center gap-3 flex-shrink-0">
                  <span className={`text-sm font-bold ${scoreColor}`}>{(video.relevanceScore * 100).toFixed(0)}%</span>
                  <button onClick={() => handleImportOne(video)} disabled={isImporting}
                    className="px-3 py-1.5 rounded text-xs font-medium bg-blue-600 text-white hover:bg-blue-700 disabled:bg-zinc-700 disabled:text-zinc-500 transition-colors">
                    {isImporting ? '...' : 'Import'}
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

export default function Home() {
  const api = useMemo(() => new ApiClient(), []);
  const [mainTab, setMainTab] = useState<MainTab>('import');
  const [youtubeUrl, setYoutubeUrl] = useState('');
  const [importing, setImporting] = useState(false);
  const [importError, setImportError] = useState<string | null>(null);
  const [currentJobId, setCurrentJobId] = useState<string | null>(null);
  const [videoGroups, setVideoGroups] = useState<VideoGroup[]>([]);
  const [clipScores, setClipScores] = useState<Record<string, ClipScore>>({});
  const [previewingClip, setPreviewingClip] = useState<string | null>(null);
  const [selectedClips, setSelectedClips] = useState<Set<string>>(new Set());
  const [exporting, setExporting] = useState(false);
  const [expandedVideos, setExpandedVideos] = useState<Set<string>>(new Set());
  const [activeTabs, setActiveTabs] = useState<Record<string, TabKey>>({});
  const [filterTiers, setFilterTiers] = useState<Record<string, string>>({});
  const [learningStats, setLearningStats] = useState<{ version: number; trained_on: number; total_feedback: number; with_actual_scores: number } | null>(null);
  const [training, setTraining] = useState(false);
  const pollIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const [initialLoad, setInitialLoad] = useState(true);

  const stopPolling = useCallback(() => {
    if (pollIntervalRef.current) {
      clearInterval(pollIntervalRef.current);
      pollIntervalRef.current = null;
    }
  }, []);

  const fetchClipsForVideo = useCallback(async (videoId: string) => {
    try {
      const data = await api.listClips(videoId) as { clips: Clip[] };
      const clipList: Clip[] = data.clips || [];
      const scores: Record<string, ClipScore> = {};
      for (const clip of clipList) {
        try {
          const detail = await api.getClip(clip.id) as ClipDetail;
          if (detail.scoreBreakdown) scores[clip.id] = detail.scoreBreakdown;
        } catch (_) { /* noop */ }
      }
      setClipScores(prev => ({ ...prev, ...scores }));
      return clipList;
    } catch (_) { return [];
    }
  }, [api]);

  const loadAllData = useCallback(async () => {
    try {
      const [videosData, jobsData] = await Promise.all([
        api.listVideos() as Promise<{ videos: Video[] }>,
        api.listJobs() as Promise<{ jobs: Job[] }>,
      ]);
      const videos: Video[] = videosData.videos || [];
      const jobs: Job[] = jobsData.jobs || [];
      const jobMap = new Map(jobs.map(j => [j.videoId, j]));

      const runningJob = jobs.find(j => j.status === 'RUNNING' || j.status === 'PENDING');

      const groups: VideoGroup[] = [];
      for (const video of videos) {
        const job = jobMap.get(video.id) || null;
        let clips: Clip[] = [];
        let jobDetail: JobDetail | null = null;

        if (job) {
          try {
            const jd = await api.getJob(job.id) as JobDetail;
            jobDetail = jd;
          } catch (_) { /* noop */ }
        }

        if (job && (job.status === 'COMPLETED' || (runningJob && job.id === runningJob.id))) {
          clips = await fetchClipsForVideo(video.id);
        }

        groups.push({
          video,
          job,
          jobDetail,
          clips,
          expanded: false,
        });
      }

      groups.sort((a, b) => {
        if (runningJob) {
          if (a.job?.id === runningJob.id) return -1;
          if (b.job?.id === runningJob.id) return 1;
        }
        const aDate = a.job?.createdAt || a.video.createdAt;
        const bDate = b.job?.createdAt || b.video.createdAt;
        return new Date(bDate).getTime() - new Date(aDate).getTime();
      });

      setVideoGroups(groups);

      if (runningJob) {
        setCurrentJobId(runningJob.id);
        setExpandedVideos(prev => new Set([...prev, runningJob.videoId]));
      } else if (groups.length > 0 && groups[0].clips.length > 0) {
        setExpandedVideos(prev => new Set([...prev, groups[0].video.id]));
      }

      try {
        const ws = await api.getWeightsStatus() as Record<string, unknown>;
        setLearningStats({
          version: ws.version as number,
          trained_on: ws.trained_on as number,
          total_feedback: ws.total_feedback as number,
          with_actual_scores: ws.with_actual_scores as number,
        });
      } catch (_) { /* noop */ }
    } catch (_) { /* noop */ }
    setInitialLoad(false);
  }, [api, fetchClipsForVideo]);

  const pollJob = useCallback(async (jobId: string) => {
    try {
      const data = await api.getJob(jobId) as JobDetail;
      setVideoGroups(prev => prev.map(g => {
        if (g.job?.id !== jobId) return g;
        return { ...g, jobDetail: data };
      }));

      if (data.job.status === 'COMPLETED') {
        stopPolling();
        setCurrentJobId(null);
        const clips = await fetchClipsForVideo(data.job.videoId);
        setVideoGroups(prev => prev.map(g => {
          if (g.video.id !== data.job.videoId) return g;
          return { ...g, clips, job: data.job, jobDetail: data };
        }));
        setExpandedVideos(prev => new Set([...prev, data.job.videoId]));
      } else if (data.job.status === 'FAILED') {
        stopPolling();
        setCurrentJobId(null);
        setVideoGroups(prev => prev.map(g => {
          if (g.job?.id !== jobId) return g;
          return { ...g, job: data.job, jobDetail: data };
        }));
      }
    } catch (_) { /* noop */ }
  }, [fetchClipsForVideo, stopPolling, api]);

  const startPolling = useCallback((jobId: string) => {
    pollJob(jobId);
    stopPolling();
    pollIntervalRef.current = setInterval(() => pollJob(jobId), 2000);
  }, [pollJob, stopPolling]);

  useEffect(() => {
    loadAllData();
    return () => stopPolling();
  }, [loadAllData, stopPolling]);

  const handleImport = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!youtubeUrl.trim()) return;
    setImporting(true);
    setImportError(null);
    try {
      const importData = await api.importVideo(youtubeUrl.trim()) as { videoId: string };
      const processData = await api.startProcessing(importData.videoId) as { jobId: string };
      setCurrentJobId(processData.jobId);
      startPolling(processData.jobId);
      setYoutubeUrl('');
      await loadAllData();
    } catch (err: unknown) {
      setImportError(err instanceof Error ? err.message : 'Import failed');
    } finally {
      setImporting(false);
    }
  };

  const toggleExpanded = (videoId: string) => {
    setExpandedVideos(prev => {
      const next = new Set(prev);
      if (next.has(videoId)) next.delete(videoId);
      else next.add(videoId);
      return next;
    });
  };

  const getTab = (videoId: string): TabKey => activeTabs[videoId] || 'clips';
  const setTab = (videoId: string, tab: TabKey) => setActiveTabs(prev => ({ ...prev, [videoId]: tab }));
  const getFilter = (videoId: string): string => filterTiers[videoId] || 'ALL';
  const setFilter = (videoId: string, tier: string) => setFilterTiers(prev => ({ ...prev, [videoId]: tier }));

  const handleExportSelected = async (videoId: string) => {
    if (selectedClips.size === 0) return;
    setExporting(true);
    try {
      await api.exportClips(Array.from(selectedClips));
      setSelectedClips(new Set());
      const clips = await fetchClipsForVideo(videoId);
      setVideoGroups(prev => prev.map(g => g.video.id === videoId ? { ...g, clips } : g));
    } catch (_) { /* noop */ } finally {
      setExporting(false);
    }
  };

  const isActiveJob = currentJobId !== null;

  return (
    <main className="max-w-5xl mx-auto px-4 py-8">
      <h1 className="text-3xl font-bold mb-2">Viral Clipper</h1>
      <p className="text-zinc-400 mb-8">AI-powered video clip maker for Indonesian TikTok content</p>

      <div className="flex gap-2 mb-6">
        <button onClick={() => setMainTab('import')} className={`px-6 py-2.5 rounded text-sm font-medium transition-colors ${
          mainTab === 'import' ? 'bg-blue-600 text-white' : 'bg-zinc-800 text-zinc-400 hover:bg-zinc-700'
        }`}>Import & Clips</button>
        <button onClick={() => setMainTab('discovery')} className={`px-6 py-2.5 rounded text-sm font-medium transition-colors ${
          mainTab === 'discovery' ? 'bg-blue-600 text-white' : 'bg-zinc-800 text-zinc-400 hover:bg-zinc-700'
        }`}>Discover</button>
        <button onClick={() => setMainTab('learning')} className={`px-6 py-2.5 rounded text-sm font-medium transition-colors ${
          mainTab === 'learning' ? 'bg-blue-600 text-white' : 'bg-zinc-800 text-zinc-400 hover:bg-zinc-700'
        }`}>
          Learning {learningStats && learningStats.with_actual_scores > 0 ? `(${learningStats.with_actual_scores})` : ''}
        </button>
      </div>

      {mainTab === 'import' && (
      <section className="bg-zinc-900 rounded-lg p-6 mb-8">
        <h2 className="text-xl font-semibold mb-4">Import Video</h2>
        <form onSubmit={handleImport} className="flex gap-3">
          <input
            type="text"
            value={youtubeUrl}
            onChange={e => setYoutubeUrl(e.target.value)}
            placeholder="Paste YouTube URL..."
            disabled={importing || isActiveJob}
            className="flex-1 bg-zinc-800 border border-zinc-700 rounded px-3 py-2 text-sm focus:outline-none focus:border-blue-500 disabled:opacity-50"
          />
          <button
            type="submit"
            disabled={importing || !youtubeUrl.trim() || isActiveJob}
            className="px-6 bg-blue-600 hover:bg-blue-700 disabled:bg-zinc-700 disabled:text-zinc-500 text-white rounded py-2 text-sm font-medium transition-colors whitespace-nowrap"
          >
            {importing ? 'Importing...' : isActiveJob ? 'Processing...' : 'Import & Process'}
          </button>
        </form>
        {importError && <p className="text-red-400 text-xs mt-2">{importError}</p>}
       </section>
      )}

      {mainTab === 'import' && initialLoad && (
        <div className="text-center py-12 text-zinc-500">
          <div className="animate-spin inline-block w-6 h-6 border-2 border-blue-500 border-t-transparent rounded-full mb-2" />
          <p>Loading...</p>
        </div>
      )}

      {mainTab === 'import' && !initialLoad && videoGroups.length === 0 && (
        <div className="text-center py-12 text-zinc-500">
          <p>No videos yet. Import a YouTube URL above to get started.</p>
        </div>
      )}

      {mainTab === 'import' && !initialLoad && videoGroups.length > 0 && (
        <section>
          <h2 className="text-xl font-semibold mb-4">Videos ({videoGroups.length})</h2>
          <div className="space-y-4">
            {videoGroups.map(group => {
              const isExpanded = expandedVideos.has(group.video.id);
              const isRunning = currentJobId !== null && group.job?.id === currentJobId;
              const tab = getTab(group.video.id);
              const filterTier = getFilter(group.video.id);
              const filteredClips = filterTier === 'ALL' ? group.clips : group.clips.filter(c => c.tier === filterTier);
              const hasRendered = group.clips.some(c => c.renderStatus === 'COMPLETED');
              const tierCounts = {
                PRIMARY: group.clips.filter(c => c.tier === 'PRIMARY').length,
                BACKUP: group.clips.filter(c => c.tier === 'BACKUP').length,
                SKIP: group.clips.filter(c => c.tier === 'SKIP').length,
              };

              return (
                <div key={group.video.id} className={`bg-zinc-900 rounded-lg border transition-colors ${
                  isRunning ? 'border-blue-600/50' : 'border-zinc-800'
                }`}>
                  <button
                    onClick={() => toggleExpanded(group.video.id)}
                    className="w-full px-5 py-4 flex items-center gap-4 text-left hover:bg-zinc-800/50 transition-colors"
                  >
                    <span className={`text-lg transition-transform ${isExpanded ? 'rotate-90' : ''}`}>&#9654;</span>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1">
                        <span className={`w-2 h-2 rounded-full ${STATUS_COLORS[group.job?.status || 'PENDING'] || 'bg-zinc-500'}`} />
                        <span className="text-sm font-medium text-zinc-200 truncate">
                          {group.video.title || group.video.sourceUrl || group.video.id.substring(0, 8)}
                        </span>
                        <span className="text-xs text-zinc-500">{timeAgo(group.video.createdAt)}</span>
                      </div>
                      <div className="flex items-center gap-3 text-xs text-zinc-500">
                        <span>{group.job?.status || 'No job'}</span>
                        {group.clips.length > 0 && (
                          <>
                            <span>\u2022</span>
                            <span>{group.clips.length} clips</span>
                            {tierCounts.PRIMARY > 0 && <span className="text-green-400">{tierCounts.PRIMARY} viral</span>}
                            {tierCounts.BACKUP > 0 && <span className="text-yellow-400">{tierCounts.BACKUP} potential</span>}
                          </>
                        )}
                        {isRunning && group.jobDetail && (
                          <>
                            <span>\u2022</span>
                            <span className="text-blue-400">
                              {Math.round(group.jobDetail.stages.filter(s => s.status === 'COMPLETED').length / STAGE_ORDER.length * 100)}% complete
                            </span>
                          </>
                        )}
                      </div>
                    </div>
                    <span className="text-xs text-zinc-500">{isExpanded ? 'Collapse' : 'Expand'}</span>
                    {group.job && (group.job.status === 'RUNNING' || group.job.status === 'QUEUED') && (
                      <button onClick={async (e) => {
                        e.stopPropagation();
                        try { await api.cancelJob(group.job!.id); await loadAllData(); } catch (_) { /* noop */ }
                      }} className="ml-2 px-3 py-1 rounded text-xs font-medium bg-orange-600 text-white hover:bg-orange-500 transition-colors">
                        Cancel
                      </button>
                    )}
                    {(!group.job || group.job.status === 'FAILED' || group.job.status === 'CANCELLED' || group.job.status === 'COMPLETED') && (
                      <button onClick={async (e) => {
                        e.stopPropagation();
                        if (confirm('Delete this video and all its clips?')) {
                          try { await api.deleteVideo(group.video.id); await loadAllData(); } catch (_) { /* noop */ }
                        }
                      }} className="ml-2 px-3 py-1 rounded text-xs font-medium bg-red-700 text-white hover:bg-red-600 transition-colors">
                        Delete
                      </button>
                    )}
                  </button>

                  {isExpanded && (
                    <div className="border-t border-zinc-800">
                      {isRunning && group.jobDetail && (
                        <div className="px-5 py-4 bg-zinc-800/30">
                          <ProgressBar stages={group.jobDetail.stages} />
                          {group.jobDetail.job.errorMessage && (
                            <p className="text-red-400 text-xs mt-2">{group.jobDetail.job.errorMessage}</p>
                          )}
                        </div>
                      )}

                      {group.clips.length > 0 && (
                        <div className="px-5 py-4">
                          <div className="flex items-center gap-2 mb-4 border-b border-zinc-800">
                            <button
                              onClick={() => setTab(group.video.id, 'clips')}
                              className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
                                tab === 'clips' ? 'border-blue-500 text-blue-400' : 'border-transparent text-zinc-500 hover:text-zinc-300'
                              }`}
                            >Clips ({group.clips.length})</button>
                            <button
                              onClick={() => setTab(group.video.id, 'transcript')}
                              className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
                                tab === 'transcript' ? 'border-blue-500 text-blue-400' : 'border-transparent text-zinc-500 hover:text-zinc-300'
                              }`}
                            >Transcript</button>
                            <div className="ml-auto flex gap-1">
                              {['ALL', 'PRIMARY', 'BACKUP', 'SKIP'].map(tier => (
                                <button key={tier} onClick={() => setFilter(group.video.id, tier)} className={`px-2 py-1 rounded text-xs font-medium transition-colors ${
                                  filterTier === tier ? 'bg-blue-600 text-white' : 'bg-zinc-800 text-zinc-400 hover:bg-zinc-700'
                                }`}>{tier === 'ALL' ? 'All' : tierLabel(tier)}</button>
                              ))}
                            </div>
                          </div>

                          {selectedClips.size > 0 && hasRendered && (
                            <div className="flex items-center gap-3 mb-4 bg-zinc-800/50 rounded-lg p-3">
                              <span className="text-sm text-zinc-400">{selectedClips.size} selected</span>
                              <button onClick={() => handleExportSelected(group.video.id)} disabled={exporting} className="px-4 py-1.5 rounded text-xs font-medium bg-green-700 text-white hover:bg-green-600 disabled:bg-zinc-700 transition-colors">
                                {exporting ? 'Exporting...' : 'Export Selected'}
                              </button>
                              <button onClick={() => setSelectedClips(new Set())} className="px-3 py-1.5 rounded text-xs font-medium bg-zinc-800 text-zinc-400 hover:bg-zinc-700 transition-colors">Clear</button>
                            </div>
                          )}

                          {tab === 'clips' && (
                            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                              {filteredClips.map(clip => (
                                <ClipCard key={clip.id} clip={clip} score={clipScores[clip.id]} previewing={previewingClip === clip.id}
                                  selectable={!!clip.renderPath}
                                  selected={selectedClips.has(clip.id)}
                                  onSelect={() => {
                                    const next = new Set(selectedClips);
                                    if (next.has(clip.id)) next.delete(clip.id); else next.add(clip.id);
                                    setSelectedClips(next);
                                  }}
                                  onPreview={() => setPreviewingClip(previewingClip === clip.id ? null : clip.id)}
                                  onExport={() => {
                                    api.exportClips([clip.id]).then(async () => {
                                      const clips = await fetchClipsForVideo(group.video.id);
                                      setVideoGroups(prev => prev.map(g => g.video.id === group.video.id ? { ...g, clips } : g));
                                    });
                                  }} api={api} />
                              ))}
                            </div>
                          )}

                          {tab === 'transcript' && (
                            <TranscriptView clips={group.clips} filterTier={filterTier} />
                          )}

                          {filteredClips.length === 0 && tab === 'clips' && (
                            <p className="text-zinc-500 text-sm py-8 text-center">No clips match this filter.</p>
                          )}
                        </div>
                      )}

                      {!isRunning && group.clips.length === 0 && group.job?.status === 'COMPLETED' && (
                        <div className="px-5 py-8 text-center text-zinc-500 text-sm">
                          Pipeline completed but no clips were generated. The video may be too short or no segments met the minimum duration.
                        </div>
                      )}

                      {!isRunning && !group.job && (
                        <div className="px-5 py-8 text-center text-zinc-500 text-sm">
                          Video imported but not processed yet.
                        </div>
                      )}

                      {!isRunning && group.job?.status === 'FAILED' && (
                        <div className="px-5 py-4">
                          <div className="bg-red-900/20 border border-red-800/50 rounded p-3">
                            <p className="text-red-400 text-sm font-medium mb-1">Pipeline Failed</p>
                            <p className="text-red-300/70 text-xs">{group.job.errorMessage || 'Unknown error'}</p>
                          </div>
                        </div>
                      )}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
           </section>
      )}

      {mainTab === 'discovery' && (
        <DiscoveryPanel api={api} onImport={async (url) => {
          const importData = await api.importVideo(url) as { videoId: string };
          const processData = await api.startProcessing(importData.videoId) as { jobId: string };
          setCurrentJobId(processData.jobId);
          startPolling(processData.jobId);
          setMainTab('import');
          await loadAllData();
        }} />
      )}

      {mainTab === 'learning' && (
        <section className="space-y-4">
          <div className="bg-zinc-900 rounded-lg p-6">
            <h2 className="text-xl font-semibold mb-4">Self-Learning Scoring</h2>
            <p className="text-sm text-zinc-400 mb-4">
              Submit TikTok performance data for your clips to train the scoring weights. After enough feedback, the system will automatically adjust which features matter most.
            </p>
            {learningStats && (
              <div className="grid grid-cols-4 gap-4 mb-6">
                <div className="bg-zinc-800 rounded-lg p-4 text-center">
                  <div className="text-2xl font-bold text-zinc-100">{learningStats.total_feedback}</div>
                  <div className="text-xs text-zinc-500">Total Feedback</div>
                </div>
                <div className="bg-zinc-800 rounded-lg p-4 text-center">
                  <div className="text-2xl font-bold text-green-400">{learningStats.with_actual_scores}</div>
                  <div className="text-xs text-zinc-500">With TikTok Data</div>
                </div>
                <div className="bg-zinc-800 rounded-lg p-4 text-center">
                  <div className="text-2xl font-bold text-blue-400">v{learningStats.version}</div>
                  <div className="text-xs text-zinc-500">Weight Version</div>
                </div>
                <div className="bg-zinc-800 rounded-lg p-4 text-center">
                  <div className="text-2xl font-bold text-yellow-400">{learningStats.trained_on}</div>
                  <div className="text-xs text-zinc-500">Trained Samples</div>
                </div>
              </div>
            )}
            <div className="flex gap-3">
              <button onClick={async () => {
                setTraining(true);
                try {
                  await api.trainWeights();
                  const ws = await api.getWeightsStatus() as Record<string, unknown>;
                  setLearningStats({
                    version: ws.version as number,
                    trained_on: ws.trained_on as number,
                    total_feedback: ws.total_feedback as number,
                    with_actual_scores: ws.with_actual_scores as number,
                  });
    } catch (e) { console.error(e); } finally {
                  setTraining(false);
                }
              }} disabled={training || !learningStats || learningStats.with_actual_scores < 5}
                className="px-6 py-2.5 rounded text-sm font-medium bg-blue-600 text-white hover:bg-blue-500 disabled:bg-zinc-700 disabled:text-zinc-500 transition-colors">
                {training ? 'Training...' : 'Retrain Weights'}
              </button>
              {learningStats && learningStats.with_actual_scores < 5 && (
                <span className="text-xs text-zinc-500 py-2">
                  Need {5 - learningStats.with_actual_scores} more clips with feedback data to enable training
                </span>
              )}
            </div>
          </div>
        </section>
      )}

      {previewingClip && (
        <div className="fixed inset-0 bg-black/80 z-50 flex items-center justify-center p-4">
          <div className="bg-zinc-900 rounded-lg max-w-sm w-full overflow-hidden">
            <div className="flex items-center justify-between p-4 border-b border-zinc-800">
              <h3 className="text-lg font-semibold">Preview</h3>
              <button onClick={() => setPreviewingClip(null)} className="text-zinc-400 hover:text-zinc-200">&#10005;</button>
            </div>
            <div className="aspect-[9/16] bg-black flex items-center justify-center">
              <video src={api.getPreviewUrl(previewingClip)} controls autoPlay className="max-h-full max-w-full" />
            </div>
          </div>
        </div>
      )}
    </main>
  );
}
