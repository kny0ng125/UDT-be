package com.example.udtbe.domain.admin.dto.request;

import jakarta.validation.constraints.Size;

public record AdminDirectorUpdateRequest(
        @Size(max = 100, message = "감독 이름은 최대 100자입니다.")
        String directorName,
        String directorImageUrl
) {

}
