package com.technicalchallenge.validation;

import com.technicalchallenge.dto.TradeDTO;
import com.technicalchallenge.dto.TradeLegDTO;
import com.technicalchallenge.model.ApplicationUser;
import com.technicalchallenge.repository.*;
import com.technicalchallenge.util.TradeOperation;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * For validating trade data against business rules,
 * user privileges, and cross-leg consistency.
 */
@Service
public class TradeValidator {

    private final ApplicationUserRepository userRepository;
    private final BookRepository bookRepository;
    private final CounterpartyRepository counterpartyRepository;
    private final TradeStatusRepository tradeStatusRepository;
    private final TradeTypeRepository tradeTypeRepository;
    private final TradeSubTypeRepository tradeSubTypeRepository;


    public TradeValidator(
            ApplicationUserRepository userRepository,
            BookRepository bookRepository,
            CounterpartyRepository counterpartyRepository,
            CurrencyRepository currencyRepository,
            TradeStatusRepository tradeStatusRepository,
            TradeTypeRepository tradeTypeRepository,
            TradeSubTypeRepository tradeSubTypeRepository,
            LegTypeRepository legTypeRepository,
            IndexRepository indexRepository,
            HolidayCalendarRepository holidayCalendarRepository,
            ScheduleRepository scheduleRepository,
            BusinessDayConventionRepository businessDayConventionRepository,
            PayRecRepository payRecRepository) {
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
        this.counterpartyRepository = counterpartyRepository;
        this.tradeStatusRepository = tradeStatusRepository;
        this.tradeTypeRepository = tradeTypeRepository;
        this.tradeSubTypeRepository = tradeSubTypeRepository;
    }

    /**
     * Validates trade business rules for a given trade DTO.
     */
    public ValidationResult validateTradeBusinessRules(TradeDTO tradeDTO) {
        ValidationResult result = new ValidationResult();

        // Date Validation Rules
        LocalDate tradeDate = tradeDTO.getTradeDate();
        LocalDate startDate = tradeDTO.getTradeStartDate();
        LocalDate maturityDate = tradeDTO.getTradeMaturityDate();
        LocalDate today = LocalDate.now();

        // Date Validation
        if (tradeDate == null || startDate == null || maturityDate == null) {
            result.addError("Trade Date, Start Date, and Maturity Date are mandatory.");
        }

        // Rule: Maturity Date cannot be before Start Date
        if (maturityDate != null && startDate != null && maturityDate.isBefore(startDate)) {
            result.addError("Maturity Date (" + maturityDate + ") cannot be before Start Date (" + startDate + ").");
        }

        // Rule: Maturity Date cannot be before Trade Date
        if (maturityDate != null && tradeDate != null && maturityDate.isBefore(tradeDate)) {
            result.addError("Maturity Date (" + maturityDate + ") cannot be before Trade Date (" + tradeDate + ").");
        }

        // Rule: Trade Date cannot be  more than 30 days in the past
        if (tradeDate != null && ChronoUnit.DAYS.between(tradeDate, today) > 30) {
            result.addError("Trade Date (" + tradeDate + ") is more than 30 days in the past.");
        }


        // Entity Status Validation (User, Book, Counterparty)
        
        // 1. TRADER VALIDATION
        Long traderUserId = tradeDTO.getTraderUserId();
        if (traderUserId == null) {
            result.addError("Trader user ID is mandatory for status validation.");
        } else if (!userRepository.existsByIdAndActive(traderUserId, true)) {
            result.addError("Trader user is inactive or does not exist (ID: " + traderUserId + ").");
        }

        // 2. BOOK VALIDATION
        Long bookId = tradeDTO.getBookId();
        if (bookId == null) {
            result.addError("Book ID is mandatory.");
        } 
        else if (!bookRepository.existsByIdAndActive(bookId, true)) {
            result.addError("Trade Book is inactive or does not exist (ID: " + bookId + ").");
        }
        
        // 3. COUNTERPARTY VALIDATION
        Long counterpartyId = tradeDTO.getCounterpartyId();
        if (counterpartyId == null) {
            result.addError("Counterparty ID is mandatory.");
        } 
        else if (!counterpartyRepository.existsByIdAndActive(counterpartyId, true)) {
            result.addError("Counterparty is inactive or does not exist (ID: " + counterpartyId + ").");
        }
        
        // --- Reference Data Validation  ---
        
        // Check Trade Status
        if (tradeDTO.getTradeStatus() != null && !tradeStatusRepository.existsByTradeStatus(tradeDTO.getTradeStatus())) {
            result.addError("Trade Status is invalid or does not exist (Name: " + tradeDTO.getTradeStatus() + ").");
        }
        
        // Check Trade Type
        if (tradeDTO.getTradeType() != null && !tradeTypeRepository.existsByTradeType(tradeDTO.getTradeType())) {
            result.addError("Trade Type is invalid or does not exist (Name: " + tradeDTO.getTradeType() + ").");
        }
        
        // Check Trade Sub Type 
        if (tradeDTO.getTradeSubTypeId() != null && !tradeSubTypeRepository.existsById(tradeDTO.getTradeSubTypeId())) {
            result.addError("Trade Sub Type ID is invalid or does not exist (ID: " + tradeDTO.getTradeSubTypeId() + ").");
        } 
        else if (tradeDTO.getTradeSubType() != null && !tradeSubTypeRepository.findByTradeSubType(tradeDTO.getTradeSubType()).isPresent()) {
            result.addError("Trade Sub Type is invalid or does not exist (Name: " + tradeDTO.getTradeSubType() + ").");
        }

        // --- D. Trade Leg Consistency Check ---
        if (tradeDTO.getTradeLegs() != null) {
            result.addErrors(validateTradeLegConsistency(tradeDTO.getTradeLegs()).getErrors());
        }

        return result;
    }

    /**
     * Validates consistency rules across trade legs
     */
    public ValidationResult validateTradeLegConsistency(List<TradeLegDTO> legs) {
        ValidationResult result = new ValidationResult();

        if (legs == null || legs.size() != 2) {
            result.addError("A trade must contain exactly two legs.");
            return result;
        }

        TradeLegDTO leg1 = legs.get(0);
        TradeLegDTO leg2 = legs.get(1);

        // Pay/Receive Flag Consistency
        String flag1 = leg1.getPayReceiveFlag();
        String flag2 = leg2.getPayReceiveFlag();

        if (flag1 == null || flag2 == null || flag1.equals(flag2)) {
            result.addError("Cross-leg inconsistency: Both legs must have opposite Pay/Receive flags (e.g., PAY vs. RECEIVE).");
        }

        // Notional Consistency (Must be identical)
        if (leg1.getNotional() == null || leg2.getNotional() == null || leg1.getNotional().compareTo(leg2.getNotional()) != 0) {
            result.addError("Cross-leg inconsistency: Notional values must be identical across both legs.");
        }

        // Currency Consistency (Must be identical for single-currency swaps)
        String currency1 = leg1.getCurrency();
        String currency2 = leg2.getCurrency();
        if (currency1 == null || currency2 == null || !currency1.equals(currency2)) {
            result.addError("Cross-leg inconsistency: Currency must be identical across both legs for a standard swap.");
        }

        // Floating/Fixed leg requirements (Index for Float, Rate for Fixed)
        validateLegTypeSpecificRules(leg1, result, "Leg 1");
        validateLegTypeSpecificRules(leg2, result, "Leg 2");

        return result;
    }

    
    // Enforces user privileges based on their role for a specific operation.
    // The parameter 'operation' use the TradeOperation enum.
     
    public boolean validateUserPrivileges(String userId, String operation, TradeDTO tradeDTO) {
        if (userId == null || operation == null) {
            return false;
        }

        // 1. Convert the input String operation to the TradeOperation enum.
        TradeOperation tradeOperation;
        try {
            // Convert to uppercase to match enum constant names
            tradeOperation = TradeOperation.valueOf(operation.toUpperCase());
        } catch (IllegalArgumentException e) {
            // If the string doesn't match a valid enum constant, deny access
            return false;
        }

        Optional<ApplicationUser> userOpt = userRepository.findByLoginId(userId);

        if (userOpt.isEmpty()) {
            return false;
        }

        ApplicationUser user = userOpt.get();

        if (!user.isActive()) {
            return false;
        }

        if (user.getUserProfile() == null || user.getUserProfile().getUserType() == null) {
              return false;
        }

        String profileType = user.getUserProfile().getUserType().toUpperCase();

        switch (profileType) {
            case "TRADER_SALES":
                return tradeOperation == TradeOperation.CREATE 
                    || tradeOperation == TradeOperation.AMEND 
                    || tradeOperation == TradeOperation.TERMINATE 
                    || tradeOperation == TradeOperation.CANCEL 
                    || tradeOperation == TradeOperation.VIEW;
            case "MO":
                return tradeOperation == TradeOperation.AMEND 
                    || tradeOperation == TradeOperation.VIEW;
            case "SUPPORT":
                return tradeOperation == TradeOperation.VIEW; 
            case "SUPERUSER":
                // SUPERUSER: Full system access for all operations.
                return true; 
            
            // ADMIN and all other roles fall through to the default, returning false.
            default:
                return false;
        }
    }

    // Helper method to validate index/rate presence based on leg type.
     
    private void validateLegTypeSpecificRules(TradeLegDTO leg, ValidationResult result, String legLabel) {
        String legType = leg.getLegType() != null ? leg.getLegType().toUpperCase() : "";

        if (legType.equals("FLOAT")) {
            // Floating legs must have an index specified
            if (leg.getIndexName() == null || leg.getIndexName().isBlank() && leg.getIndexId() == null) {
                result.addError(legLabel + ": Floating leg missing required index specification (Name or ID).");
            }
        } else if (legType.equals("FIXED")) {
            // Fixed legs must have a rate specified
            if (leg.getRate() == null) {
                result.addError(legLabel + ": Fixed leg missing required rate specification.");
            }
        }
    }
}
