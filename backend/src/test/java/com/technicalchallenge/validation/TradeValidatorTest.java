package com.technicalchallenge.validation;

import com.technicalchallenge.dto.CashflowDTO;
import com.technicalchallenge.dto.TradeDTO;
import com.technicalchallenge.dto.TradeLegDTO;
import com.technicalchallenge.model.*;
import com.technicalchallenge.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeValidatorTest {

    // Mock Repositories
    @Mock(lenient = true) private ApplicationUserRepository userRepository;
    @Mock(lenient = true) private BookRepository bookRepository;
    @Mock(lenient = true) private CounterpartyRepository counterpartyRepository;
    @Mock(lenient = true) private TradeStatusRepository tradeStatusRepository;
    @Mock(lenient = true) private TradeTypeRepository tradeTypeRepository;
    @Mock(lenient = true) private TradeSubTypeRepository tradeSubTypeRepository;

    @Mock private CurrencyRepository currencyRepository;
    @Mock private LegTypeRepository legTypeRepository;
    @Mock private IndexRepository indexRepository;
    @Mock private HolidayCalendarRepository holidayCalendarRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private BusinessDayConventionRepository businessDayConventionRepository;
    @Mock private PayRecRepository payRecRepository;

    @InjectMocks
    private TradeValidator tradeValidator;

    private TradeDTO validTradeDTO;
    private TradeLegDTO fixedLeg;
    private TradeLegDTO floatLeg;
    private Long validBookId = 1L;
    private Long validCpId = 2L;
    private Long validTraderId = 3L;

    @BeforeEach
    void setUp() {
        // Setup Valid Trade Legs
        fixedLeg = new TradeLegDTO();
        fixedLeg.setNotional(BigDecimal.valueOf(1000000));
        fixedLeg.setCurrency("USD");
        fixedLeg.setPayReceiveFlag("PAY");
        fixedLeg.setLegType("FIXED");
        fixedLeg.setRate(0.05);

        LocalDate sharedMaturityDate = LocalDate.now().plusYears(1);
        fixedLeg.setCashflows(createDefaultCashflows(sharedMaturityDate, 2));

        floatLeg = new TradeLegDTO();
        floatLeg.setNotional(BigDecimal.valueOf(1000000));
        floatLeg.setCurrency("USD");
        floatLeg.setPayReceiveFlag("RECEIVE");
        floatLeg.setLegType("FLOAT");
        floatLeg.setIndexName("SOFR");

        floatLeg.setCashflows(createDefaultCashflows(LocalDate.now().plusYears(1), 2));

        // Setup Valid Trade DTO
        validTradeDTO = new TradeDTO();
        validTradeDTO.setTradeDate(LocalDate.now());
        validTradeDTO.setTradeStartDate(LocalDate.now().plusDays(2));
        validTradeDTO.setTradeMaturityDate(LocalDate.now().plusYears(1));
        validTradeDTO.setBookId(validBookId);
        validTradeDTO.setCounterpartyId(validCpId);
        validTradeDTO.setTraderUserId(validTraderId);
        validTradeDTO.setTradeLegs(Arrays.asList(fixedLeg, floatLeg));

        // Mock Default Successful Repository Calls for TradeDTO fields
        when(bookRepository.existsByIdAndActive(eq(validBookId), eq(true))).thenReturn(true);
        when(counterpartyRepository.existsByIdAndActive(eq(validCpId), eq(true))).thenReturn(true);
        when(userRepository.existsByIdAndActive(eq(validTraderId), eq(true))).thenReturn(true);
    }

    private List<CashflowDTO> createDefaultCashflows(LocalDate lastDate, int count) {
        return Arrays.asList(
            new CashflowDTO(1L, null, BigDecimal.ONE, lastDate.minusMonths(3), 0.0, null, null, null, null, true),
            new CashflowDTO(2L, null, BigDecimal.ONE, lastDate, 0.0, null, null, null, null, true)
        );
    }

    // Helper Method for Mocking ApplicationUser Structure
    private void mockUser(String loginId, String userTypeString) {
        ApplicationUser user = new ApplicationUser();
        user.setLoginId(loginId);
        user.setActive(true);
        
        UserProfile profile = new UserProfile();
        profile.setUserType(userTypeString); 

        user.setUserProfile(profile);
        
        when(userRepository.findByLoginId(eq(loginId))).thenReturn(Optional.of(user));
    }
    

    // --- 1. validateTradeBusinessRules Tests ---


    @Test
    void testValidateBusinessRules_AllValid_ShouldSucceed() {
        // Act
        ValidationResult result = tradeValidator.validateTradeBusinessRules(validTradeDTO);

        // Assert
        assertTrue(result.isSuccessful());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void testValidateBusinessRules_MaturityDateBeforeStartDate_ShouldFail() {
        // Arrange
        validTradeDTO.setTradeMaturityDate(validTradeDTO.getTradeStartDate().minusDays(1));

        // Act
        ValidationResult result = tradeValidator.validateTradeBusinessRules(validTradeDTO);

        // Assert
        assertFalse(result.isSuccessful());
        assertTrue(result.toErrorMessage().contains("cannot be before Start Date"));
    }

    @Test
    void testValidateBusinessRules_TradeDateTooFarInPast_ShouldFail() {
        // Arrange
        validTradeDTO.setTradeDate(LocalDate.now().minusDays(31));

        // Act
        ValidationResult result = tradeValidator.validateTradeBusinessRules(validTradeDTO);

        // Assert
        assertFalse(result.isSuccessful());
        assertTrue(result.toErrorMessage().contains("is more than 30 days in the past."));
    }
    
    @Test
    void testValidateBusinessRules_InactiveBook_ShouldFail() {
        // Arrange
        Long inactiveBookId = 99L;
        validTradeDTO.setBookId(inactiveBookId);
        when(bookRepository.existsByIdAndActive(eq(inactiveBookId), eq(true))).thenReturn(false);

        // Act
        ValidationResult result = tradeValidator.validateTradeBusinessRules(validTradeDTO);

        // Assert
        assertFalse(result.isSuccessful());
        assertTrue(result.toErrorMessage().contains("Trade Book is inactive or does not exist (ID: " + inactiveBookId + ")."));
    }

    @Test
    void testValidateBusinessRules_InvalidTradeStatusName_ShouldFail() {
        // Arrange
        validTradeDTO.setTradeStatus("INVALID_STATUS");
        when(tradeStatusRepository.existsByTradeStatus(eq("INVALID_STATUS"))).thenReturn(false);

        // Act
        ValidationResult result = tradeValidator.validateTradeBusinessRules(validTradeDTO);

        // Assert
        assertFalse(result.isSuccessful());
        assertTrue(result.toErrorMessage().contains("Trade Status is invalid or does not exist (Name: INVALID_STATUS)."));
    }
    
    // --- 2. validateTradeLegConsistency Tests ---

    @Test
    void testValidateLegConsistency_AllValid_ShouldSucceed() {
        // Act
        ValidationResult result = tradeValidator.validateTradeLegConsistency(validTradeDTO.getTradeLegs());

        // Assert
        assertTrue(result.isSuccessful());
        assertTrue(result.getErrors().isEmpty());
    }
    
    @Test
    void testValidateLegConsistency_NotTwoLegs_ShouldFail() {
        // Arrange
        List<TradeLegDTO> singleLegs = Arrays.asList(fixedLeg);
        
        // Act
        ValidationResult result = tradeValidator.validateTradeLegConsistency(singleLegs);
        
        // Assert
        assertFalse(result.isSuccessful());
        assertTrue(result.toErrorMessage().contains("A trade must contain exactly two legs."));
    }

    @Test
    void testValidateLegConsistency_SamePayReceiveFlag_ShouldFail() {
        // Arrange
        floatLeg.setPayReceiveFlag("PAY"); // Same as fixedLeg
        
        // Act
        ValidationResult result = tradeValidator.validateTradeLegConsistency(validTradeDTO.getTradeLegs());

        // Assert
        assertFalse(result.isSuccessful());
        assertTrue(result.toErrorMessage().contains("Both legs must have opposite Pay/Receive flags"));
    }

    @Test
    void testValidateLegConsistency_DifferentNotional_ShouldFail() {
        // Arrange
        floatLeg.setNotional(BigDecimal.valueOf(500000)); // Different from fixedLeg (1000000)

        // Act
        ValidationResult result = tradeValidator.validateTradeLegConsistency(validTradeDTO.getTradeLegs());

        // Assert
        assertFalse(result.isSuccessful());
        assertTrue(result.toErrorMessage().contains("Notional values must be identical across both legs."));
    }
    
    @Test
    void testValidateLegConsistency_FixedLegMissingRate_ShouldFail() {
        // Arrange
        fixedLeg.setRate(null); // Fixed leg missing rate

        // Act
        ValidationResult result = tradeValidator.validateTradeLegConsistency(validTradeDTO.getTradeLegs());

        // Assert
        assertFalse(result.isSuccessful());
        assertTrue(result.toErrorMessage().contains("Leg 1: Fixed leg missing required rate specification."));
    }

    @Test
    void testValidateLegConsistency_FloatingLegMissingIndex_ShouldFail() {
        // Arrange
        floatLeg.setIndexName(null); // Floating leg missing index

        // Act
        ValidationResult result = tradeValidator.validateTradeLegConsistency(validTradeDTO.getTradeLegs());

        // Assert
        assertFalse(result.isSuccessful());
        assertTrue(result.toErrorMessage().contains("Leg 2: Floating leg missing required index specification (Name or ID)."));
    }

    // --- 3. validateUserPrivileges Tests ---
    
    @Test
    void testValidateUserPrivileges_TraderSales_CanCreateAndAmend_ShouldSucceed() {
        // Arrange
        mockUser("trader1", "TRADER_SALES");
        
        // Assert
        assertTrue(tradeValidator.validateUserPrivileges("trader1", "CREATE", validTradeDTO));
        assertTrue(tradeValidator.validateUserPrivileges("trader1", "AMEND", validTradeDTO));
    }
    
    @Test
    void testValidateUserPrivileges_TraderSales_CannotApprove_ShouldFail() {
        // Arrange
        mockUser("trader1", "TRADER_SALES");
        
        // Assert
        assertFalse(tradeValidator.validateUserPrivileges("trader1", "APPROVE", validTradeDTO));
    }

    @Test
    void testValidateUserPrivileges_MO_CanAmendAndView_ShouldSucceed() {
        // Arrange
        mockUser("mo_user", "MO");
        
        // Assert
        assertTrue(tradeValidator.validateUserPrivileges("mo_user", "AMEND", validTradeDTO));
        assertTrue(tradeValidator.validateUserPrivileges("mo_user", "VIEW", validTradeDTO));
    }
    
    @Test
    void testValidateUserPrivileges_MO_CannotCreate_ShouldFail() {
        // Arrange
        mockUser("mo_user", "MO");
        
        // Assert
        assertFalse(tradeValidator.validateUserPrivileges("mo_user", "CREATE", validTradeDTO));
    }

    @Test
    void testValidateUserPrivileges_Support_OnlyCanView_ShouldSucceed() {
        // Arrange
        mockUser("support", "SUPPORT");
        
        // Assert
        assertTrue(tradeValidator.validateUserPrivileges("support", "VIEW", null));
    }

    @Test
    void testValidateUserPrivileges_SuperUser_CanDoAnything_ShouldSucceed() {
        // Arrange
        mockUser("super", "SUPERUSER");
        
        // Assert
        assertTrue(tradeValidator.validateUserPrivileges("super", "CREATE", validTradeDTO));
        assertTrue(tradeValidator.validateUserPrivileges("super", "TERMINATE", null));
    }

    @Test
    void testValidateUserPrivileges_InvalidOperation_ShouldFail() {
        // Arrange
        mockUser("super", "SUPERUSER");
        
        // Assert
        assertFalse(tradeValidator.validateUserPrivileges("super", "INVALID_OP", validTradeDTO));
    }
    
    @Test
    void testValidateUserPrivileges_UserNotFound_ShouldFail() {
        // Arrange
        when(userRepository.findByLoginId(eq("unknown"))).thenReturn(Optional.empty());
        
        // Assert
        assertFalse(tradeValidator.validateUserPrivileges("unknown", "VIEW", null));
    }
    
    @Test
    void testValidateUserPrivileges_InactiveUser_ShouldFail() {
        // Arrange
        ApplicationUser inactiveUser = new ApplicationUser();
        inactiveUser.setLoginId("inactive");
        inactiveUser.setActive(false);
        UserProfile profile = new UserProfile();
        profile.setUserType("SUPERUSER"); 
        inactiveUser.setUserProfile(profile);
        when(userRepository.findByLoginId(eq("inactive"))).thenReturn(Optional.of(inactiveUser));
        
        // Assert
        assertFalse(tradeValidator.validateUserPrivileges("inactive", "CREATE", null));
    }

    @Test
    void testValidateLegConsistency_DifferentImplicitMaturityDates_ShouldFail() {
        // Arrange: Create two cashflows lists with different last dates
        
        // Leg 1: Matures 2025-12-31
        CashflowDTO cf1_1 = new CashflowDTO(1L, fixedLeg.getLegId(), BigDecimal.TEN, LocalDate.of(2025, 6, 30), 0.0, "PAY", null, null, null, true);
        CashflowDTO cf1_2 = new CashflowDTO(2L, fixedLeg.getLegId(), BigDecimal.TEN, LocalDate.of(2025, 12, 31), 0.0, "PAY", null, null, null, true);
        fixedLeg.setCashflows(Arrays.asList(cf1_1, cf1_2)); // Maturity is 2025-12-31

        // Leg 2: Matures 2026-06-30
        CashflowDTO cf2_1 = new CashflowDTO(3L, floatLeg.getLegId(), BigDecimal.TEN, LocalDate.of(2025, 12, 31), 0.0, "RECEIVE", null, null, null, true);
        CashflowDTO cf2_2 = new CashflowDTO(4L, floatLeg.getLegId(), BigDecimal.TEN, LocalDate.of(2026, 6, 30), 0.0, "RECEIVE", null, null, null, true);
        floatLeg.setCashflows(Arrays.asList(cf2_1, cf2_2)); // Maturity is 2026-06-30

        // Ensure other consistency rules are met
        floatLeg.setPayReceiveFlag("RECEIVE"); 
        floatLeg.setNotional(fixedLeg.getNotional());

        // Act
        ValidationResult result = tradeValidator.validateTradeLegConsistency(Arrays.asList(fixedLeg, floatLeg));

        // Assert
        assertFalse(result.isSuccessful());
        assertTrue(result.toErrorMessage().contains("Implicit Maturity Date (last cashflow value date) must be identical across both legs."));
    }

    @Test
    void testValidateLegConsistency_MissingCashflows_ShouldFail() {
        // Arrange: One leg has no cashflows
        fixedLeg.setCashflows(List.of()); 
        floatLeg.setCashflows(List.of(new CashflowDTO())); // Must also fail if one is empty

        // Act
        ValidationResult result = tradeValidator.validateTradeLegConsistency(Arrays.asList(fixedLeg, floatLeg));

        // Assert
        assertFalse(result.isSuccessful());
        assertTrue(result.toErrorMessage().contains("Cashflows must be provided on both legs to determine implicit maturity."));
    }
}