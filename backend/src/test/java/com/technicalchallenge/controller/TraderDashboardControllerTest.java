package com.technicalchallenge.controller;

import com.technicalchallenge.dto.DailySummaryDTO;
import com.technicalchallenge.dto.TradeDTO;
import com.technicalchallenge.dto.TradeSummaryDTO;
import com.technicalchallenge.service.TraderDashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Unit tests for TraderDashboardController using MockMvc to test the Controller layer in isolation.
// Security context and service layer logic are mocked.

@WebMvcTest(TraderDashboardController.class)
class TraderDashboardControllerTest {

    private static final String API_BASE = "/api/dashboard";
    private static final String TEST_LOGIN_ID = "test_trader";
    private static final Long TEST_USER_ID = 100L;
    private static final Long TEST_BOOK_ID = 5L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TraderDashboardService dashboardService;

    @BeforeEach
    void setUp() {
        // Mock the conversion of loginId (from SecurityContext) to internal ID
        when(dashboardService.getTraderIdByLoginId(eq(TEST_LOGIN_ID))).thenReturn(TEST_USER_ID);
    }

    // SECURITY AND CRITICAL AUTHENTICATION TESTS

    @Test
    void getTraderPersonalTrades_WhenUnauthenticated_ShouldReturnUnauthorized() throws Exception {
        // Test access without @WithMockUser
        mockMvc.perform(get(API_BASE + "/my-trades"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = TEST_LOGIN_ID)
    void getCurrentTraderUserId_WhenUserNotFound_ShouldReturnInternalServerError() throws Exception {
        // Arrange
        when(dashboardService.getTraderIdByLoginId(eq(TEST_LOGIN_ID))).thenReturn(null);

        // Act & Assert
        mockMvc.perform(get(API_BASE + "/my-trades"))
                .andExpect(status().isInternalServerError())
                .andExpect(status().reason(org.hamcrest.Matchers.containsString("internal trader ID not found")));
    }
    
    // 1. My Trades Blotter (/my-trades)
    

    @Test
    @WithMockUser(username = TEST_LOGIN_ID)
    void getTraderPersonalTrades_ShouldReturnPageOfTrades() throws Exception {
        // Arrange
        TradeDTO mockTrade = new TradeDTO();
        mockTrade.setTradeId(1L);
        mockTrade.setBookId(TEST_BOOK_ID);

        Pageable pageable = PageRequest.of(0, 10);
        PageImpl<TradeDTO> mockPage = new PageImpl<>(Collections.singletonList(mockTrade), pageable, 1);

        // Mock service call using the expected user ID
        when(dashboardService.getUserTrades(eq(TEST_USER_ID), any(Pageable.class))).thenReturn(mockPage);

        // Act & Assert
        mockMvc.perform(get(API_BASE + "/my-trades")
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].tradeId", is(1)))
                .andExpect(jsonPath("$.totalPages", is(1)))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    
    // 2. Book Trades Blotter (/book/{bookId}/trades)
    

    @Test
    @WithMockUser(username = TEST_LOGIN_ID)
    void getBookTrades_ShouldReturnPageOfTradesForBook() throws Exception {
        // Arrange
        TradeDTO mockTrade = new TradeDTO();
        mockTrade.setTradeId(2L);
        mockTrade.setBookId(TEST_BOOK_ID);

        Pageable pageable = PageRequest.of(0, 10);
        PageImpl<TradeDTO> mockPage = new PageImpl<>(Collections.singletonList(mockTrade), pageable, 1);

        // Mock service call using the expected user ID and Book ID
        when(dashboardService.getBookTrades(eq(TEST_USER_ID), eq(TEST_BOOK_ID), any(Pageable.class))).thenReturn(mockPage);

        // Act & Assert
        mockMvc.perform(get(API_BASE + "/book/{bookId}/trades", TEST_BOOK_ID)
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].tradeId", is(2)))
                .andExpect(jsonPath("$.content[0].bookId", is(TEST_BOOK_ID.intValue())));
    }

    @Test
    @WithMockUser(username = TEST_LOGIN_ID)
    void getBookTrades_WhenServiceReturnsEmpty_ShouldReturnOkWithEmptyPage() throws Exception {
        // Arrange
        PageImpl<TradeDTO> emptyPage = new PageImpl<>(Collections.emptyList());
        when(dashboardService.getBookTrades(eq(TEST_USER_ID), eq(TEST_BOOK_ID), any(Pageable.class))).thenReturn(emptyPage);

        // Act & Assert
        mockMvc.perform(get(API_BASE + "/book/{bookId}/trades", TEST_BOOK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    
    // 3. Portfolio Summary (/summary)
    

    @Test
    @WithMockUser(username = TEST_LOGIN_ID)
    void getPortfolioSummary_ShouldReturnTradeSummaryDTO() throws Exception {
        // Arrange
        Map<String, BigDecimal> mockNotionalByCurrency = new HashMap<>();
        mockNotionalByCurrency.put("USD", BigDecimal.valueOf(10000000));
        mockNotionalByCurrency.put("EUR", BigDecimal.valueOf(500000));

        TradeSummaryDTO mockSummary = new TradeSummaryDTO(
            mockNotionalByCurrency,      // Argument 1: Map<String, BigDecimal>
            BigDecimal.valueOf(-10000),  // Argument 2: portfolioMTM
            BigDecimal.valueOf(5000) // Argument 3: totalVaR
        );

        when(dashboardService.getPortfolioSummary(eq(TEST_USER_ID))).thenReturn(mockSummary);

        // Act & Assert
        mockMvc.perform(get(API_BASE + "/summary")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNotionalByCurrency.USD", is(10000000)))
                .andExpect(jsonPath("$.totalNotionalByCurrency.EUR", is(500000)))
                .andExpect(jsonPath("$.portfolioMTM", is(-10000)))
                .andExpect(jsonPath("$.totalVaR", is(5000)));
    }

    
    // 4. Daily Summary (/daily-summary)
    

    @Test
    @WithMockUser(username = TEST_LOGIN_ID)
    void getDailySummary_ShouldReturnDailySummaryDTO() throws Exception {
        // Arrange
            DailySummaryDTO mockDailySummary = new DailySummaryDTO(
            10L,
            BigDecimal.valueOf(500000),
            BigDecimal.valueOf(1000)
        );

        when(dashboardService.getDailySummary(eq(TEST_USER_ID))).thenReturn(mockDailySummary);

        // Act & Assert
        mockMvc.perform(get(API_BASE + "/daily-summary")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todaysTradeCount", is(10)))
                .andExpect(jsonPath("$.todaysTotalNotionalUSD", is(500000)))
                .andExpect(jsonPath("$.dailyRealizedPL", is(1000)));
    }
}