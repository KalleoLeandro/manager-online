export function navigate(path: string): void { window.location.hash = path; }
export function currentPath(): string { return window.location.hash.slice(1) || 'login'; }
