import type { ReactNode } from "react";

const svg = (path: ReactNode) => (props: { size?: number }) => (
  <svg viewBox="0 0 24 24" width={props.size ?? 18} height={props.size ?? 18} fill="none" stroke="currentColor"
    strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{path}</svg>
);

export const IconCopy = svg(<><rect x="8" y="8" width="12" height="12" rx="2.5" /><path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2" /></>);
export const IconRegenerate = svg(<><path d="M20 11a8 8 0 1 0-2.3 5.7" /><path d="M20 4v7h-7" /></>);
export const IconSpeaker = svg(<><path d="M4 10v4h4l5 4V6L8 10H4z" /><path d="M16.5 8.5a5 5 0 0 1 0 7M19 6a8.5 8.5 0 0 1 0 12" /></>);
export const IconStopCircle = svg(<><circle cx="12" cy="12" r="9" /><rect x="9" y="9" width="6" height="6" rx="1" /></>);
export const IconMore = svg(<><circle cx="12" cy="5" r="1.3" /><circle cx="12" cy="12" r="1.3" /><circle cx="12" cy="19" r="1.3" /></>);
export const IconChevronLeft = svg(<path d="M15 6l-6 6 6 6" />);
export const IconChevronRight = svg(<path d="M9 6l6 6-6 6" />);
export const IconChevronDown = svg(<path d="M6 9l6 6 6-6" />);
export const IconEdit = svg(<><path d="M4 20h4L19 9l-4-4L4 16v4z" /><path d="M13.5 6.5l4 4" /></>);
export const IconUp = svg(<><path d="M12 19V5" /><path d="M6 11l6-6 6 6" /></>);
export const IconDown = svg(<><path d="M12 5v14" /><path d="M6 13l6 6 6-6" /></>);
export const IconBolt = svg(<path d="M13 3L5 13h6l-1 8 8-10h-6l1-8z" />);
export const IconClock = svg(<><circle cx="12" cy="12" r="8.5" /><path d="M12 7.5V12l3 2" /></>);
export const IconPlus = svg(<path d="M12 5v14M5 12h14" />);
export const IconMic = svg(<><rect x="9" y="3" width="6" height="11" rx="3" /><path d="M5.5 11a6.5 6.5 0 0 0 13 0M12 17.5V21" /></>);
export const IconClose = svg(<path d="M6 6l12 12M18 6L6 18" />);
export const IconFile = svg(<><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8l-5-5z" /><path d="M14 3v5h5" /></>);
export const IconImage = svg(<><rect x="3.5" y="4.5" width="17" height="15" rx="2.5" /><circle cx="9" cy="10" r="1.8" /><path d="M20 16l-5-5-8 8" /></>);
export const IconTool = svg(<path d="M14.5 6.5a4 4 0 0 0-5.3 5.2L4 17l3 3 5.3-5.2a4 4 0 0 0 5.2-5.3l-2.5 2.5-2.5-.5-.5-2.5 2.5-2.5z" />);
export const IconStar = svg(<path d="M12 4l2.5 5 5.5.8-4 3.9.9 5.5L12 16.6 7.1 19.2 8 13.7 4 9.8 9.5 9 12 4z" />);
export const IconShare = svg(<><circle cx="18" cy="5.5" r="2.5" /><circle cx="6" cy="12" r="2.5" /><circle cx="18" cy="18.5" r="2.5" /><path d="M8.2 10.8l7.6-4M8.2 13.2l7.6 4" /></>);
export const IconFork = svg(<><circle cx="6" cy="5" r="2" /><circle cx="18" cy="5" r="2" /><circle cx="12" cy="19" r="2" /><path d="M6 7v2a3 3 0 0 0 3 3h6a3 3 0 0 0 3-3V7M12 12v5" /></>);
export const IconTrash = svg(<><path d="M5 7h14M10 7V5h4v2M7 7l1 12h8l1-12" /></>);
export const IconMonitor = svg(<><rect x="3" y="4" width="18" height="12" rx="2" /><path d="M8 20h8M12 16v4" /></>);
export const IconPhone = svg(<><rect x="7" y="2.5" width="10" height="19" rx="2.5" /><path d="M11 18.5h2" /></>);
export const IconSpark = svg(<path d="M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5L18 18M6 18l2.5-2.5M15.5 8.5L18 6" />);
