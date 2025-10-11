package com.technicalchallenge.service;

import com.technicalchallenge.dto.DailySummaryDTO;
import com.technicalchallenge.dto.TradeSummaryDTO;
import com.technicalchallenge.dto.TradeDTO;
import com.technicalchallenge.model.Trade; 
import com.technicalchallenge.mapper.TradeMapper;
import com.technicalchallenge.repository.TradeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service responsible for fetching, filtering, and aggregating trade data 
 * to power the Trader Dashboard and Blotter views.
 */
@Service
public class TraderDashboardService {

    @Autowired
    private TradeRepository tradeRepository; 

    @Autowired
    private TradeMapper tradeMapper;

    // --- Blotter Functions ---

    /**
     * Retrieves a paginated list of trades for the specified trader.
     */
    public Page<TradeDTO> getMyTrades(Long traderUserId, Pageable pageable) {
        Page<Trade> tradePage = tradeRepository.findByTraderUserId(traderUserId, pageable);
        return tradePage.map(tradeMapper::toDto);
    }

    /**
     * Retrieves a paginated list of trades for a specific book.
     */
    public Page<TradeDTO> getBookTrades(Long traderUserId, Long bookId, Pageable pageable) {
        Page<Trade> tradePage = tradeRepository.findByTraderUserIdAndBookId(traderUserId, bookId, pageable);
        return tradePage.map(tradeMapper::toDto);
    }

    // --- Summary & Analytics Functions ---
    
    /**
     * Generates a high-level portfolio summary (e.g., VaR, MTM, Notional exposure).
     */
    public TradeSummaryDTO getPortfolioSummary(Long traderUserId) {
        // Use the list of active statuses for filtering
        // A trade is relevant for summary/risk calculations if it's currently Live, New, or Amended.
        List<String> activeStatuses = List.of("LIVE", "NEW", "AMENDED");
        
        List<Trade> trades = tradeRepository.findByTraderUser_IdAndTradeStatus_TradeStatusIn(traderUserId, activeStatuses);

        // 1. Convert to DTOs for simpler in-memory aggregation logic
        List<TradeDTO> tradeDTOs = trades.stream().map(tradeMapper::toDto).toList();

        // --- Aggregation Logic ---
        
        // Count by Status
        Map<String, Long> countByStatus = tradeDTOs.stream()
            .collect(Collectors.groupingBy(TradeDTO::getTradeStatus, Collectors.counting()));

        // Count by Trade Type
        Map<String, Long> tradeCountByType = tradeDTOs.stream()
            .collect(Collectors.groupingBy(TradeDTO::getTradeType, Collectors.counting()));
            
        // Count by Counterparty Name
        Map<String, Long> tradeCountByCounterparty = tradeDTOs.stream()
            .collect(Collectors.groupingBy(TradeDTO::getCounterpartyName, Collectors.counting()));

        // Total Notional by Currency (Aggregating from TradeLegs)
        Map<String, BigDecimal> notionalByCurrency = tradeDTOs.stream()
            .flatMap(trade -> trade.getTradeLegs().stream())
            .collect(Collectors.groupingBy(
                tradeLeg -> tradeLeg.getCurrency(),
                Collectors.mapping(
                    tradeLeg -> tradeLeg.getNotional().abs(),
                    Collectors.reducing(BigDecimal.ZERO, BigDecimal::add)
                )
            ));

        // Total Notional (Aggregated Notional equivalent)
        BigDecimal aggregatedNotional = notionalByCurrency.values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // --- Mocking Complex Financial Metrics ---
        // In production, these fields (VaR, MTM) would be fetched from 
        // a dedicated Risk or P&L service.
        BigDecimal totalVaR = BigDecimal.valueOf(aggregatedNotional.doubleValue() * 0.015).setScale(2, RoundingMode.HALF_UP);
        BigDecimal portfolioMTM = BigDecimal.valueOf(aggregatedNotional.doubleValue() * 0.003).setScale(2, RoundingMode.HALF_UP);

        TradeSummaryDTO summary = new TradeSummaryDTO();
        summary.setTradeCountByStatus(countByStatus);
        summary.setTradeCountByType(tradeCountByType); // Set new field
        summary.setTradeCountByCounterparty(tradeCountByCounterparty); // Set new field
        summary.setTotalNotionalByCurrency(notionalByCurrency);
        summary.setTotalVaR(totalVaR);
        summary.setPortfolioMTM(portfolioMTM);

        return summary;
    }

    /**
     * Generates a summary of activity for the current day, including comparison metrics.
     */
    public DailySummaryDTO getDailySummary(Long traderUserId) {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        
        // Fetch Today's trades
        List<Trade> todaysTrades = tradeRepository.findTradesByTraderAndDate(traderUserId, today);
        List<TradeDTO> todaysTradeDTOs = todaysTrades.stream().map(tradeMapper::toDto).toList();
        
        // Fetch Yesterday's trades for comparison
        List<Trade> yesterdaysTrades = tradeRepository.findTradesByTraderAndDate(traderUserId, yesterday);
        
        // --- Aggregation & Comparison Logic ---

        long todaysCount = todaysTrades.size();
        long yesterdaysCount = yesterdaysTrades.size();
        
        // Today's Notional (Calculated by summing leg notionals)
        BigDecimal todaysNotional = todaysTradeDTOs.stream()
            .flatMap(trade -> trade.getTradeLegs().stream())
            .map(tradeLeg -> tradeLeg.getNotional().abs())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Comparison Calculation
        long tradeCountChange = todaysCount - yesterdaysCount; 
            

        DailySummaryDTO dailySummary = new DailySummaryDTO();
        
        // Today's Metrics
        dailySummary.setTodaysTradeCount(todaysCount);
        dailySummary.setTodaysTotalNotionalUSD(todaysNotional);
        
        // User-specific performance (Mocked)
        dailySummary.setDailyRealizedPL(BigDecimal.valueOf(150000.00).setScale(2, RoundingMode.HALF_UP));
        
        // Comparison Metrics
        dailySummary.setVsYesterdayTradeCountChange(BigDecimal.valueOf(tradeCountChange));
        dailySummary.setVsYesterdayNotionalChangePercent(BigDecimal.valueOf(4.5).setScale(1, RoundingMode.HALF_UP)); // Mocked percentage
        
        // Book activity (Today's count per book)
        dailySummary.setBookActivitySummary(todaysTrades.stream()
            .collect(Collectors.groupingBy(trade -> trade.getBook().getBookName(), Collectors.counting()))
            .entrySet().stream()
            .collect(Collectors.toMap(e -> "Book-" + e.getKey(), Map.Entry::getValue))); // Convert ID to name for display

        return dailySummary;
    }
}
