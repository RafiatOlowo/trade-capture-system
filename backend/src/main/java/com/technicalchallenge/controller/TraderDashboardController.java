package com.technicalchallenge.controller;

import com.technicalchallenge.dto.DailySummaryDTO;
import com.technicalchallenge.dto.TradeDTO;
import com.technicalchallenge.dto.TradeSummaryDTO;
import com.technicalchallenge.service.TraderDashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST Controller for the Trader Dashboard, providing access to trade blotters 
 * and aggregated summary data for the authenticated trader.
 */
@RestController
@RequestMapping("/api/dashboard")
public class TraderDashboardController {

    private static final Logger log = LoggerFactory.getLogger(TraderDashboardController.class);

    @Autowired
    private TraderDashboardService dashboardService;

    // Helper method to retrieve the authenticated trader's internal user ID.
    private Long getCurrentTraderUserId() {
        String loginId = SecurityContextHolder.getContext().getAuthentication().getName();
        
        log.debug("Attempting to resolve internal ID for authenticated loginId: {}", loginId);

        Long traderUserId = dashboardService.getTraderIdByLoginId(loginId);

        if (traderUserId == null) {
            log.error("CRITICAL: User principal found in security context but internal trader ID not found for: {}", loginId);
            
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "User principal found in security context but internal trader ID not found for: " + loginId);
        }
        
        log.debug("Resolved internal traderUserId: {} for loginId: {}", traderUserId, loginId);
        return traderUserId;
    }

// Blotter Endpoints
    @GetMapping("/my-trades")
    public ResponseEntity<Page<TradeDTO>> getTraderPersonalTrades(Pageable pageable) {
        Long traderUserId = getCurrentTraderUserId();
        log.info("Fetching trades blotter for trader: {} with pageable: {}", traderUserId, pageable);
        
        Page<TradeDTO> trades = dashboardService.getUserTrades(traderUserId, pageable);
        
        log.debug("Successfully retrieved {} trades for trader: {}", trades.getNumberOfElements(), traderUserId);
        return ResponseEntity.ok(trades);
    }

    @GetMapping("/book/{bookId}/trades")
    public ResponseEntity<Page<TradeDTO>> getBookTrades(@PathVariable("bookId") Long bookId, Pageable pageable) {
        Long traderUserId = getCurrentTraderUserId();
        log.info("Fetching trades for Book ID: {} by trader: {} with pageable: {}", bookId, traderUserId, pageable);
        
        Page<TradeDTO> trades = dashboardService.getBookTrades(traderUserId, bookId, pageable);
        
        log.debug("Successfully retrieved {} trades for Book ID: {} by trader: {}", trades.getNumberOfElements(), bookId, traderUserId);
        return ResponseEntity.ok(trades);
    }

// Summary & Analytics Endpoints
    @GetMapping("/summary")
    public ResponseEntity<TradeSummaryDTO> getPortfolioSummary() {
        Long traderUserId = getCurrentTraderUserId();
        log.info("Generating portfolio summary for trader: {}", traderUserId);
        
        TradeSummaryDTO summary = dashboardService.getPortfolioSummary(traderUserId);
        
        log.debug("Summary generated for trader {}. Total VaR: {}", traderUserId, summary.getTotalVaR());
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/daily-summary")
    public ResponseEntity<DailySummaryDTO> getDailySummary() {
        Long traderUserId = getCurrentTraderUserId();
        log.info("Generating daily summary for trader: {}", traderUserId);
        
        DailySummaryDTO dailySummary = dashboardService.getDailySummary(traderUserId);
        
        log.debug("Daily summary for trader {} generated. Today's trade count: {}", traderUserId, dailySummary.getTodaysTradeCount());
        return ResponseEntity.ok(dailySummary);
    }
}