package com.example.udtbe.domain.admin.dto.request;

import jakarta.validation.constraints.Size;

public record AdminCastUpdateRequest(
        @Size(max = 100, message = "출연진 이름은 최대 100자입니다.")
        String castName,
        String castImageUrl
) {

}
