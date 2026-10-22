#!/usr/bin/env node

/**
 * Compare OCR and Summary extraction quality between gemini-3.5-flash-lite and gemini-3.5-flash on 10 demo images.
 * NOTE: API Key passed via GEMINI_API_KEY env var or --key arg. No hardcoded keys.
 */

const fs = require('fs');
const path = require('path');

let apiKey = process.env.GEMINI_API_KEY;
const args = process.argv.slice(2);
for (let i = 0; i < args.length; i++) {
  if (args[i] === '--key' && args[i + 1]) {
    apiKey = args[i + 1];
    i++;
  }
}

if (!apiKey) {
  console.error('Lỗi: Thiếu GEMINI_API_KEY!');
  process.exit(1);
}

const IMAGES_DIR = path.join(__dirname, 'demo-images');
const BASE_URL = 'https://generativelanguage.googleapis.com/v1beta';

const CARD_SCHEMA = {
  type: 'OBJECT',
  properties: {
    title: { type: 'STRING' },
    summary: { type: 'STRING' },
    content_type: { type: 'STRING', enum: ['THEORY', 'PROBLEM', 'SOLUTION', 'CODE', 'NOTES', 'UNKNOWN'] },
    tags: {
      type: 'OBJECT',
      properties: {
        subject: { type: 'STRING' },
        topics: { type: 'ARRAY', items: { type: 'STRING' } }
      },
      required: ['subject', 'topics']
    },
    ocr_text: { type: 'STRING' }
  },
  required: ['title', 'summary', 'content_type', 'tags', 'ocr_text']
};

async function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function extractCard(modelId, imageBuffer, retries = 3) {
  const base64Data = imageBuffer.toString('base64');
  const body = {
    contents: [
      {
        parts: [
          {
            inlineData: {
              mimeType: 'image/png',
              data: base64Data
            }
          },
          {
            text: 'Hãy đọc toàn bộ văn bản trong ảnh chụp màn hình bài học này, trích xuất cấu trúc thẻ ghi nhớ theo schema: giữ nguyên công thức toán lý, định lý, tên thuật ngữ.'
          }
        ]
      }
    ],
    generationConfig: {
      responseMimeType: 'application/json',
      responseSchema: CARD_SCHEMA
    }
  };

  for (let attempt = 1; attempt <= retries; attempt++) {
    const start = Date.now();
    try {
      const res = await fetch(`${BASE_URL}/models/${modelId}:generateContent`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-goog-api-key': apiKey
        },
        body: JSON.stringify(body)
      });

      const elapsed = Date.now() - start;

      if (res.status === 429) {
        console.warn(`[${modelId}] Rate limited (429), chờ 15s trước khi thử lại (lần ${attempt}/${retries})...`);
        await sleep(15000);
        continue;
      }

      if (!res.ok) {
        const text = await res.text();
        return { success: false, status: res.status, error: text, latency: elapsed };
      }

      const data = await res.json();
      const rawText = data.candidates?.[0]?.content?.parts?.[0]?.text;
      const parsed = JSON.parse(rawText);
      return { success: true, parsed, latency: elapsed };
    } catch (err) {
      if (attempt === retries) {
        return { success: false, error: err.message, latency: Date.now() - start };
      }
      await sleep(3000);
    }
  }
}

async function main() {
  const files = fs.readdirSync(IMAGES_DIR).filter(f => f.endsWith('.png')).sort();
  console.log(`Tìm thấy ${files.length} ảnh demo trong ${IMAGES_DIR}. Bắt đầu so sánh trích xuất...\n`);

  const comparisons = [];

  for (const file of files) {
    const imgPath = path.join(IMAGES_DIR, file);
    const buf = fs.readFileSync(imgPath);

    console.log(`\n============================================================`);
    console.log(`Đang phân tích tệp: ${file}`);
    console.log(`============================================================`);

    // 1. Run gemini-3.5-flash-lite
    process.stdout.write(`  [1/2] Đang gọi gemini-3.5-flash-lite... `);
    const liteRes = await extractCard('gemini-3.5-flash-lite', buf);
    if (liteRes.success) {
      console.log(`OK (${liteRes.latency}ms) - Title: "${liteRes.parsed.title}"`);
    } else {
      console.log(`FAILED (${liteRes.latency}ms) - ${liteRes.error}`);
    }

    // Delay 3s between models to respect rate limits
    await sleep(3000);

    // 2. Run gemini-3.5-flash
    process.stdout.write(`  [2/2] Đang gọi gemini-3.5-flash... `);
    const flashRes = await extractCard('gemini-3.5-flash', buf);
    if (flashRes.success) {
      console.log(`OK (${flashRes.latency}ms) - Title: "${flashRes.parsed.title}"`);
    } else {
      console.log(`FAILED (${flashRes.latency}ms) - ${flashRes.error}`);
    }

    comparisons.push({
      file,
      lite: liteRes,
      flash: flashRes
    });

    // Delay 5s between images to avoid 429
    await sleep(5000);
  }

  // Summary
  console.log(`\n============================ BẢNG SO SÁNH CHẤT LƯỢNG (10 ẢNH DEMO) ============================`);
  const summaryTable = comparisons.map(c => ({
    Image: c.file,
    'Lite Latency': c.lite.success ? `${c.lite.latency}ms` : 'FAIL',
    'Flash Latency': c.flash.success ? `${c.flash.latency}ms` : 'FAIL',
    'Lite OCR Length': c.lite.success ? c.lite.parsed.ocr_text.length : 0,
    'Flash OCR Length': c.flash.success ? c.flash.parsed.ocr_text.length : 0,
    'Lite Type': c.lite.success ? c.lite.parsed.content_type : 'N/A',
    'Flash Type': c.flash.success ? c.flash.parsed.content_type : 'N/A'
  }));
  console.table(summaryTable);

  const outPath = path.join(__dirname, 'vision-comparison-results.json');
  fs.writeFileSync(outPath, JSON.stringify(comparisons, null, 2), 'utf8');
  console.log(`\nChi tiết kết quả so sánh đã được lưu tại: ${outPath}`);
}

main().catch(console.error);
