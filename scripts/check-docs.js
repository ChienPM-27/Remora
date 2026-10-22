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

const mdFiles = getMarkdownFiles(DOCS_DIR);
let errors = 0;

console.log(`Đang kiểm tra Front-Matter trên ${mdFiles.length} tệp tài liệu trong docs/...`);

for (const file of mdFiles) {
  const relPath = path.relative(path.join(__dirname, '..'), file);
  // Bỏ qua các file tham chiếu hoặc template đặc biệt nếu cần
  if (relPath.includes('0000-template.md')) continue;

  const err = checkFrontMatter(file);
  if (err) {
    console.error(`❌ [FAIL] ${relPath}: ${err}`);
    errors++;
  } else {
    console.log(`✅ [PASS] ${relPath}`);
  }
}

if (errors > 0) {
  console.error(`\nPhát hiện ${errors} lỗi front-matter! Vui lòng khắc phục trước khi commit.`);
  process.exit(1);
} else {
  console.log(`\n🎉 Toàn bộ ${mdFiles.length - 1} tệp tài liệu đều có đầy đủ front-matter hợp lệ!`);
  process.exit(0);
}
