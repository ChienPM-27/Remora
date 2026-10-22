package com.remora.core.model.entity

import kotlinx.serialization.Serializable

@Serializable
enum class ProcessingStatus {
    PENDING,          // Vừa tạo, đang chờ xử lý AI ngầm
    DONE,             // Đã trích xuất thông tin và vector embedding thành công
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

@Serializable
enum class ResurfaceGroup {
    SIMILAR,         // Tương đồng (ôn tập củng cố)
    COMPLEMENTARY,   // Bổ trợ (lý thuyết - bài tập, liên môn)
    ACTIONABLE       // Cần hành động (phát hiện mâu thuẫn, bài tập cần làm)
}

@Serializable
enum class ResurfaceRelation {
    DUPLICATE,       // Trùng lặp nội dung
    SUPPLEMENT,      // Bổ sung kiến thức còn thiếu
    EXPLANATION,     // Giải thích lý do hoặc chứng minh
    CONTRADICTION    // Mâu thuẫn giả định hoặc đối nghịch
}
