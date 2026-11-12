export const AWARD_TIMING = { intro: 2000, nominee: 2300, suspense: 3200, result: 3000 } as const;
export function awardTimeline(
  elapsed: number,
  candidates: number,
): { phase: "intro" | "nominees" | "suspense" | "result" | "done"; nominees: number; results: number } {
  if (elapsed < AWARD_TIMING.intro) return { phase: "intro", nominees: 0, results: 0 };
  if (candidates === 0) return { phase: "done", nominees: 0, results: 0 };
  const nominationEnd = AWARD_TIMING.intro + candidates * AWARD_TIMING.nominee;
  if (elapsed < nominationEnd)
    return {
      phase: "nominees",
      nominees: Math.min(candidates, Math.floor((elapsed - AWARD_TIMING.intro) / AWARD_TIMING.nominee) + 1),
      results: 0,
    };
  const revealStart = nominationEnd + AWARD_TIMING.suspense;
  if (elapsed < revealStart) return { phase: "suspense", nominees: candidates, results: 0 };
  const results = Math.min(candidates, Math.floor((elapsed - revealStart) / AWARD_TIMING.result) + 1);
  return {
    phase: elapsed >= revealStart + candidates * AWARD_TIMING.result ? "done" : "result",
    nominees: candidates,
    results,
  };
}
