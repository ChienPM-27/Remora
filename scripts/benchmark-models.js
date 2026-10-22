#!/usr/bin/env node

/**
 * Benchmark script for Gemini models (Latency, P50, P95, and JSON conformance)
 * NOTE: Never hardcode API keys in this script. Pass via GEMINI_API_KEY env var or --key arg.
 *
 * Usage:
 *   GEMINI_API_KEY="your_key" node scripts/benchmark-models.js [--n 10]
 */

const fs = require('fs');
const path = require('path');

// Extract API key from environment variable or command line argument
let apiKey = process.env.GEMINI_API_KEY;
const args = process.argv.slice(2);
for (let i = 0; i < args.length; i++) {
  if (args[i] === '--key' && args[i + 1]) {
    apiKey = args[i + 1];
    i++;
  }
}

if (!apiKey) {
  console.error('Lỗi: Thiếu API key! Vui lòng đặt biến môi trường GEMINI_API_KEY hoặc truyền --key <API_KEY>');
  process.exit(1);
}

const N = parseInt(process.env.BENCHMARK_N || '10', 10);
const BASE_URL = 'https://generativelanguage.googleapis.com/v1beta';

function percentile(arr, p) {
  if (arr.length === 0) return 0;
  const sorted = [...arr].sort((a, b) => a - b);
  const index = Math.ceil((p / 100) * sorted.length) - 1;
  return sorted[Math.max(0, index)];
}

async function benchmarkModel(modelId, type = 'generateContent') {
  console.log(`\n--- Bắt đầu đo kiểm ${modelId} (${type}) với N=${N} lần gọi ---`);
  const latencies = [];
  const errors = [];

  for (let i = 1; i <= N; i++) {
    const start = Date.now();
    try {
      let endpoint = `${BASE_URL}/models/${modelId}:${type}`;
      let body;

      if (type === 'embedContent') {
        body = {
          content: { parts: [{ text: 'Định lý Thales và tam giác đồng dạng môn Toán 8' }] },
          outputDimensionality: 768,
          taskType: 'RETRIEVAL_DOCUMENT'
        };
      } else {
        body = {
          contents: [{ parts: [{ text: 'Trích xuất bài học: Định lý Pythagoras trong tam giác vuông: a^2 + b^2 = c^2.' }] }],
          generationConfig: {
            responseMimeType: 'application/json',
            responseSchema: {
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
            }
          }
        };
      }

      const res = await fetch(endpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-goog-api-key': apiKey
        },
        body: JSON.stringify(body)
      });

      const elapsed = Date.now() - start;

      if (!res.ok) {
        const errText = await res.text();
        errors.push({ iteration: i, status: res.status, error: errText });
        process.stdout.write(` [x${res.status}]`);
      } else {
        const data = await res.json();
        latencies.push(elapsed);
        process.stdout.write(` [${elapsed}ms]`);
      }
    } catch (err) {
      const elapsed = Date.now() - start;
      errors.push({ iteration: i, error: err.message });
      process.stdout.write(` [Err]`);
    }
  }

  process.stdout.write('\n');

  const p50 = percentile(latencies, 50);
  const p95 = percentile(latencies, 95);
  const min = latencies.length ? Math.min(...latencies) : 0;
  const max = latencies.length ? Math.max(...latencies) : 0;
  const avg = latencies.length ? Math.round(latencies.reduce((a, b) => a + b, 0) / latencies.length) : 0;

  return {
    modelId,
    type,
    total: N,
    success: latencies.length,
    failed: errors.length,
    p50,
    p95,
    min,
    max,
    avg,
    errors
  };
}

async function main() {
  console.log(`======================================================================`);
  console.log(`REMORA AI BENCHMARK SUITE`);
  console.log(`Ngày đo: ${new Date().toISOString().split('T')[0]}`);
  console.log(`Endpoint: ${BASE_URL}`);
  console.log(`Số lần đo mỗi model: N=${N}`);
  console.log(`======================================================================`);

  const results = [];
  results.push(await benchmarkModel('gemini-3.5-flash-lite', 'generateContent'));
  results.push(await benchmarkModel('gemini-3.5-flash', 'generateContent'));
  results.push(await benchmarkModel('gemini-embedding-2', 'embedContent'));

  console.log(`\n============================ BẢNG TỔNG HỢP ============================`);
  console.table(results.map(r => ({
    Model: r.modelId,
    Type: r.type,
    SuccessRate: `${r.success}/${r.total} (${Math.round((r.success / r.total) * 100)}%)`,
    'P50 (ms)': r.p50,
    'P95 (ms)': r.p95,
    'Min (ms)': r.min,
    'Max (ms)': r.max,
    'Avg (ms)': r.avg
  })));

  // Write results to temporary output file for decisions.md inclusion
  const outPath = path.join(__dirname, 'benchmark-results.json');
  fs.writeFileSync(outPath, JSON.stringify(results, null, 2), 'utf8');
  console.log(`Kết quả chi tiết đã được ghi vào: ${outPath}`);
}

main().catch(err => {
  console.error('Fatal error:', err);
  process.exit(1);
});
