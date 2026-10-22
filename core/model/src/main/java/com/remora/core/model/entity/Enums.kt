package com.remora.core.model.entity

import kotlinx.serialization.Serializable

@Serializable
enum class ProcessingStatus {
    PENDING,          // Vừa chụp, đang chờ xử lý
    MLKIT_OCR_DONE,   // Đã xong OCR nhanh trên máy (hiện thẻ ngay lập tức)
    GEMINI_ANALYZED,  // Đã phân tích xong qua Gemini (tóm tắt, môn học, tag)
    READY,            // Đã embed vector và sẵn sàng tìm kiếm / resurface
    FAILED            // Xử lý gặp lỗi (cho phép người dùng bấm thử lại)
}

@Serializable
enum class ContentType {
    THEORY,      // Lý thuyết / Slide bài giảng
    PROBLEM,     // Đề bài tập / Câu hỏi ôn tập
    SOLUTION,    // Bài giải mẫu / Hướng dẫn giải
    CODE,        // Đoạn mã nguồn / Lập trình
    NOTES,       // Ghi chú viết tay / Bảng viết
    UNKNOWN      // Chưa xác định
}
