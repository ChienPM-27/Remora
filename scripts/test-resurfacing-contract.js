#!/usr/bin/env node

/**
 * Resurfacing Contract & Prompt Conformance Test
 * Verifies:
 * 1. candidate_id is INTEGER
 * 2. 10-20 candidates
 * 3. Negative case returns empty results: []
 * 4. High threshold: confidence > 0.80
 * 5. Relation strictly matches Group
 *
 * NOTE: API key passed via GEMINI_API_KEY env var or --key arg. No hardcoded keys.
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

const BASE_URL = 'https://generativelanguage.googleapis.com/v1beta';

const RESURFACE_RESPONSE_SCHEMA = {
  type: 'OBJECT',
  properties: {
    results: {
      type: 'ARRAY',
      items: {
        type: 'OBJECT',
        properties: {
          candidate_id: { type: 'INTEGER' },
          group: { type: 'STRING', enum: ['SIMILAR', 'COMPLEMENTARY', 'ACTIONABLE'] },
          relation: { type: 'STRING', enum: ['DUPLICATE', 'SUPPLEMENT', 'EXPLANATION', 'CONTRADICTION'] },
          new_info: { type: 'STRING' },
          benefit: { type: 'STRING' },
          reason: { type: 'STRING' },
          confidence: { type: 'NUMBER' }
        },
        required: ['candidate_id', 'group', 'relation', 'new_info', 'benefit', 'reason', 'confidence']
      }
    }
  },
  required: ['results']
};

const RESURFACE_SYSTEM_PROMPT = `Bạn là trợ lý AI chuyên về Memory Resurfacing (gợi nhớ tri thức học tập liên quan) của Remora.
NHIỆM VỤ: So sánh một thẻ kiến thức mới với danh sách các thẻ ứng viên (10-20 thẻ) để tìm ra liên kết có giá trị học tập thực sự.

QUY TẮC RÀNG BUỘC CỐT LÕI:
1. CHỈ TRẢ VỀ khi có LỢI ÍCH HỌC TẬP CỤ THỂ (nhắc lại tiên đề cần nhớ, bài tập rèn luyện tương ứng, hoặc cảnh báo mâu thuẫn/khác biệt công thức).
2. TUYỆT ĐỐI KHÔNG GƯỢNG ÉP LIÊN KẾT: Nếu các thẻ ứng viên không cùng chủ đề, không bổ trợ thực tế hoặc không mang lại giá trị học tập, KHÔNG ĐƯỢC trả về.
3. CA ÂM TÍNH: Nếu không có thẻ nào thực sự liên quan, BẮT BUỘC trả về danh sách rỗng: {"results": []}.
4. QUY TẮC KHỚP RELATION VÀ GROUP:
   - Nhóm SIMILAR: relation BẮT BUỘC là DUPLICATE (ảnh chụp trùng bài giảng) hoặc SUPPLEMENT (cùng chuyên đề, cùng dạng).
   - Nhóm COMPLEMENTARY: relation BẮT BUỘC là EXPLANATION (bài giải giải thích cho đề bài, hoặc lý thuyết nền) hoặc SUPPLEMENT (mở rộng hệ quả, khía cạnh bổ sung).
   - Nhóm ACTIONABLE: relation BẮT BUỘC là SUPPLEMENT (bài tập thực hành cho lý thuyết vừa học) hoặc CONTRADICTION (chỉ ra công thức trái ngược/điều kiện áp dụng khác nhau).
5. candidate_id BẮT BUỘC là số nguyên (INTEGER) khớp chính xác với candidate_id trong danh sách đầu vào. Không được tự bịa ID.
6. Mỗi group tối đa 2 thẻ.
7. Đánh giá confidence trung thực từ 0.0 đến 1.0. Chỉ gợi ý khi confidence > 0.80.`;

// 15 candidates dataset
const CANDIDATES_15 = [
  { candidate_id: 101, title: 'Hệ quả định lý Thales', summary: 'Đường thẳng song song với 1 cạnh tạo ra tam giác mới có 3 cạnh tương ứng tỉ lệ.', tags: { subject: 'Toán học', topics: ['Hình học', 'Thales', 'Tam giác đồng dạng'] }, content_type: 'THEORY' },
  { candidate_id: 102, title: 'Bài tập: Tính độ dài đoạn thẳng bằng định lý Thales', summary: 'Cho tam giác ABC, DE // BC, tính độ dài đoạn DE biết AD=2, AB=5, BC=10.', tags: { subject: 'Toán học', topics: ['Hình học', 'Bài tập Thales'] }, content_type: 'PROBLEM' },
  { candidate_id: 103, title: 'Định luật II Newton', summary: 'F = m*a, gia tốc tỉ lệ thuận với lực tác dụng và tỉ lệ nghịch với khối lượng.', tags: { subject: 'Vật lý', topics: ['Động lực học'] }, content_type: 'THEORY' },
  { candidate_id: 104, title: 'Bảng đạo hàm cơ bản', summary: 'Đạo hàm x^n, sin x, cos x, e^x và quy tắc chuỗi hàm hợp.', tags: { subject: 'Giải tích', topics: ['Đạo hàm'] }, content_type: 'THEORY' },
  { candidate_id: 105, title: 'Phân phối chuẩn Gauss', summary: 'Hàm mật độ xác suất f(x) hình chuông đối xứng và kỹ thuật đổi biến Z-Score.', tags: { subject: 'Xác suất thống kê', topics: ['Phân phối chuẩn'] }, content_type: 'THEORY' },
  { candidate_id: 106, title: 'Tìm kiếm nhị phân Binary Search', summary: 'Thuật toán tìm kiếm O(log N) trên mảng đã sắp xếp.', tags: { subject: 'Tin học', topics: ['Giải thuật', 'Tìm kiếm'] }, content_type: 'CODE' },
  { candidate_id: 107, title: 'Định luật tuần hoàn Men-đê-lê-ép', summary: 'Tính chất các nguyên tố biến đổi tuần hoàn theo điện tích hạt nhân.', tags: { subject: 'Hóa học', topics: ['Bảng tuần hoàn'] }, content_type: 'THEORY' },
  { candidate_id: 108, title: 'Thuật toán sắp xếp QuickSort', summary: 'Phân hoạch mảng quanh pivot và đệ quy, độ phức tạp trung bình O(N log N).', tags: { subject: 'Tin học', topics: ['Giải thuật', 'Sắp xếp'] }, content_type: 'CODE' },
  { candidate_id: 109, title: 'Phép nhân ma trận & định thức', summary: 'Điều kiện nhân ma trận và cách tính định thức ma trận vuông.', tags: { subject: 'Đại số tuyến tính', topics: ['Ma trận'] }, content_type: 'THEORY' },
  { candidate_id: 110, title: 'Cấu trúc xoắn kép phân tử ADN', summary: 'Mô hình Watson-Crick, nguyên tắc bổ sung A-T (2 lk H), G-X (3 lk H).', tags: { subject: 'Sinh học', topics: ['Di truyền', 'ADN'] }, content_type: 'THEORY' },
  { candidate_id: 111, title: 'Định luật bảo toàn năng lượng', summary: 'Năng lượng không tự nhiên sinh ra hay mất đi, cơ năng E = Wd + Wt.', tags: { subject: 'Vật lý', topics: ['Cơ học'] }, content_type: 'THEORY' },
  { candidate_id: 112, title: 'Tích phân từng phần', summary: 'Công thức int(u dv) = u*v - int(v du) trong giải tích tích phân.', tags: { subject: 'Giải tích', topics: ['Tích phân'] }, content_type: 'THEORY' },
  { candidate_id: 113, title: 'Phản ứng Este hóa', summary: 'Phản ứng giữa axit cacboxylic và ancol tạo este và nước xúc tác H2SO4 đặc.', tags: { subject: 'Hóa học', topics: ['Hóa hữu cơ'] }, content_type: 'THEORY' },
  { candidate_id: 114, title: 'Định luật Ohm cho toàn mạch', summary: 'Cường độ dòng điện I = E / (R + r), định luật Jun-Len-xơ.', tags: { subject: 'Vật lý', topics: ['Điện học'] }, content_type: 'THEORY' },
  { candidate_id: 115, title: 'Quá trình Nguyên phân', summary: 'Các kỳ phân bào: kỳ đầu, kỳ giữa, kỳ sau, kỳ cuối, duy trì bộ NST 2n.', tags: { subject: 'Sinh học', topics: ['Tế bào'] }, content_type: 'THEORY' }
];

async function callResurfacing(modelId, newCard, candidates) {
  const promptText = `${RESURFACE_SYSTEM_PROMPT}

THẺ MỚI VỪA LƯU:
${JSON.stringify(newCard, null, 2)}

DANH SÁCH ${candidates.length} ỨNG VIÊN CẦN ĐÁNH GIÁ:
${JSON.stringify(candidates, null, 2)}

Hãy xuất JSON theo đúng schema.`;

  const body = {
    contents: [{ parts: [{ text: promptText }] }],
    generationConfig: {
      responseMimeType: 'application/json',
      responseSchema: RESURFACE_RESPONSE_SCHEMA
    }
  };

  const start = Date.now();
  const res = await fetch(`${BASE_URL}/models/${modelId}:generateContent`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'x-goog-api-key': apiKey
    },
    body: JSON.stringify(body)
  });

  const latency = Date.now() - start;

  if (!res.ok) {
    const errText = await res.text();
    return { success: false, status: res.status, error: errText, latency };
  }

  const data = await res.json();
  const rawText = data.candidates?.[0]?.content?.parts?.[0]?.text;
  const parsed = JSON.parse(rawText);
  return { success: true, parsed, latency };
}

async function main() {
  console.log(`======================================================================`);
  console.log(`REMORA RESURFACING CONTRACT & REASONING EVALUATION`);
  console.log(`Kiểm thử: candidate_id (integer), 15 ứng viên, ca âm tính, rule relation-group`);
  console.log(`======================================================================\n`);

  // Positive Test Card: Thales Theorem (Related to 101 and 102)
  const positiveCard = {
    title: 'Định lý Thales trong tam giác',
    summary: 'Nếu một đường thẳng song song với một cạnh của tam giác và cắt hai cạnh còn lại thì nó định ra trên hai cạnh đó những đoạn thẳng tương ứng tỉ lệ.',
    tags: { subject: 'Toán học', topics: ['Hình học', 'Thales', 'Tam giác'] },
    content_type: 'THEORY',
    user_intent: 'Học bài mới trên lớp'
  };

  // Negative Test Card: English Grammar (Zero related candidates in math/physics/chemistry pool)
  const negativeCard = {
    title: 'Ngữ pháp Tiếng Anh: Thì Hiện Tại Hoàn Thành (Present Perfect)',
    summary: 'Cấu trúc S + have/has + V3/ed. Dùng để diễn tả hành động đã xảy ra trong quá khứ nhưng kết quả còn lưu ở hiện tại hoặc hành động vừa mới xảy ra.',
    tags: { subject: 'Tiếng Anh', topics: ['Ngữ pháp', 'Thì', 'Present Perfect'] },
    content_type: 'THEORY',
    user_intent: 'Ôn tập ngữ pháp tiếng Anh'
  };

  const testCases = [
    { name: 'Ca Dương Tính (Thales -> liên quan 101, 102)', card: positiveCard, expectEmpty: false },
    { name: 'Ca Âm Tính (Tiếng Anh -> không liên quan 15 thẻ KHTN)', card: negativeCard, expectEmpty: true }
  ];

  const models = ['gemini-3.5-flash-lite', 'gemini-3.5-flash'];
  const testResults = [];

  for (const model of models) {
    console.log(`\n------------------------------------------------------------`);
    console.log(`Đang chạy kiểm thử trên Model: ${model}`);
    console.log(`------------------------------------------------------------`);

    for (const tc of testCases) {
      console.log(`\n[Test Case] ${tc.name}`);
      const res = await callResurfacing(model, tc.card, CANDIDATES_15);

      if (!res.success) {
        console.error(`  ❌ Thất bại: HTTP ${res.status} - ${res.error}`);
        testResults.push({ model, test: tc.name, success: false, latency: res.latency, error: res.error });
        continue;
      }

      console.log(`  ✅ Phản hồi thành công (${res.latency}ms)`);
      const results = res.parsed.results || [];
      console.log(`  Số lượng gợi ý: ${results.length}`);

      let integerIdsValid = true;
      let relationGroupValid = true;
      let thresholdCompliant = true;

      for (const item of results) {
        // Verify candidate_id is integer
        if (!Number.isInteger(item.candidate_id)) {
          integerIdsValid = false;
        }

        // Verify relation matches group
        if (item.group === 'SIMILAR' && !['DUPLICATE', 'SUPPLEMENT'].includes(item.relation)) {
          relationGroupValid = false;
        }
        if (item.group === 'COMPLEMENTARY' && !['EXPLANATION', 'SUPPLEMENT'].includes(item.relation)) {
          relationGroupValid = false;
        }
        if (item.group === 'ACTIONABLE' && !['SUPPLEMENT', 'CONTRADICTION'].includes(item.relation)) {
          relationGroupValid = false;
        }

        // Check confidence threshold (> 0.80)
        if (item.confidence <= 0.80) {
          thresholdCompliant = false;
        }

        console.log(`    -> ID: ${item.candidate_id} (${typeof item.candidate_id}) | Group: ${item.group} | Relation: ${item.relation} | Conf: ${item.confidence}`);
        console.log(`       Lý do: "${item.reason}"`);
      }

      const emptyAsExpected = tc.expectEmpty ? results.length === 0 : results.length > 0;
      console.log(`  [Kiểm tra] Integer ID: ${integerIdsValid ? 'PASS' : 'FAIL'} | Relation-Group Match: ${relationGroupValid ? 'PASS' : 'FAIL'} | Confidence > 0.80: ${thresholdCompliant ? 'PASS' : 'FAIL'} | Kết quả rỗng đúng kỳ vọng: ${emptyAsExpected ? 'PASS' : 'FAIL'}`);

      testResults.push({
        model,
        test: tc.name,
        success: true,
        latency: res.latency,
        count: results.length,
        integerIdsValid,
        relationGroupValid,
        thresholdCompliant,
        emptyAsExpected,
        results
      });

      // Brief delay between calls
      await new Promise(r => setTimeout(r, 4000));
    }
  }

  const outPath = path.join(__dirname, 'resurfacing-contract-results.json');
  fs.writeFileSync(outPath, JSON.stringify(testResults, null, 2), 'utf8');
  console.log(`\nToàn bộ kết quả test Resurfacing đã được lưu tại: ${outPath}`);
}

main().catch(console.error);
