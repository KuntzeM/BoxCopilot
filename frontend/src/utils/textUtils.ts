/**
 * Truncates text to the first line and adds "[...]" if there are additional lines.
 * @param text The text to truncate
 * @returns The first line with "[...]" appended if the text contains multiple lines
 */
export function truncateToFirstLine(text: string | null | undefined): string {
  if (!text) {
    return '';
  }

  const lines = text.split('\n');
  const firstLine = lines[0].trim();

  if (lines.length > 1 || text.includes('\n')) {
    return firstLine ? `${firstLine} [...]` : '';
  }

  return firstLine;
}
