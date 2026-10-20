package com.example.loandocs.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanDto {
    private Long id;
    private String applicationNumber;

    @NotBlank(message = "Loan type is required")
    private String loanType;

    @NotNull(message = "Requested amount is required")
    @DecimalMin(value = "100.00", message = "Minimum requested amount is 100.00")
    private BigDecimal requestedAmount;

    private BigDecimal approvedAmount;
    private BigDecimal interestRate;

    @NotNull(message = "Tenure is required")
    @Min(value = 1, message = "Tenure must be at least 1 month")
    private Integer tenureMonths;

    private String status;
    private String applicantName;
    private String officerName;
}
