// Small display components: pipeline ProgressBar, ScoreBadge, TranscriptView.
// Vercel Geist styling: solid blue accent, crisp borders, no gradients.

import type { Clip, StageStatus } from '@/types';
import {
  STAGE_ORDER,
  STAGE_LABELS,
  TIER_COLORS,
  formatTime,
  formatDuration,
  tierLabel,
} from '@/lib/ui-utils';

export function ProgressBar({ stages }: { stages: StageStatus[] }) {
  const total = STAGE_ORDER.length;
  const completed = stages.filter(s => s.status === 'COMPLETED').length;
  const current = stages.find(s => s.status === 'RUNNING' || s.status === 'IN_PROGRESS');
  const pct = Math.round((completed / total) * 100);
  const renderStage = stages.find(
    s => s.stage === 'RENDER' && (s.status === 'RUNNING' || s.status === 'IN_PROGRESS'),
  );

  return (
    <div className="space-y-3">
      <div className="flex justify-between items-baseline text-sm">
        <span className="text-muted">
          {current ? STAGE_LABELS[current.stage] || current.stage : 'Waiting…'}
        </span>
        <span className="font-mono text-xs text-subtle">{pct}%</span>
      </div>
      <div className="h-1 bg-surface-2 rounded-full overflow-hidden">
        <div
          className="h-full bg-accent rounded-full transition-all duration-500 ease-out"
          style={{ width: `${pct}%` }}
        />
      </div>
      {renderStage?.outputPath && (
        <p className="text-xs text-accent font-mono">{renderStage.outputPath}</p>
      )}
      <div className="grid grid-cols-3 gap-1.5 pt-1">
        {STAGE_ORDER.map((stage) => {
          const stageStatus = stages.find(s => s.stage === stage);
          const status = stageStatus?.status || 'PENDING';
          const isCurrent = status === 'RUNNING' || status === 'IN_PROGRESS';
          const isDone = status === 'COMPLETED';
          const isFailed = status === 'FAILED';
          return (
            <div
              key={stage}
              className={`text-xs px-2 py-1 rounded-card truncate border ${
                isDone ? 'text-success bg-success-tint border-success/30' :
                isCurrent ? 'text-accent bg-accent-tint border-accent/40' :
                isFailed ? 'text-error bg-error-tint border-error/30' :
                'text-subtle bg-surface border-line'
              }`}
              title={STAGE_LABELS[stage] || stage}
            >
              {isDone ? '✓' : isFailed ? '✗' : isCurrent ? '●' : '○'}{' '}
              {STAGE_LABELS[stage] || stage}
            </div>
          );
        })}
      </div>
    </div>
  );
}

export function ScoreBadge({ score, label }: { score: number | null; label: string }) {
  if (score === null) return null;
  const color =
    score >= 0.7 ? 'text-success' :
    score >= 0.4 ? 'text-warning' : 'text-error';
  return (
    <div className="flex justify-between items-center text-xs">
      <span className="text-muted">{label}</span>
      <span className={`font-mono ${color}`}>{(score * 100).toFixed(0)}%</span>
    </div>
  );
}

export function TranscriptView({ clips, filterTier }: { clips: Clip[]; filterTier: string }) {
  const filtered = filterTier === 'ALL' ? clips : clips.filter(c => c.tier === filterTier);
  if (filtered.length === 0) {
    return <p className="text-subtle text-sm py-4">No clips to show.</p>;
  }

  return (
    <div className="space-y-3">
      {filtered.map(clip => (
        <div
          key={clip.id}
          className="bg-surface rounded-card p-4 border border-line"
        >
          <div className="flex items-center gap-2 mb-2">
            <span className="text-sm font-mono font-semibold text-fg">
              #{clip.rankPos || '-'}
            </span>
            <span
              className={`px-2 py-0.5 rounded text-xs font-medium border ${TIER_COLORS[clip.tier] || ''}`}
            >
              {tierLabel(clip.tier)}
            </span>
            <span className="text-xs font-mono text-subtle">
              {formatTime(clip.startTime)} → {formatTime(clip.endTime)} ({formatDuration(clip.durationSec)})
            </span>
            {clip.score !== null && (
              <span className="ml-auto text-sm font-mono font-semibold text-accent">
                {(clip.score * 100).toFixed(0)}%
              </span>
            )}
          </div>
          {clip.textContent ? (
            <p className="text-sm text-fg/85 leading-relaxed whitespace-pre-wrap">
              {clip.textContent}
            </p>
          ) : (
            <p className="text-sm text-subtle italic">No transcript available</p>
          )}
        </div>
      ))}
    </div>
  );
}
