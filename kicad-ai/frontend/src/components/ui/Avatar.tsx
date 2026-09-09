import clsx from "clsx";
import { initials } from "@/lib/format";

interface Props {
  name: string;
  color?: string;
  size?: number;
  className?: string;
  ring?: boolean;
}

export function Avatar({ name, color = "#6366f1", size = 32, className, ring }: Props) {
  return (
    <div
      className={clsx("flex shrink-0 items-center justify-center rounded-full font-semibold text-white select-none", ring && "ring-2 ring-white", className)}
      style={{ width: size, height: size, background: color, fontSize: Math.max(10, size * 0.38) }}
      title={name}
    >
      {initials(name)}
    </div>
  );
}

export function BotAvatar({ size = 36, className }: { size?: number; className?: string }) {
  return (
    <div
      className={clsx("flex shrink-0 items-center justify-center rounded-xl bg-[#23263a] text-white shadow-[0_2px_6px_rgba(35,38,58,0.25)]", className)}
      style={{ width: size, height: size }}
    >
      <svg width={size * 0.62} height={size * 0.62} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <rect x="4" y="8" width="16" height="11" rx="3.5" />
        <path d="M12 8V5.5" />
        <circle cx="12" cy="4.2" r="1.2" fill="currentColor" stroke="none" />
        <circle cx="9.2" cy="13" r="1.3" fill="currentColor" stroke="none" />
        <circle cx="14.8" cy="13" r="1.3" fill="currentColor" stroke="none" />
        <path d="M9.5 16.3h5" />
        <path d="M2.5 12.5v2.5M21.5 12.5v2.5" />
      </svg>
    </div>
  );
}

/** Brand mark: uploaded logo when configured, otherwise the default indigo monogram. */
export function BrandMark({ size = 32, logoUrl, className }: { size?: number; logoUrl?: string | null; className?: string }) {
  if (logoUrl) {
    return <img src={logoUrl} alt="logo" className={clsx("shrink-0 rounded-lg object-contain", className)} style={{ width: size, height: size }} />;
  }
  return (
    <div className={clsx("flex shrink-0 items-center justify-center rounded-lg bg-brand-500 text-white shadow-sm", className)} style={{ width: size, height: size }}>
      <svg width={size * 0.56} height={size * 0.56} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round">
        <path d="M5 18V6l7 8 7-8v12" />
      </svg>
    </div>
  );
}
