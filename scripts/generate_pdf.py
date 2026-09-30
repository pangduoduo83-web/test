import os
import sys
import subprocess
from pathlib import Path
from markdown_it import MarkdownIt

def main():
    root = Path(__file__).resolve().parent.parent
    md_file = root / "系统全功能使用说明手册.md"
    html_file = root / "系统全功能使用说明手册.html"
    pdf_file = root / "AI未来实践教学平台_系统全功能使用说明手册.pdf"

    if not md_file.exists():
        print(f"File not found: {md_file}")
        sys.exit(1)

    with open(md_file, "r", encoding="utf-8") as f:
        md_text = f.read()

    md = MarkdownIt("commonmark").enable("table").enable("strikethrough")
    html_content = md.render(md_text)

    # 封面与高颜值 CSS 样式
    html_doc = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<title>AI未来实践教学平台 · 系统全功能使用说明手册</title>
<style>
@page {{
    size: A4;
    margin: 22mm 18mm 22mm 18mm;
    @bottom-right {{
        content: counter(page);
    }}
}}
body {{
    font-family: -apple-system, "PingFang SC", "Microsoft YaHei", "WenQuanYi Micro Hei", sans-serif;
    color: #1e293b;
    line-height: 1.75;
    font-size: 13.5px;
    background: #ffffff;
}}
.cover {{
    page-break-after: always;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    text-align: center;
    min-height: 820px;
    box-sizing: border-box;
    padding-top: 140px;
}}
.cover-badge {{
    display: inline-block;
    background: #e0e7ff;
    color: #3730a3;
    font-size: 13px;
    font-weight: 600;
    padding: 6px 18px;
    border-radius: 9999px;
    margin-bottom: 24px;
    letter-spacing: 1px;
}}
.cover h1 {{
    font-size: 34px;
    color: #0f172a;
    margin: 0 0 16px 0;
    font-weight: 800;
    letter-spacing: 1.5px;
}}
.cover .subtitle {{
    font-size: 22px;
    color: #2563eb;
    margin: 0 0 24px 0;
    font-weight: 600;
}}
.cover .desc {{
    font-size: 15px;
    color: #475569;
    margin: 0 0 160px 0;
}}
.cover-meta {{
    border-top: 2px solid #e2e8f0;
    padding-top: 24px;
    width: 380px;
    margin: 0 auto;
    font-size: 13.5px;
    color: #64748b;
    line-height: 2.2;
    text-align: left;
}}
.cover-meta span {{
    color: #0f172a;
    font-weight: 600;
}}
h1 {{
    color: #0f172a;
    font-size: 22px;
    border-bottom: 2px solid #2563eb;
    padding-bottom: 8px;
    margin-top: 36px;
    margin-bottom: 16px;
    font-weight: 700;
}}
h2 {{
    color: #1d4ed8;
    font-size: 17px;
    border-left: 4px solid #2563eb;
    padding-left: 10px;
    margin-top: 28px;
    margin-bottom: 12px;
    font-weight: 600;
}}
h3 {{
    color: #334155;
    font-size: 14.5px;
    margin-top: 20px;
    margin-bottom: 8px;
    font-weight: 600;
}}
table {{
    width: 100%;
    border-collapse: collapse;
    margin: 18px 0;
    font-size: 12.5px;
    page-break-inside: avoid;
}}
th {{
    background-color: #f8fafc;
    color: #0f172a;
    font-weight: 600;
    border: 1px solid #cbd5e1;
    padding: 9px 12px;
    text-align: left;
}}
td {{
    border: 1px solid #e2e8f0;
    padding: 8px 12px;
}}
tr:nth-child(even) {{
    background-color: #f8fafc;
}}
code {{
    background: #f1f5f9;
    color: #0369a1;
    padding: 2px 6px;
    border-radius: 4px;
    font-family: Consolas, "Courier New", monospace;
    font-size: 12px;
}}
pre {{
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 6px;
    padding: 12px;
    overflow-x: auto;
    font-family: Consolas, "Courier New", monospace;
    font-size: 12px;
    page-break-inside: avoid;
}}
blockquote {{
    border-left: 4px solid #3b82f6;
    background: #eff6ff;
    padding: 10px 16px;
    margin: 16px 0;
    border-radius: 0 6px 6px 0;
    color: #1e40af;
}}
ul, ol {{
    padding-left: 22px;
    margin-top: 6px;
    margin-bottom: 8px;
}}
li {{
    margin-bottom: 4px;
}}
hr {{
    border: none;
    border-top: 1px solid #e2e8f0;
    margin: 32px 0;
}}
p {{
    margin-top: 6px;
    margin-bottom: 8px;
}}
</style>
</head>
<body>
<div class="cover">
    <div class="cover-badge">官方产品使用指导书</div>
    <h1>AI未来实践教学平台</h1>
    <div class="subtitle">系统全功能用户操作手册</div>
    <div class="desc">学生端 · 教师端 · 实验管理端 · 微信小程序端</div>
    <div class="cover-meta">
        <div><span>面向对象：</span>院校实训中心主管 / 实验管理员 / 教师 / 学生</div>
        <div><span>适用系统：</span>AI未来实践教学平台 (IOEDU)</div>
        <div><span>手册版本：</span>v1.0 (正式交付版)</div>
        <div><span>发布日期：</span>2026年9月</div>
    </div>
</div>

{html_content}
</body>
</html>
"""

    with open(html_file, "w", encoding="utf-8") as f:
        f.write(html_doc)
    print(f"HTML 临时文件已生成: {html_file}")

    # 尝试调用 Chrome 或 Edge 转 PDF
    chrome_paths = [
        r"C:\Program Files\Google\Chrome\Application\chrome.exe",
        r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
        r"C:\Program Files\Microsoft\Edge\Application\msedge.exe"
    ]
    browser = None
    for p in chrome_paths:
        if os.path.exists(p):
            browser = p
            break

    if not browser:
        print("未找到 Chrome 或 Edge 浏览器，尝试生成 Word 或说明。")
        return

    print(f"使用浏览器生成 PDF: {browser}")
    html_url = html_file.as_uri()
    cmd = [
        browser,
        "--headless=new",
        "--disable-gpu",
        "--no-sandbox",
        "--run-all-compositor-stages-before-draw",
        f"--print-to-pdf={str(pdf_file)}",
        html_url
    ]

    try:
        res = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
        if pdf_file.exists() and pdf_file.stat().st_size > 0:
            print(f"SUCCESS: PDF 文件生成成功: {pdf_file} (大小: {pdf_file.stat().st_size} 字节)")
        else:
            print(f"PDF 文件未正常生成。命令输出: {res.stdout}, {res.stderr}")
    except Exception as e:
        print(f"调用浏览器生成 PDF 发生异常: {e}")

if __name__ == "__main__":
    main()
