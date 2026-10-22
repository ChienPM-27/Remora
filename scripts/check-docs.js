#!/usr/bin/env node

/**
 * Script kiểm tra tính hiện diện của Front-Matter trong toàn bộ các tệp Markdown thuộc docs/
 */

const fs = require('fs');
const path = require('path');

const DOCS_DIR = path.join(__dirname, '..', 'docs');
const REQUIRED_FIELDS = ['title', 'status', 'owner', 'last_verified', 'related_code'];

function getMarkdownFiles(dir, fileList = []) {
  const files = fs.readdirSync(dir);
  for (const file of files) {
    const filePath = path.join(dir, file);
    const stat = fs.statSync(filePath);
    if (stat.isDirectory()) {
      getMarkdownFiles(filePath, fileList);
    } else if (file.endsWith('.md')) {
      fileList.push(filePath);
    }
  }
  return fileList;
}

function checkFrontMatter(filePath) {
  const content = fs.readFileSync(filePath, 'utf8');
  if (!content.startsWith('---')) {
    return `Thiếu front-matter mở đầu ('---')`;
  }

  const endIdx = content.indexOf('\n---', 3);
  if (endIdx === -1) {
    return `Thiếu thẻ đóng front-matter ('---')`;
  }

  const frontMatterContent = content.substring(3, endIdx);
  const missing = [];
  for (const field of REQUIRED_FIELDS) {
    if (!new RegExp(`^${field}:`, 'm').test(frontMatterContent)) {
      missing.push(field);
    }
  }

  if (missing.length > 0) {
    return `Thiếu các trường bắt buộc trong front-matter: ${missing.join(', ')}`;
  }

  return null;
}

function checkLinksAndLocalPaths(filePath) {
  const content = fs.readFileSync(filePath, 'utf8');
  const issues = [];

  // Kiểm tra liên kết tuyệt đối file:///
  if (/file:\/\/\//i.test(content)) {
    issues.push("Chứa liên kết giao thức cục bộ file:///");
  }

  // Kiểm tra đường dẫn ổ đĩa tuyệt đối máy cá nhân (C:\, D:\, /Users/, /home/username/)
  const localDriveRegex = /(?:(?<=[^a-zA-Z0-9]|^)[a-zA-Z]:[\\\/]|\/Users\/[a-zA-Z0-9_-]+|\/home\/[a-zA-Z0-9_-]+)/;
  if (localDriveRegex.test(content)) {
    issues.push("Chứa đường dẫn thư mục máy cục bộ");
  }

  // Kiểm tra các relative markdown links
  const linkRegex = /\[([^\]]+)\]\(([^)]+)\)/g;
  let match;
  while ((match = linkRegex.exec(content)) !== null) {
    const rawLink = match[2].trim();
    if (rawLink.startsWith('http://') || rawLink.startsWith('https://') || rawLink.startsWith('mailto:') || rawLink.startsWith('#')) {
      continue;
    }
    const cleanLink = rawLink.split('#')[0];
    if (!cleanLink) continue;

    const targetPath = path.resolve(path.dirname(filePath), cleanLink);
    if (!fs.existsSync(targetPath)) {
      issues.push(`Broken link: "${rawLink}" -> không tìm thấy tệp tại ${path.relative(path.join(__dirname, '..'), targetPath)}`);
    }
  }

  return issues.length > 0 ? issues.join('; ') : null;
}

const mdFiles = getMarkdownFiles(DOCS_DIR);
let errors = 0;

console.log(`Đang kiểm tra Front-Matter & Liên kết trên ${mdFiles.length} tệp tài liệu trong docs/...`);

for (const file of mdFiles) {
  const relPath = path.relative(path.join(__dirname, '..'), file);
  // Bỏ qua các file tham chiếu hoặc template đặc biệt nếu cần
  if (relPath.includes('0000-template.md')) continue;

  const fmErr = checkFrontMatter(file);
  const linkErr = checkLinksAndLocalPaths(file);

  if (fmErr || linkErr) {
    const errMessages = [fmErr, linkErr].filter(Boolean).join(' | ');
    console.error(`❌ [FAIL] ${relPath}: ${errMessages}`);
    errors++;
  } else {
    console.log(`✅ [PASS] ${relPath}`);
  }
}

if (errors > 0) {
  console.error(`\nPhát hiện ${errors} lỗi tài liệu! Vui lòng khắc phục trước khi commit.`);
  process.exit(1);
} else {
  console.log(`\n🎉 Toàn bộ ${mdFiles.length - 1} tệp tài liệu đều hợp lệ (Front-matter chuẩn, không link cục bộ, mọi relative link đều tồn tại)!`);
  process.exit(0);
}
