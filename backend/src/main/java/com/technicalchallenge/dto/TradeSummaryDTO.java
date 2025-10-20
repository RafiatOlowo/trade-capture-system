package com.technicalchallenge.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TradeSummaryDTO {
    // Total number of trades by status
    private Map<String, Long> tradeCountByStatus;

    // Total notional amounts by currency 
    private Map<String, BigDecimal> totalNotionalByCurrency;

    // Breakdown by trade type
    private Map<String, Long> tradeCountByType;

    // Breakdown by top counterparties
    private Map<String, Long> tradeCountByCounterparty;

    // Risk exposure summaries
    // Value at Risk
    private BigDecimal totalVaR; 
    
    // Mark-to-Market (P/L)
    private BigDecimal portfolioMTM;
}
