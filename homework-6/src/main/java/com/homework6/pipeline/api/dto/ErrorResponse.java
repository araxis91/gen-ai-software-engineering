package com.homework6.pipeline.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A machine-readable error body -- never a raw stack trace")
public record ErrorResponse(
        @Schema(example = "TRANSACTION_NOT_FOUND") String code,
        @Schema(example = "No result found for transaction 'TXN999'") String message
) {
}
