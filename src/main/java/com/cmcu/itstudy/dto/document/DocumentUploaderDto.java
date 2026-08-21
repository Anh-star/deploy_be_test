package com.cmcu.itstudy.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploaderDto {
    private String id;
    private String fullName;
    /** Thứ hạng cao nhất trong top 10 (1-10) ở bất kỳ bảng xếp hạng nào. Null nếu ngoài top 10. */
    private Integer bestRank;
    /** Tên bảng xếp hạng tương ứng với bestRank: "views", "freeDownloads", "paidDownloads". */
    private String bestRankCategory;
    /** true nếu tổng lượt tải >= 50 HOẶC tổng lượt xem >= 100 (dù ngoài top 10). */
    private Boolean verified;
}
