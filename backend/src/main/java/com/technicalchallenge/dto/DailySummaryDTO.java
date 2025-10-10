package com.technicalchallenge.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;
import java.time.LocalDate;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DailySummaryDTO {

    // --- Today's Metrics ---
    private LocalDate reportDate = LocalDate.now();
    private Long todaysTradeCount;
    private BigDecimal todaysTotalNotionalUSD;

    // --- Performance and Comparison ---
    // User-specific performance metrics
    private BigDecimal dailyRealizedPL; 
    // Historical comparison
    private BigDecimal vsYesterdayTradeCountChange;
    private BigDecimal vsYesterdayNotionalChangePercent; 

    // --- User and Book Activity ---
    private Long traderId;
    // Map of Book Name to the number of trades executed today in that book
    // Book-level activity summaries
    private Map<String, Long> bookActivitySummary; 
}
