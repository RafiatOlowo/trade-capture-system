package com.technicalchallenge.service;

import com.technicalchallenge.dto.CashflowDTO;
import com.technicalchallenge.dto.TradeDTO;
import com.technicalchallenge.dto.TradeLegDTO;
import com.technicalchallenge.model.*;
import com.technicalchallenge.repository.*;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Integration Test for TradeService, focusing on cashflow generation.
@SpringBootTest 
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "test_trader", roles = {"TRADER"})
@Transactional
class TradeServiceIT {

    @Autowired private TradeService tradeService;
    @Autowired private CashflowRepository cashflowRepository;
    @Autowired private ApplicationUserRepository applicationUserRepository;
    @Autowired private BookRepository bookRepository;
    @Autowired private CounterpartyRepository counterpartyRepository;
    @Autowired private TradeStatusRepository tradeStatusRepository;
    @Autowired private CurrencyRepository currencyRepository;
    @Autowired private LegTypeRepository legTypeRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    // Entities required for the test
    private TradeDTO tradeDTO;
    private ApplicationUser testUser;
    private TradeStatus newStatus;
    private Book book;
    private Counterparty counterparty;
    private LegType fixedType;
    private Currency usd;

    @BeforeEach
    void setUp() {

        // MANUAL DATABASE CLEANUP - DELETE DEPENDENT DATA FIRST
        jdbcTemplate.execute("DELETE FROM cashflow");
        jdbcTemplate.execute("DELETE FROM trade_leg");
        jdbcTemplate.execute("DELETE FROM trade");
        jdbcTemplate.execute("DELETE FROM application_user");
        jdbcTemplate.execute("DELETE FROM user_profile");
        jdbcTemplate.execute("DELETE FROM book");
        jdbcTemplate.execute("DELETE FROM counterparty");
        jdbcTemplate.execute("DELETE FROM leg_type");
        jdbcTemplate.execute("DELETE FROM schedule");
        jdbcTemplate.execute("DELETE FROM trade_status");
        jdbcTemplate.execute("DELETE FROM currency");
        
        // --- 1. Reference Data Setup ---
        fixedType = new LegType();
        fixedType.setType("Fixed");
        fixedType = legTypeRepository.save(fixedType);

        Schedule quarterlyScheduleEntity = new Schedule();
        quarterlyScheduleEntity.setSchedule("Quarterly");
        scheduleRepository.save(quarterlyScheduleEntity);

        usd = new Currency();
        usd.setCurrency("USD");
        this.usd = currencyRepository.save(usd);

        newStatus = new TradeStatus();
        newStatus.setTradeStatus("NEW");
        this.newStatus = tradeStatusRepository.save(newStatus);
        
        book = new Book();
        book.setBookName("TEST_BOOK");
        book.setActive(true);
        this.book = bookRepository.save(book);

        counterparty = new Counterparty();
        counterparty.setName("TEST_CP");
        counterparty.setId(null);
        counterparty.setActive(true);
        this.counterparty = counterpartyRepository.save(counterparty);

        UserProfile traderProfile = new UserProfile();
        traderProfile.setUserType("TRADER_SALES"); 
        traderProfile = userProfileRepository.save(traderProfile);

        testUser = new ApplicationUser();
        testUser.setFirstName("TEST");
        testUser.setLastName("TRADER");
        testUser.setLoginId("test_trader");
        testUser.setPassword("pass");
        testUser.setActive(true);
        testUser.setUserProfile(traderProfile);
        this.testUser = applicationUserRepository.save(testUser);
        
        // --- 2. TradeDTO Setup ---
        tradeDTO = new TradeDTO();
        tradeDTO.setTradeId(100001L);

        LocalDate today = LocalDate.now();
        tradeDTO.setTradeDate(today);
        tradeDTO.setTradeStartDate(today.plusDays(2)); 
        tradeDTO.setTradeMaturityDate(today.plusYears(1).plusDays(2));
        
        // Lookup strings for service methods
        tradeDTO.setTradeStatus("NEW");
        tradeDTO.setBookName("TEST_BOOK");
        tradeDTO.setBookId(this.book.getId());
        tradeDTO.setCounterpartyName("TEST_CP");
        tradeDTO.setCounterpartyId(this.counterparty.getId());
        tradeDTO.setTraderUserName("test_trader"); 
        tradeDTO.setTraderUserId(this.testUser.getId());
        tradeDTO.setTradeDate(LocalDate.now()); // Use the current date
        tradeDTO.setTradeStartDate(LocalDate.now().plusDays(2)); 
        tradeDTO.setTradeMaturityDate(LocalDate.now().plusYears(1).plusDays(2));

        LocalDate maturityDate = tradeDTO.getTradeMaturityDate();

        // 3. TradeLeg DTO Setup 
        TradeLegDTO fixedLeg = new TradeLegDTO();
        fixedLeg.setNotional(BigDecimal.valueOf(10000000)); // 10 Million
        fixedLeg.setRate(3.5); // 3.5%
        fixedLeg.setLegType("Fixed");
        fixedLeg.setCurrency("USD");
        fixedLeg.setCalculationPeriodSchedule("Quarterly"); // 3 months interval
        fixedLeg.setPayReceiveFlag("RECEIVE");
        fixedLeg.setCashflows(Arrays.asList(new CashflowDTO(null, null, BigDecimal.ZERO, maturityDate, 0.0, "RECEIVE", null, null, null, true)));

        TradeLegDTO floatLeg = new TradeLegDTO();
        floatLeg.setNotional(BigDecimal.valueOf(10000000));
        floatLeg.setRate(0.0);
        floatLeg.setLegType("Floating");
        floatLeg.setCurrency("USD");
        floatLeg.setCalculationPeriodSchedule("Quarterly");
        floatLeg.setPayReceiveFlag("PAY");
        floatLeg.setCashflows(Arrays.asList(new CashflowDTO(null, null, BigDecimal.ZERO, maturityDate, 0.0, "PAY", null, null, null, true)));
        
        tradeDTO.setTradeLegs(Arrays.asList(fixedLeg, floatLeg));
    }

    // -------------------------------------------------------------------------
    // INTEGRATION TEST: CASHFLOW GENERATION
    // -------------------------------------------------------------------------

    @Test
    @Transactional
    void createTrade_ShouldGenerateAndPersistCorrectCashflowValues() {
        // GIVEN: TradeDTO set up for $10M notional, 3.5% fixed rate, quarterly
        
        // WHEN: Create the trade, which triggers cashflow generation
        tradeService.createTrade(tradeDTO);
        // THEN: Verify the cashflows were saved correctly and the value is accurate
        
        // 1. Verify Count (1 year duration)
        // Fixed Leg: 1 year / 3 months = 4 payments
        // Floating Leg: 1 year / 3 months = 4 payments (will have 0 value initially)
        List<Cashflow> allCashflows = cashflowRepository.findAll();
        
        assertEquals(8, allCashflows.size(), "Total cashflows generated should be 8 (4 per leg).");

        // 2. Verify Calculated Value for the Fixed Leg
        // Expected Value: (10,000,000 * 0.035 * 3) / 12 = 87,500.00
        BigDecimal expectedValue = new BigDecimal("87500.0000000000"); // Exact value, scaled to 10

        // Find the first cashflow from the fixed leg (leg with rate 3.5)
        Cashflow fixedLegCashflow = allCashflows.stream()
            .filter(cf -> cf.getTradeLeg().getRate() == 3.5)
            .findFirst()
            .orElseThrow(() -> new AssertionError("Fixed leg cashflow not found."));

        // The calculated value from the service must match the expected value exactly
        assertNotNull(fixedLegCashflow.getPaymentValue(), "Fixed leg cashflow payment value should not be null.");
        
        // Compare using compareTo to handle BigDecimal precision safely
        assertEquals(0, fixedLegCashflow.getPaymentValue().compareTo(expectedValue),
            "The calculated cashflow value for the fixed leg must be exactly 87,500.00.");

        // 3. Verify Calculated Value for the Floating Leg
        // Expected Value: Floating leg value should be zero before fixing.
        Cashflow floatLegCashflow = allCashflows.stream()
            .filter(cf -> cf.getTradeLeg().getRate() == 0.0)
            .findFirst()
            .orElseThrow(() -> new AssertionError("Floating leg cashflow not found."));
            
        assertNotNull(floatLegCashflow.getPaymentValue(), "Floating leg cashflow payment value should not be null.");

        // FIX: Check if the value is numerically zero (0)
        assertEquals(0, floatLegCashflow.getPaymentValue().compareTo(BigDecimal.ZERO),
            "Floating leg cashflow value must be numerically zero before fixing.");
    }
}