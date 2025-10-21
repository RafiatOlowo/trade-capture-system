package com.technicalchallenge.service;

import com.technicalchallenge.dto.TradeDTO;
import com.technicalchallenge.dto.TradeLegDTO;
import com.technicalchallenge.model.ApplicationUser;
import com.technicalchallenge.model.Book;
import com.technicalchallenge.model.Cashflow;
import com.technicalchallenge.model.Counterparty;
import com.technicalchallenge.model.Schedule;
import com.technicalchallenge.model.Trade;
import com.technicalchallenge.model.TradeLeg;
import com.technicalchallenge.model.TradeStatus;
import com.technicalchallenge.model.Currency;
import com.technicalchallenge.model.LegType;
import com.technicalchallenge.repository.ApplicationUserRepository;
import com.technicalchallenge.repository.BookRepository;
import com.technicalchallenge.repository.CashflowRepository;
import com.technicalchallenge.repository.CounterpartyRepository;
import com.technicalchallenge.repository.CurrencyRepository;
import com.technicalchallenge.repository.LegTypeRepository;
import com.technicalchallenge.repository.PayRecRepository;
import com.technicalchallenge.repository.ScheduleRepository;
import com.technicalchallenge.repository.TradeLegRepository;
import com.technicalchallenge.repository.TradeRepository;
import com.technicalchallenge.repository.TradeStatusRepository;
import com.technicalchallenge.validation.TradeValidator;
import com.technicalchallenge.validation.ValidationResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.Authentication;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;
import java.util.Collections;
import java.util.List;
import java.lang.reflect.Method;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradeServiceTest {

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private TradeLegRepository tradeLegRepository;

    @Mock
    private CashflowRepository cashflowRepository;

    @Mock
    private TradeStatusRepository tradeStatusRepository;

    @Mock
    private AdditionalInfoService additionalInfoService;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private CounterpartyRepository counterpartyRepository;

    @Mock
    private ApplicationUserRepository applicationUserRepository;

    @Mock
    private ScheduleRepository scheduleRepository; 
    
    @Mock
    private CurrencyRepository currencyRepository;

    @Mock
    private LegTypeRepository legTypeRepository;

    @Mock
    private PayRecRepository payRecRepository;

    @Mock
    private TradeValidator tradeValidator; 

    @InjectMocks
    private TradeService tradeService;

    private TradeDTO tradeDTO;
    private Trade trade;
    private Book mockBook;
    private Counterparty mockCounterparty;
    private TradeStatus mockTradeStatus;
    private ApplicationUser mockTraderUser;
    private TradeLeg mockTradeLeg;
    private Pageable pageable;
    private List<Trade> tradeList;
    private Page<Trade> tradePage;


    @BeforeEach
    void setUp() {

        // 1. Define the principal object
        String principalUsername = "testUser";
        
        // 2. Mock the Authentication object
        Authentication authentication = Mockito.mock(Authentication.class);
        lenient().when(authentication.getName()).thenReturn(principalUsername);
        lenient().when(authentication.getPrincipal()).thenReturn(principalUsername);

        // 3. Mock the SecurityContext
        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication); 

        // 4. Set the mocked context globally for the test
        SecurityContextHolder.setContext(securityContext);

        // Create successful ValidationResult mocks
        ValidationResult successfulResult = mock(ValidationResult.class);
        lenient().when(successfulResult.isSuccessful()).thenReturn(true);
    
        // MOCK VALIDATOR: Assume privileges are always granted for these basic tests
        lenient().when(tradeValidator.validateUserPrivileges(anyString(), anyString(), any())).thenReturn(true);
    
        // MOCK VALIDATOR: Assume business rules and leg consistency pass by default
        lenient().when(tradeValidator.validateTradeBusinessRules(any())).thenReturn(successfulResult);
        lenient().when(tradeValidator.validateTradeLegConsistency(any())).thenReturn(successfulResult);

        // Set up test data
        tradeDTO = new TradeDTO();
        tradeDTO.setTradeId(100001L);
        tradeDTO.setTradeDate(LocalDate.of(2025, 1, 15));
        tradeDTO.setTradeStartDate(LocalDate.of(2025, 1, 17));
        tradeDTO.setTradeMaturityDate(LocalDate.of(2026, 1, 17));
        tradeDTO.setBookName("TEST_BOOK");
        tradeDTO.setCounterpartyName("TEST_CP");
        tradeDTO.setTraderUserName("TEST_TRADER");

        TradeLegDTO leg1 = new TradeLegDTO();
        leg1.setNotional(BigDecimal.valueOf(1000000));
        leg1.setRate(0.05);

        TradeLegDTO leg2 = new TradeLegDTO();
        leg2.setNotional(BigDecimal.valueOf(1000000));
        leg2.setRate(0.0);

        tradeDTO.setTradeLegs(Arrays.asList(leg1, leg2));
        
        tradeDTO.getTradeLegs().forEach(leg -> {
            leg.setCalculationPeriodSchedule("Monthly"); 
            // leg.setLegType("Fixed"); // Needed for cashflow calculation
        });
        
        mockBook = new Book();
        mockCounterparty = new Counterparty();
        mockTradeStatus = new TradeStatus();
        mockTraderUser = new ApplicationUser();

        mockTradeLeg = new TradeLeg();
        mockTradeLeg.setLegId(1L);

        trade = new Trade();
        trade.setId(1L);
        trade.setTradeId(100001L);
        trade.setVersion(1);
        tradeList = List.of(trade);
        tradePage = new PageImpl<>(tradeList);
        
        pageable = PageRequest.of(0, 10);
    }

    
    // Helper method to call the private calculateCashflowValue method via reflection.

    private BigDecimal callCalculateCashflowValue(TradeLeg leg, int monthsInterval) throws Exception {
        Method method = TradeService.class.getDeclaredMethod("calculateCashflowValue", TradeLeg.class, int.class);
        method.setAccessible(true);
        // The TradeService instance is 'tradeService'
        return (BigDecimal) method.invoke(tradeService, leg, monthsInterval);
    }

    // -------------------------------------------------------------------------
    // UNIT TESTS FOR calculateCashflowValue(TradeLeg leg, int monthsInterval)
    // -------------------------------------------------------------------------

    @Test
    void calculateCashflowValue_FixedLeg_Quarterly_ValueCheck() throws Exception {
        // GIVEN: Fixed Leg (Quarterly Interval = 3 months)
        // Formula: (Notional * Rate * Months) / 12
        // Input: Notional = 10,000,000, Rate = 3.5% (0.035), Months = 3
        // Expected: (10,000,000 * 0.035 * 3) / 12 = 1,050,000 / 12 = 87,500.00
        
        TradeLeg fixedLeg = new TradeLeg();
        fixedLeg.setNotional(BigDecimal.valueOf(10000000)); // $10,000,000
        fixedLeg.setRate(3.5); // 3.5%
        
        LegType fixedType = new LegType();
        fixedType.setType("Fixed");
        fixedLeg.setLegRateType(fixedType);
        
        int monthsInterval = 3; // Quarterly

        // WHEN
        BigDecimal result = callCalculateCashflowValue(fixedLeg, monthsInterval);

        // THEN
        // The calculation yields exactly 87,500. The service scales to 10 decimal places.
        // The value 87500.0000000000 is mathematically exact.
        BigDecimal expectedValue = new BigDecimal("87500.0000000000"); // Enforce 10 decimal places
        
        // Use compareTo(0) for safe BigDecimal equality check
        assertEquals(0, result.compareTo(expectedValue), 
            "Cashflow value for 10M at 3.5% quarterly must be 87,500.00.");
    }

    @Test
    void calculateCashflowValue_FixedLeg_Monthly_Success() throws Exception {
        // GIVEN: Fixed Leg (Monthly Interval = 1 month)
        // Formula: (Notional * Rate * Months) / 12
        // Input: Notional = 1,000,000, Rate = 5.0% (0.05), Months = 1
        // Expected: 4166.6666666700 (10 decimal places, HALF_UP rounding)
        
        TradeLeg fixedLeg = new TradeLeg();
        fixedLeg.setNotional(BigDecimal.valueOf(1000000));
        fixedLeg.setRate(5.0); // 5.0%
        
        LegType fixedType = new LegType();
        fixedType.setType("Fixed");
        fixedLeg.setLegRateType(fixedType);
        
        int monthsInterval = 1; // Monthly

        // WHEN
        BigDecimal result = callCalculateCashflowValue(fixedLeg, monthsInterval);

        // THEN
        // Define the expected value using the string constructor to enforce 10 decimal places
        BigDecimal expectedValue = new BigDecimal("4166.6666666667"); 
        
        // Use compareTo(0) for safe BigDecimal equality check
        assertEquals(0, result.compareTo(expectedValue), 
            "Cashflow value for fixed leg should be correct for monthly period.");
    }

    @Test
    void calculateCashflowValue_FixedLeg_Quarterly_Success() throws Exception {
        // GIVEN: Fixed Leg (Quarterly Interval = 3 months)
        // Formula: (Notional * Rate * Months) / 12
        // Input: Notional = 20,000,000, Rate = 3.5% (0.035), Months = 3
        // Expected: (20,000,000 * 0.035 * 3) / 12 = 2,100,000 / 12 = 175,000.00
        
        TradeLeg fixedLeg = new TradeLeg();
        fixedLeg.setNotional(BigDecimal.valueOf(20000000));
        fixedLeg.setRate(3.5); // 3.5%
        
        LegType fixedType = new LegType();
        fixedType.setType("Fixed");
        fixedLeg.setLegRateType(fixedType);
        
        int monthsInterval = 3; // Quarterly

        // WHEN
        BigDecimal result = callCalculateCashflowValue(fixedLeg, monthsInterval);

        // THEN
        BigDecimal expected = BigDecimal.valueOf(175000.0000000000); 
        assertEquals(0, result.compareTo(expected), "Cashflow value for fixed leg should be correct for quarterly period.");
    }

    @Test
    void calculateCashflowValue_FloatingLeg_ReturnsZero() throws Exception {
        // GIVEN: Floating Leg
        TradeLeg floatLeg = new TradeLeg();
        floatLeg.setNotional(BigDecimal.valueOf(1000000));
        floatLeg.setRate(0.0);
        
        LegType floatType = new LegType();
        floatType.setType("Floating");
        floatLeg.setLegRateType(floatType);
        
        int monthsInterval = 6; // Semi-annual

        // WHEN
        BigDecimal result = callCalculateCashflowValue(floatLeg, monthsInterval);

        // THEN
        assertEquals(BigDecimal.ZERO, result, "Cashflow value for floating leg should be zero (before fixing).");
    }

    @Test
    void calculateCashflowValue_UnknownLegType_ReturnsZero() throws Exception {
        // GIVEN: Unknown Leg Type
        TradeLeg unknownLeg = new TradeLeg();
        unknownLeg.setNotional(BigDecimal.valueOf(1000000));
        unknownLeg.setRate(5.0);
        
        LegType unknownType = new LegType();
        unknownType.setType("Equity");
        unknownLeg.setLegRateType(unknownType);
        
        int monthsInterval = 1;

        // WHEN
        BigDecimal result = callCalculateCashflowValue(unknownLeg, monthsInterval);

        // THEN
        assertEquals(BigDecimal.ZERO, result, "Cashflow value for unknown leg type should be zero.");
    }

    @Test
    void calculateCashflowValue_NullLegType_ReturnsZero() throws Exception {
        // GIVEN: Null Leg Type
        TradeLeg nullLeg = new TradeLeg();
        nullLeg.setNotional(BigDecimal.valueOf(1000000));
        nullLeg.setRate(5.0);
        nullLeg.setLegRateType(null);
        
        int monthsInterval = 1;

        // WHEN
        BigDecimal result = callCalculateCashflowValue(nullLeg, monthsInterval);

        // THEN
        assertEquals(BigDecimal.ZERO, result, "Cashflow value for null leg type should be zero.");
    }

    @Test
    void testCreateTrade_Success() {
        // Given
        when(tradeRepository.save(any(Trade.class))).thenReturn(trade);
        when(bookRepository.findByBookName(anyString())).thenReturn(Optional.of(mockBook));
        when(counterpartyRepository.findByName(anyString())).thenReturn(Optional.of(mockCounterparty));
        when(tradeStatusRepository.findByTradeStatus("NEW")).thenReturn(Optional.of(mockTradeStatus));
        when(applicationUserRepository.findByFirstName(anyString())).thenReturn(Optional.of(mockTraderUser));
        when(tradeLegRepository.save(any(TradeLeg.class))).thenReturn(mockTradeLeg);

        // When
        Trade result = tradeService.createTrade(tradeDTO);

        // Then
        assertNotNull(result);
        assertEquals(100001L, result.getTradeId());
        verify(tradeRepository).save(any(Trade.class));
        verify(bookRepository).findByBookName(anyString());
        verify(counterpartyRepository).findByName(anyString());
        verify(tradeLegRepository, times(2)).save(any(TradeLeg.class));
    }

    @Test
    void testCreateTrade_InvalidDates_ShouldFail() {
        // Given - This test is intentionally failing for candidates to fix
        tradeDTO.setTradeStartDate(LocalDate.of(2025, 1, 10)); // Before trade date

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
     
            tradeService.createTrade(tradeDTO);
        });

        // This assertion is intentionally wrong - candidates need to fix it
        assertEquals("Start date cannot be before trade date", exception.getMessage());
    }

    @Test
    void testCreateTrade_InvalidLegCount_ShouldFail() {
        // Given
        tradeDTO.setTradeLegs(Arrays.asList(new TradeLegDTO())); // Only 1 leg

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            tradeService.createTrade(tradeDTO);
        });

        assertTrue(exception.getMessage().contains("Trade must have exactly 2 legs"));
    }

    @Test
    void testGetTradeById_Found() {
        // Given
        when(tradeRepository.findByTradeIdAndActiveTrue(100001L)).thenReturn(Optional.of(trade));

        // When
        Optional<Trade> result = tradeService.getTradeById(100001L);

        // Then
        assertTrue(result.isPresent());
        assertEquals(100001L, result.get().getTradeId());
    }

    @Test
    void testGetTradeById_NotFound() {
        // Given
        when(tradeRepository.findByTradeIdAndActiveTrue(999L)).thenReturn(Optional.empty());

        // When
        Optional<Trade> result = tradeService.getTradeById(999L);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void testAmendTrade_Success() {
        // Given
        when(tradeRepository.findByTradeIdAndActiveTrue(100001L)).thenReturn(Optional.of(trade));
        when(tradeStatusRepository.findByTradeStatus("AMENDED")).thenReturn(Optional.of(new com.technicalchallenge.model.TradeStatus()));
        when(tradeRepository.save(any(Trade.class))).thenReturn(trade);
        when(tradeLegRepository.save(any(TradeLeg.class))).thenReturn(mockTradeLeg);

        // When
        Trade result = tradeService.amendTrade(100001L, tradeDTO);

        // Then
        assertNotNull(result);
        verify(tradeRepository, times(2)).save(any(Trade.class)); // Save old and new
        verify(tradeLegRepository, times(2)).save(any(TradeLeg.class));
    }

    @Test
    void testAmendTrade_TradeNotFound() {
        // Given
        when(tradeRepository.findByTradeIdAndActiveTrue(999L)).thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            tradeService.amendTrade(999L, tradeDTO);
        });

        assertTrue(exception.getMessage().contains("Trade not found: 999"));
    }

    // This test has a deliberate bug for candidates to find and fix
    @Test
    void testCashflowGeneration_MonthlySchedule() {
        // This test method is incomplete and has logical errors
        // Candidates need to implement proper cashflow testing

        // GIVEN - SETUP COMPLETE
        // Mock reference data lookups for Trade (from testCreateTrade_Success)
        when(tradeRepository.save(any(Trade.class))).thenReturn(trade);
        when(bookRepository.findByBookName(anyString())).thenReturn(Optional.of(mockBook));
        when(counterpartyRepository.findByName(anyString())).thenReturn(Optional.of(mockCounterparty));
        when(tradeStatusRepository.findByTradeStatus("NEW")).thenReturn(Optional.of(mockTradeStatus));
        when(applicationUserRepository.findByFirstName(anyString())).thenReturn(Optional.of(mockTraderUser));
        
        // Mock reference data lookups for TradeLegs
        Schedule mockSchedule = new Schedule();
        mockSchedule.setSchedule("Monthly");
        // Mocking the reference data lookup for the schedule
        when(scheduleRepository.findBySchedule("Monthly")).thenReturn(Optional.of(mockSchedule));

        // Mock the TradeLegRepository.save() to ensure the saved leg has the Schedule
        // The generateCashflows logic relies on the Schedule being on the saved leg.
        when(tradeLegRepository.save(any(TradeLeg.class))).thenAnswer(invocation -> {
            TradeLeg savedLeg = invocation.getArgument(0);
            savedLeg.setLegId(1L); // Simulate ID generation
            savedLeg.setCalculationPeriodSchedule(mockSchedule); // Inject the Schedule entity
            return savedLeg;
        });

        // WHEN
        // Call the public method which triggers the entire cashflow generation process
        tradeService.createTrade(tradeDTO);

        // THEN 
        // Duration: 2025-01-17 to 2026-01-17 is 1 year (12 months).
        // Schedule: Monthly (1 month interval).
        // Cashflows per leg: 12 payments.
        // Total Legs: 2.
        // Total cashflow saves: 2 legs * 12 payments/leg = 24.
        
        // Verify that the CashflowRepository.save() method was called 24 times.
        verify(cashflowRepository, times(24)).save(any(Cashflow.class));
    }

    // Test Methods for findAll(Pageable)

    @Test
    void testFindAll_ReturnsPageOfTrades() {
        // Arrange
        when(tradeRepository.findAll(pageable)).thenReturn(tradePage);

        // Act
        Page<Trade> result = tradeService.findAll(pageable);

        // Assert
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.getTotalElements());
        verify(tradeRepository, times(1)).findAll(pageable);
    }

    @Test
    void testFindAll_HandlesEmptyResult() {
        // Arrange
        Page<Trade> emptyPage = new PageImpl<>(Collections.emptyList());
        when(tradeRepository.findAll(pageable)).thenReturn(emptyPage);

        // Act
        Page<Trade> result = tradeService.findAll(pageable);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertEquals(0, result.getTotalElements());
        verify(tradeRepository, times(1)).findAll(pageable);
    }

    // Test Methods for searchTrades(Specification, Pageable)
    @Test
    void testSearchTrades_ExecutesWithSpecification() {
        // Arrange
        Specification<Trade> mockSpec = mock(Specification.class);
        when(tradeRepository.findAll(mockSpec, pageable)).thenReturn(tradePage);

        // Act
        Page<Trade> result = tradeService.searchTrades(mockSpec, pageable);

        // Assert
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(trade.getId(), result.getContent().get(0).getId());
        // Verify the repository was called with the specific Specification and Pageable
        verify(tradeRepository, times(1)).findAll(mockSpec, pageable); 
    }

    @Test
    void testSearchTrades_HandlesNoMatch() {
        // Arrange
        Specification<Trade> mockSpec = mock(Specification.class); 
        Page<Trade> emptyPage = new PageImpl<>(Collections.emptyList());
        when(tradeRepository.findAll(mockSpec, pageable)).thenReturn(emptyPage);

        // Act
        Page<Trade> result = tradeService.searchTrades(mockSpec, pageable);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(tradeRepository, times(1)).findAll(mockSpec, pageable);
    }
}
