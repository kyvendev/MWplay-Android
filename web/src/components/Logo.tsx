/** The official MW Play mark, same geometry and colors as res/drawable/mw_play_mark.xml. */
export function Logo({ className = 'logo', title = 'MW Play' }: { className?: string; title?: string }) {
  return (
    <svg className={className} viewBox="6 28 102 52" role="img" aria-label={title}>
      <path fill="#825CFF" d="M8,78 L8,30 L20,30 L31,54 L42,30 L54,30 L54,78 L43,78 L43,50 L35,69 L28,69 L19,50 L19,78 Z" />
      <path fill="#4E8CFF" d="M57,30 L68,30 L74,61 L81,40 L88,61 L95,30 L106,30 L97,78 L88,78 L81,58 L74,78 L65,78 Z" />
    </svg>
  );
}
