package com.a304.wildworker.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@Builder
@AllArgsConstructor
public class StationRankResponse {

    List<StationRankInfoResponse> ranking;
    String orderBy;
}
