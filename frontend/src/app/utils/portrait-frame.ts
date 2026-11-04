export interface PortraitFrame {
  width: number;
  left: number;
  top: number;
}

/** Presentation coordinates only: original PNGs and their transparency stay intact. */
export function portraitFrame(image: Pick<ImageData, 'width' | 'height' | 'data'>): PortraitFrame | null {
  const { width, height, data } = image;
  if (width < 40 || height < 40) return null;
  const fullBody = height > width * 1.6;

  // Some roster headshots are PNGs on a flat background rather than transparent PNGs.
  // Only use colour separation when all four upper corner samples agree.
  const corners = [[0, 0], [width - 1, 0], [0, Math.floor(height * .1)], [width - 1, Math.floor(height * .1)]];
  const samples = corners.map(([x, y]) => (y * width + x) * 4);
  const background = samples.every(i => data[i + 3] >= 240 &&
    [0, 1, 2].every(channel => Math.abs(data[i + channel] - data[samples[0] + channel]) <= 16))
    ? [data[samples[0]], data[samples[0] + 1], data[samples[0] + 2]] : null;

  const rows: { y: number; left: number; right: number }[] = [];
  for (let y = 0; y < height; y++) {
    let left = width, right = -1;
    const edgeLeft = y * width * 4, edgeRight = (y * width + width - 1) * 4;
    // Tall official roster photographs may have an opaque, softly shaded backdrop.
    // Compare against the edges of each row instead of assuming one flat colour.
    const rowBackground = fullBody && data[edgeLeft + 3] >= 240 && data[edgeRight + 3] >= 240 &&
      [0, 1, 2].every(channel => Math.abs(data[edgeLeft + channel] - data[edgeRight + channel]) <= 24);
    for (let x = 0; x < width; x++) {
      const pixel = (y * width + x) * 4;
      const foreground = rowBackground ? [0, 1, 2].some(channel =>
        Math.abs(data[pixel + channel] - (data[edgeLeft + channel] +
          (data[edgeRight + channel] - data[edgeLeft + channel]) * x / Math.max(1, width - 1))) > 42)
        : !background || Math.max(
        Math.abs(data[pixel] - background[0]),
        Math.abs(data[pixel + 1] - background[1]),
        Math.abs(data[pixel + 2] - background[2])) > 42;
      if (data[pixel + 3] >= 96 && foreground) {
        left = Math.min(left, x);
        right = x;
      }
    }
    if (right - left >= Math.max(3, width * .025)) rows.push({ y, left, right });
  }
  if (!rows.length) return null;

  const top = rows[0].y;
  const contentHeight = rows[rows.length - 1].y - top + 1;
  const head = rows.filter(row => row.y >= top + contentHeight * (fullBody ? .025 : .08) &&
    row.y <= top + contentHeight * (fullBody ? .12 : .30));
  if (head.length < 5) return null;
  const headWidth = head.reduce((sum, row) => sum + row.right - row.left + 1, 0) / head.length;
  // Existing face portraits and photographs without a clear silhouette keep their framing.
  if (headWidth / width >= .40) return null;

  const center = head.reduce((sum, row) => sum + (row.left + row.right) / 2, 0) / head.length;
  const cropWidth = Math.min(width, Math.max(headWidth * 1.7, width / 2.8));
  const cropHeight = cropWidth * 36 / 31;
  const cropLeft = Math.max(0, Math.min(width - cropWidth, center - cropWidth / 2));
  const cropTop = Math.max(0, top - contentHeight * .025);
  return { width: width / cropWidth * 100, left: -cropLeft / cropWidth * 100, top: -cropTop / cropHeight * 100 };
}
