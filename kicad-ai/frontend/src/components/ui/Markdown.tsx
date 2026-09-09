import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import clsx from "clsx";

export function Markdown({ content, className }: { content: string; className?: string }) {
  return (
    <div className={clsx("markdown text-[13.5px] leading-relaxed", className)}>
      <ReactMarkdown remarkPlugins={[remarkGfm]}>{content}</ReactMarkdown>
    </div>
  );
}
