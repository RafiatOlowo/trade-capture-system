package com.technicalchallenge.controller;

import com.technicalchallenge.model.Trade;
import com.technicalchallenge.model.ApplicationUser;
import com.technicalchallenge.model.Book;
import com.technicalchallenge.model.TradeStatus;
import com.technicalchallenge.model.TradeLeg;
import com.technicalchallenge.model.Currency; 
import com.technicalchallenge.repository.TradeRepository;
import com.technicalchallenge.repository.ApplicationUserRepository;
import com.technicalchallenge.repository.BookRepository; 
import com.technicalchallenge.repository.TradeStatusRepository; 
import com.technicalchallenge.repository.CurrencyRepository; 

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest 
@AutoConfigureMockMvc 
@ActiveProfiles("test") 
class TraderDashboardControllerIT {

    private static final String API_BASE = "/api/dashboard";
    private static final String TRADER_LOGIN_ID = "test_user_int";

    @Autowired private MockMvc mockMvc;
    @Autowired private ApplicationUserRepository applicationUserRepository;
    @Autowired private TradeRepository tradeRepository;
    @Autowired private BookRepository bookRepository; 
    @Autowired private TradeStatusRepository tradeStatusRepository; 
    @Autowired private CurrencyRepository currencyRepository; 

    private ApplicationUser testUser;
    private Book book1;
    private TradeStatus liveStatus;
    private TradeStatus amendedStatus;
    private Currency usd;
    private Currency eur;

    @BeforeEach
    void setupDatabase() {
        // Clear data
        tradeRepository.deleteAll();
        applicationUserRepository.deleteAll();
        bookRepository.deleteAll();
        tradeStatusRepository.deleteAll();
        currencyRepository.deleteAll();

        // 1. Create CURRENCIES
        usd = currencyRepository.save(new Currency(null, "USD"));
        eur = currencyRepository.save(new Currency(null, "EUR"));
        
        // 2. Create Supporting Entities
        book1 = bookRepository.save(new Book(null, "EMEA-FX", true, 1, null)); 
        Book book2 = bookRepository.save(new Book(null, "ASIA-EQ", true, 1, null));
        liveStatus = tradeStatusRepository.save(new TradeStatus(null, "LIVE"));
        amendedStatus = tradeStatusRepository.save(new TradeStatus(null, "AMENDED"));

        // 3. Create ApplicationUser
        testUser = applicationUserRepository.save(new ApplicationUser(null, "Test", "User", TRADER_LOGIN_ID, "password", true, null, 1, null)); 

        // 4. Define Common TradeLeg Params
        final Boolean ACTIVE = true;
        final LocalDateTime CREATED_DATE = LocalDateTime.now();
        final Double RATE = 0.0;
        final BigDecimal HALF_MILLION = BigDecimal.valueOf(500000);
        final BigDecimal HUNDRED_K = BigDecimal.valueOf(100000);
        
        // 5. Create Trades with TWO LEGS each

        // Trade 1 (Today, Active/Summary): Total Notional 1M USD (2 legs @ 500k).
        Trade t1 = new Trade();
        t1.setTraderUser(testUser);             
        t1.setBook(book1);                      
        t1.setTradeStatus(liveStatus);          
        t1.setTradeDate(LocalDate.now());
        
        // Legs for T1
        TradeLeg t1_leg_fixed = new TradeLeg(null, HALF_MILLION, RATE, t1, usd, null, null, null, null, null, null, null, ACTIVE, CREATED_DATE, null, null); 
        TradeLeg t1_leg_float = new TradeLeg(null, HALF_MILLION, RATE, t1, usd, null, null, null, null, null, null, null, ACTIVE, CREATED_DATE, null, null); 
        t1.setTradeLegs(List.of(t1_leg_fixed, t1_leg_float));


        // Trade 2 (Today, Active/Summary): Total Notional 1M USD (2 legs @ 500k).
        Trade t2 = new Trade();
        t2.setTraderUser(testUser);
        t2.setBook(book1);
        t2.setTradeStatus(amendedStatus);
        t2.setTradeDate(LocalDate.now());
        
        // Legs for T2
        TradeLeg t2_leg_fixed = new TradeLeg(null, HALF_MILLION, RATE, t2, usd, null, null, null, null, null, null, null, ACTIVE, CREATED_DATE, null, null);
        TradeLeg t2_leg_float = new TradeLeg(null, HALF_MILLION, RATE, t2, usd, null, null, null, null, null, null, null, ACTIVE, CREATED_DATE, null, null);
        t2.setTradeLegs(List.of(t2_leg_fixed, t2_leg_float));


        // Trade 3 (Yesterday, comparison): Total Notional 200k EUR (2 legs @ 100k).
        Trade t3 = new Trade();
        t3.setTraderUser(testUser);
        t3.setBook(book2);
        t3.setTradeStatus(liveStatus);
        t3.setTradeDate(LocalDate.now().minusDays(1));
        
        // Legs for T3
        TradeLeg t3_leg_fixed = new TradeLeg(null, HUNDRED_K, RATE, t3, eur, null, null, null, null, null, null, null, ACTIVE, CREATED_DATE, null, null);
        TradeLeg t3_leg_float = new TradeLeg(null, HUNDRED_K, RATE, t3, eur, null, null, null, null, null, null, null, ACTIVE, CREATED_DATE, null, null);
        t3.setTradeLegs(List.of(t3_leg_fixed, t3_leg_float));

        // Save all trades
        tradeRepository.saveAll(List.of(t1, t2, t3));
    }

    // -------------------------------------------------------------------------
    // INTEGRATION TEST CASES
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = TRADER_LOGIN_ID) 
    void getTraderPersonalTrades_ShouldReturnOnlyAuthenticatedUsersTrades() throws Exception {
        // T1, T2, T3 belong to the authenticated user. Total: 3
        mockMvc.perform(get(API_BASE + "/my-trades")
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3))) 
                .andExpect(jsonPath("$.totalElements", is(3)));
    }

    @Test
    @WithMockUser(username = TRADER_LOGIN_ID)
    void getBookTrades_ShouldReturnTradesForSpecificBookAndUser() throws Exception {
        // T1 and T2 belong to book1. Total: 2
        mockMvc.perform(get(API_BASE + "/book/{bookId}/trades", book1.getId())
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].bookId", is(book1.getId().intValue())));
    }

    @Test
    @WithMockUser(username = TRADER_LOGIN_ID)
    void getPortfolioSummary_ShouldAggregateDataCorrectly() throws Exception {
        // Active Trades: T1, T2 (USD Notional 2,000,000) + T3 (EUR Notional 200,000)
        // Total Aggregated Notional: 2,200,000.00
        // VaR (Mocked): 2,200,000 * 0.015 = 33,000.00
        // MTM (Mocked): 2,200,000 * 0.003 = 6,600.00
        
        final double TOTAL_AGGREGATED_NOTIONAL = 2200000.00; 
        final double USD_NOTIONAL = 2000000.00;
        final double EUR_NOTIONAL = 200000.00; 

        double expectedVaR = BigDecimal.valueOf(TOTAL_AGGREGATED_NOTIONAL * 0.015).setScale(2, RoundingMode.HALF_UP).doubleValue();
        double expectedMTM = BigDecimal.valueOf(TOTAL_AGGREGATED_NOTIONAL * 0.003).setScale(2, RoundingMode.HALF_UP).doubleValue();
        
        mockMvc.perform(get(API_BASE + "/summary")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.portfolioMTM", is(expectedMTM)))
            .andExpect(jsonPath("$.totalVaR", is(expectedVaR)))
            
            // T1 (LIVE), T2 (AMENDED), T3 (LIVE) -> 2 LIVE, 1 AMENDED
            .andExpect(jsonPath("$.tradeCountByStatus.LIVE", is(2)))       
            .andExpect(jsonPath("$.tradeCountByStatus.AMENDED", is(1)))
            
            // Assert both USD and EUR notional breakdown ---
            // Total Notional by Currency:
            .andExpect(jsonPath("$.totalNotionalByCurrency.USD", is(USD_NOTIONAL))) // 2,000,000.00
            .andExpect(jsonPath("$.totalNotionalByCurrency.EUR", is(EUR_NOTIONAL))); // 200,000.00
}

    @Test
    @WithMockUser(username = TRADER_LOGIN_ID)
    void getDailySummary_ShouldCalculateTodayVsYesterday() throws Exception {
        // Today's Trades (T1, T2): Count = 2. Notional = 2,000,000
        // Yesterday's Trades (T3): Count = 1 (T3 is EUR, but included in count)
        // Change in Count: 2 (Today) - 1 (Yesterday) = 1
        
        mockMvc.perform(get(API_BASE + "/daily-summary")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // Today's Metrics
                .andExpect(jsonPath("$.todaysTradeCount", is(2)))
                .andExpect(jsonPath("$.todaysTotalNotionalUSD", is(2000000.00))) // Sum of all today's absolute leg notionals
                // Comparison Metrics
                .andExpect(jsonPath("$.vsYesterdayTradeCountChange", is(1))) 
                // Book Activity: T1 and T2 are in EMEA-FX
                .andExpect(jsonPath("$.bookActivitySummary['Book-EMEA-FX']", is(2))); 
    }
}