package com.technicalchallenge.dto;

import lombok.Data;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Schema(description = "Request body for trade settlement instructions")
public class SettlementInstructionsDTO {
    
    @Size(min = 10, max = 500, message = "Settlement instructions must be between 10 and 500 characters if provided.")
    @Schema(description = "The new settlement instruction text for the trade.", example = "New Instructions: Pay Bank of America, Account XYZ")
    private String instructions;
}