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

    public TradeSummaryDTO(Map<String, BigDecimal> totalNotionalByCurrency, BigDecimal portfolioMTM, BigDecimal totalVaR) {
        this.totalNotionalByCurrency = totalNotionalByCurrency;
        this.portfolioMTM = portfolioMTM;
        this.totalVaR = totalVaR;
        
        // Initialize Maps to prevent NullPointerExceptions in the service layer if not set
        this.tradeCountByStatus = Map.of();
        this.tradeCountByType = Map.of();
        this.tradeCountByCounterparty = Map.of();
    }
}
