package com.technicalchallenge.service;

import com.technicalchallenge.dto.TradeDTO;
import com.technicalchallenge.dto.TradeLegDTO;
import com.technicalchallenge.exception.InsufficientPrivilegeException;
import com.technicalchallenge.exception.TradeValidationException;
import com.technicalchallenge.mapper.TradeMapper;
import com.technicalchallenge.model.*;
import com.technicalchallenge.validation.ValidationResult;

import com.technicalchallenge.repository.*;
import com.technicalchallenge.validation.TradeValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class TradeService {
    private static final Logger logger = LoggerFactory.getLogger(TradeService.class);

    @Autowired
    private TradeValidator tradeValidator;
    @Autowired
    private TradeRepository tradeRepository;
    @Autowired
    private TradeLegRepository tradeLegRepository;
    @Autowired
    private CashflowRepository cashflowRepository;
    @Autowired
    private TradeStatusRepository tradeStatusRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private CounterpartyRepository counterpartyRepository;
    @Autowired
    private ApplicationUserRepository applicationUserRepository;
    @Autowired
    private TradeTypeRepository tradeTypeRepository;
    @Autowired
    private TradeSubTypeRepository tradeSubTypeRepository;
    @Autowired
    private CurrencyRepository currencyRepository;
    @Autowired
    private LegTypeRepository legTypeRepository;
    @Autowired
    private IndexRepository indexRepository;
    @Autowired
    private HolidayCalendarRepository holidayCalendarRepository;
    @Autowired
    private ScheduleRepository scheduleRepository;
    @Autowired
    private BusinessDayConventionRepository businessDayConventionRepository;
    @Autowired
    private PayRecRepository payRecRepository;
    @Autowired
    private AdditionalInfoService additionalInfoService;
    @Autowired
    private TradeMapper tradeMapper;


    public TradeDTO getTradeDetailsByTradeId(Long tradeId) {
        logger.debug("Retrieving trade details DTO by business trade id: {}", tradeId);

        // 1. Retrieve the Trade Entity
        Trade trade = getTradeById(tradeId)
                .orElseThrow(() -> new RuntimeException("Trade not found or inactive for ID: " + tradeId));

        // 2. Map the Trade Entity to DTO
        TradeDTO tradeDTO = tradeMapper.toDto(trade);

        // 3. ENHANCE DTO with Settlement Instructions (SI is linked by the internal 'id')
        Long internalId = trade.getId();
        String instructions = additionalInfoService.getSettlementInstructions(internalId);
        
        // Set the SI value onto the dedicated DTO field
        tradeDTO.setSettlementInstructions(instructions);
        
        logger.debug("Retrieved settlement instructions for trade ID: {}", tradeId);

        return tradeDTO;
    }

    public List<Trade> getAllTrades() {
        logger.info("Retrieving all trades");
        return tradeRepository.findAll();
    }

    public Optional<Trade> getTradeById(Long tradeId) {
        logger.debug("Retrieving trade by id: {}", tradeId);
        return tradeRepository.findByTradeIdAndActiveTrue(tradeId);
    }

    @Transactional
    public Trade createTrade(TradeDTO tradeDTO) {

        // 1. Get the User ID from the Security Context
        String userId = getCurrentUserId();

        // 3. Privilege Validation
        if (!tradeValidator.validateUserPrivileges(userId, "CREATE", tradeDTO)) {
        // If the boolean is FALSE (validation fails), throw the exception
        throw new InsufficientPrivilegeException("User " + userId + " does not have privileges to create this trade.");
        }

        // 4. Business Rules Validation
        ValidationResult businessRulesResult = tradeValidator.validateTradeBusinessRules(tradeDTO);
        if (!businessRulesResult.isSuccessful()) {
            throw new TradeValidationException(businessRulesResult.getErrors());
        }

        // 5. Leg Consistency Validation
        ValidationResult legConsistencyResult = tradeValidator.validateTradeLegConsistency(tradeDTO.getTradeLegs());
        if (!legConsistencyResult.isSuccessful()) {
            throw new TradeValidationException(legConsistencyResult.getErrors());
        }
        
        logger.info("Creating new trade with ID: {}", tradeDTO.getTradeId());

        // Generate trade ID if not provided
        if (tradeDTO.getTradeId() == null) {
            // Generate sequential trade ID starting from 10000
            Long generatedTradeId = generateNextTradeId();
            tradeDTO.setTradeId(generatedTradeId);
            logger.info("Generated trade ID: {}", generatedTradeId);
        }

        // Validate business rules
        validateTradeCreation(tradeDTO);

        // Create trade entity
        Trade trade = mapDTOToEntity(tradeDTO);
        trade.setVersion(1);
        trade.setActive(true);
        trade.setCreatedDate(LocalDateTime.now());
        trade.setLastTouchTimestamp(LocalDateTime.now());

        // Set default trade status to NEW if not provided
        if (tradeDTO.getTradeStatus() == null) {
            tradeDTO.setTradeStatus("NEW");
        }

        // Populate reference data
        populateReferenceDataByName(trade, tradeDTO);

        // Ensure we have essential reference data
        validateReferenceData(trade);

        Trade savedTrade = tradeRepository.save(trade);

        // Create trade legs and cashflows
        createTradeLegsWithCashflows(tradeDTO, savedTrade);

        // START INTEGRATION: SAVE SETTLEMENT INSTRUCTIONS IF PROVIDED
        if (tradeDTO.getSettlementInstructions() != null && !tradeDTO.getSettlementInstructions().isBlank()) {
            additionalInfoService.saveSettlementInstructions(
                savedTrade.getId(), 
                tradeDTO.getSettlementInstructions()
            );
            logger.debug("Saved settlement instructions for trade ID: {}", savedTrade.getTradeId());
        }
        // END INTEGRATION 

        logger.info("Successfully created trade with ID: {}", savedTrade.getTradeId());
        return savedTrade;
    }

    // NEW METHOD: For controller compatibility
    @Transactional
    public Trade saveTrade(Trade trade, TradeDTO tradeDTO) {
        logger.info("Saving trade with ID: {}", trade.getTradeId());

        // If this is an existing trade (has ID), handle as amendment
        if (trade.getId() != null) {
            return amendTrade(trade.getTradeId(), tradeDTO);
        } else {
            return createTrade(tradeDTO);
        }
    }

    // FIXED: Populate reference data by names from DTO
    public void populateReferenceDataByName(Trade trade, TradeDTO tradeDTO) {
        logger.debug("Populating reference data for trade");

        // Populate Book
        if (tradeDTO.getBookName() != null) {
            bookRepository.findByBookName(tradeDTO.getBookName())
                    .ifPresent(trade::setBook);
        } else if (tradeDTO.getBookId() != null) {
            bookRepository.findById(tradeDTO.getBookId())
                    .ifPresent(trade::setBook);
        }

        // Populate Counterparty
        if (tradeDTO.getCounterpartyName() != null) {
            counterpartyRepository.findByName(tradeDTO.getCounterpartyName())
                    .ifPresent(trade::setCounterparty);
        } else if (tradeDTO.getCounterpartyId() != null) {
            counterpartyRepository.findById(tradeDTO.getCounterpartyId())
                    .ifPresent(trade::setCounterparty);
        }

        // Populate TradeStatus
        if (tradeDTO.getTradeStatus() != null) {
            tradeStatusRepository.findByTradeStatus(tradeDTO.getTradeStatus())
                    .ifPresent(trade::setTradeStatus);
        } else if (tradeDTO.getTradeStatusId() != null) {
            tradeStatusRepository.findById(tradeDTO.getTradeStatusId())
                    .ifPresent(trade::setTradeStatus);
        }

        // Populate other reference data
        populateUserReferences(trade, tradeDTO);
        populateTradeTypeReferences(trade, tradeDTO);
    }

    private void populateUserReferences(Trade trade, TradeDTO tradeDTO) {
        // Handle trader user by name or ID with enhanced logging
        if (tradeDTO.getTraderUserName() != null) {
            logger.debug("Looking up trader user by name: {}", tradeDTO.getTraderUserName());
            String[] nameParts = tradeDTO.getTraderUserName().trim().split("\\s+");
            if (nameParts.length >= 1) {
                String firstName = nameParts[0];
                logger.debug("Searching for user with firstName: {}", firstName);
                Optional<ApplicationUser> userOpt = applicationUserRepository.findByFirstName(firstName);
                if (userOpt.isPresent()) {
                    trade.setTraderUser(userOpt.get());
                    logger.debug("Found trader user: {} {}", userOpt.get().getFirstName(), userOpt.get().getLastName());
                } else {
                    logger.warn("Trader user not found with firstName: {}", firstName);
                    // Try with loginId as fallback
                    Optional<ApplicationUser> byLoginId = applicationUserRepository.findByLoginId(tradeDTO.getTraderUserName().toLowerCase());
                    if (byLoginId.isPresent()) {
                        trade.setTraderUser(byLoginId.get());
                        logger.debug("Found trader user by loginId: {}", tradeDTO.getTraderUserName());
                    } else {
                        logger.warn("Trader user not found by loginId either: {}", tradeDTO.getTraderUserName());
                    }
                }
            }
        } else if (tradeDTO.getTraderUserId() != null) {
            applicationUserRepository.findById(tradeDTO.getTraderUserId())
                    .ifPresent(trade::setTraderUser);
        }

        // Handle inputter user by name or ID with enhanced logging
        if (tradeDTO.getInputterUserName() != null) {
            logger.debug("Looking up inputter user by name: {}", tradeDTO.getInputterUserName());
            String[] nameParts = tradeDTO.getInputterUserName().trim().split("\\s+");
            if (nameParts.length >= 1) {
                String firstName = nameParts[0];
                logger.debug("Searching for inputter with firstName: {}", firstName);
                Optional<ApplicationUser> userOpt = applicationUserRepository.findByFirstName(firstName);
                if (userOpt.isPresent()) {
                    trade.setTradeInputterUser(userOpt.get());
                    logger.debug("Found inputter user: {} {}", userOpt.get().getFirstName(), userOpt.get().getLastName());
                } else {
                    logger.warn("Inputter user not found with firstName: {}", firstName);
                    // Try with loginId as fallback
                    Optional<ApplicationUser> byLoginId = applicationUserRepository.findByLoginId(tradeDTO.getInputterUserName().toLowerCase());
                    if (byLoginId.isPresent()) {
                        trade.setTradeInputterUser(byLoginId.get());
                        logger.debug("Found inputter user by loginId: {}", tradeDTO.getInputterUserName());
                    } else {
                        logger.warn("Inputter user not found by loginId either: {}", tradeDTO.getInputterUserName());
                    }
                }
            }
        } else if (tradeDTO.getTradeInputterUserId() != null) {
            applicationUserRepository.findById(tradeDTO.getTradeInputterUserId())
                    .ifPresent(trade::setTradeInputterUser);
        }
    }

    private void populateTradeTypeReferences(Trade trade, TradeDTO tradeDTO) {
        if (tradeDTO.getTradeType() != null) {
            logger.debug("Looking up trade type: {}", tradeDTO.getTradeType());
            Optional<TradeType> tradeTypeOpt = tradeTypeRepository.findByTradeType(tradeDTO.getTradeType());
            if (tradeTypeOpt.isPresent()) {
                trade.setTradeType(tradeTypeOpt.get());
                logger.debug("Found trade type: {} with ID: {}", tradeTypeOpt.get().getTradeType(), tradeTypeOpt.get().getId());
            } else {
                logger.warn("Trade type not found: {}", tradeDTO.getTradeType());
            }
        } else if (tradeDTO.getTradeTypeId() != null) {
            tradeTypeRepository.findById(tradeDTO.getTradeTypeId())
                    .ifPresent(trade::setTradeType);
        }

        if (tradeDTO.getTradeSubType() != null) {
            Optional<TradeSubType> tradeSubTypeOpt = tradeSubTypeRepository.findByTradeSubType(tradeDTO.getTradeSubType());
            if (tradeSubTypeOpt.isPresent()) {
                trade.setTradeSubType(tradeSubTypeOpt.get());
            } else {
                List<TradeSubType> allSubTypes = tradeSubTypeRepository.findAll();
                for (TradeSubType subType : allSubTypes) {
                    if (subType.getTradeSubType().equalsIgnoreCase(tradeDTO.getTradeSubType())) {
                        trade.setTradeSubType(subType);
                        break;
                    }
                }
            }
        } else if (tradeDTO.getTradeSubTypeId() != null) {
            tradeSubTypeRepository.findById(tradeDTO.getTradeSubTypeId())
                    .ifPresent(trade::setTradeSubType);
        }
    }

    // NEW METHOD: Delete trade (mark as cancelled)
    @Transactional
    public void deleteTrade(Long tradeId) {
        logger.info("Deleting (cancelling) trade with ID: {}", tradeId);
        cancelTrade(tradeId);
    }

    @Transactional
    public Trade amendTrade(Long tradeId, TradeDTO tradeDTO) {
        // 1. Get the User ID from the Security Context
        String userId = getCurrentUserId();
    
        // 2. PRIVILEGE CHECK
        if (!tradeValidator.validateUserPrivileges(userId, "AMEND", tradeDTO)) {
            throw new InsufficientPrivilegeException("User " + userId + " does not have privileges to amend this trade.");
        }

        // 3. BUSINESS RULE VALIDATION
        ValidationResult businessRulesResult = tradeValidator.validateTradeBusinessRules(tradeDTO);
        if (!businessRulesResult.isSuccessful()) {
            throw new TradeValidationException(businessRulesResult.getErrors());
        }

        // 4. LEG CONSISTENCY VALIDATION
        ValidationResult legConsistencyResult = tradeValidator.validateTradeLegConsistency(tradeDTO.getTradeLegs());
        if (!legConsistencyResult.isSuccessful()) {
            throw new TradeValidationException(legConsistencyResult.getErrors());
        }    

        logger.info("Amending trade with ID: {}", tradeId);

        Optional<Trade> existingTradeOpt = getTradeById(tradeId);
        if (existingTradeOpt.isEmpty()) {
            throw new RuntimeException("Trade not found: " + tradeId);
        }

        Trade existingTrade = existingTradeOpt.get();

        // Deactivate existing trade
        existingTrade.setActive(false);
        existingTrade.setDeactivatedDate(LocalDateTime.now());
        tradeRepository.save(existingTrade);

        // Create new version
        Trade amendedTrade = mapDTOToEntity(tradeDTO);
        amendedTrade.setTradeId(tradeId);
        amendedTrade.setVersion(existingTrade.getVersion() + 1);
        amendedTrade.setActive(true);
        amendedTrade.setCreatedDate(LocalDateTime.now());
        amendedTrade.setLastTouchTimestamp(LocalDateTime.now());

        // Populate reference data
        populateReferenceDataByName(amendedTrade, tradeDTO);

        // Set status to AMENDED
        TradeStatus amendedStatus = tradeStatusRepository.findByTradeStatus("AMENDED")
                .orElseThrow(() -> new RuntimeException("AMENDED status not found"));
        amendedTrade.setTradeStatus(amendedStatus);

        Trade savedTrade = tradeRepository.save(amendedTrade);

        // Create new trade legs and cashflows
        createTradeLegsWithCashflows(tradeDTO, savedTrade);

        // START INTEGRATION: SAVE/UPDATE SETTLEMENT INSTRUCTIONS
        String newInstructions = tradeDTO.getSettlementInstructions();
        
        // Check if instructions were provided in the DTO
        if (newInstructions != null && !newInstructions.isBlank()) {

            // ACCESS CONTROL CHECK for Settlement Instructions
            // Only TRADER_SALES can edit SI, even during general amendment.
            if (tradeValidator.hasAnyRole(userId, "TRADER_SALES")) {

                // Validate content security before persisting (e.g., no forbidden special chars)
                validateSettlementInstructionsContent(newInstructions);

                // Delegate to service for persistence (deactivates old, creates new for audit)
                additionalInfoService.saveSettlementInstructions(
                    savedTrade.getId(), 
                    newInstructions
                );
                logger.debug("Updated settlement instructions for new amended trade ID: {}", savedTrade.getTradeId());
            } else {
                logger.warn("User {} attempted to amend SI without TRADER/SALES role. SI update skipped.", userId);
                // If the user provided SI but lacked permission,simply skip saving SI
                // to allow the general trade amendment (e.g., date change) to proceed.
            }
        }
        // END INTEGRATION
        
            logger.info("Successfully amended trade with ID: {}", savedTrade.getTradeId());
            return savedTrade;
    }

    /**
     * Updates the settlement instructions for the latest active version of a trade.
     * This triggers a deactivation of the old SI record and creation of a new one 
     * via the AdditionalInfoService, ensuring an audit trail.
     * @param tradeId The business ID of the trade.
     * @param instructions The new settlement instructions text.
     * @return The updated Trade entity.
     */
    @Transactional
    @PreAuthorize("hasAnyAuthority('ROLE_TRADER_SALES')")
    public Trade updateTradeSettlementInstructions(Long tradeId, String instructions) {

        // Get the User ID from the Security Context
        String userId = getCurrentUserId();

        logger.info("Starting update of settlement instructions for trade ID: {}, by user ID: {}", tradeId, userId);

        // 1. Find the active trade by ID
        Trade trade = getTradeById(tradeId)
                .orElseThrow(() -> new RuntimeException("Trade not found or inactive for ID: " + tradeId));

        // 2. Content Security Validation (Enforces no special characters that cause security issues)
        validateSettlementInstructionsContent(instructions);

        // 3. Delegate to AdditionalInfoService for persistence and audit
        // The service handles deactivation of the old SI and creation of the new SI record.
        additionalInfoService.saveSettlementInstructions(
            trade.getId(), // Use the internal primary key
            instructions
        );

        // 4. Update the trade's last touch timestamp
        trade.setLastTouchTimestamp(LocalDateTime.now());
        tradeRepository.save(trade);

        logger.info("Successfully updated SI for trade ID: {}", tradeId);
        return trade;
    }

    /**
     * Helper method for content security validation.
     */
    private void validateSettlementInstructionsContent(String instructions) {
        // The instructions passed here has already passed the DTO @Size check (10-500)
        if (instructions == null || instructions.trim().isEmpty()) {
            return;
        }

        // Regex to allow standard alphanumeric, spaces, and common financial symbols/punctuation:
        // [a-zA-Z0-9\s.,:/\-()#$&%*+'] (Strictly excludes <, >, ;, which are common injection threats)
        String safePattern = "^[a-zA-Z0-9\\s.,:\\-/()#$&%*+']{10,500}$";
        
        if (!instructions.matches(safePattern)) {
            logger.warn("Settlement instruction validation failed. Content: {}", instructions);
            throw new TradeValidationException("Settlement instructions contain prohibited special characters or do not meet length requirements.");
        }
    }

    @Transactional
    public Trade terminateTrade(Long tradeId) {

        // 1. Get the User ID from the Security Context
        String userId = getCurrentUserId();

        // 2. PRIVILEGE CHECK
        if (!tradeValidator.validateUserPrivileges(userId, "TERMINATE", null)) { // tradeDTO can be null for this check
            throw new InsufficientPrivilegeException("User " + userId + " does not have privileges to terminate this trade.");
        }
        logger.info("Terminating trade with ID: {}", tradeId);

        Optional<Trade> tradeOpt = getTradeById(tradeId);
        if (tradeOpt.isEmpty()) {
            throw new RuntimeException("Trade not found: " + tradeId);
        }

        Trade trade = tradeOpt.get();
        TradeStatus terminatedStatus = tradeStatusRepository.findByTradeStatus("TERMINATED")
                .orElseThrow(() -> new RuntimeException("TERMINATED status not found"));

        trade.setTradeStatus(terminatedStatus);
        trade.setLastTouchTimestamp(LocalDateTime.now());

        return tradeRepository.save(trade);
    }

    @Transactional
    public Trade cancelTrade(Long tradeId) {

        /// 1. Get the User ID from the Security Context
        String userId = getCurrentUserId();

        // 2. PRIVILEGE CHECK Use "TERMINATE" operation
        if (!tradeValidator.validateUserPrivileges(userId, "TERMINATE", null)) { // tradeDTO can be null for this check
            throw new InsufficientPrivilegeException("User " + userId + " does not have privileges to terminate this trade.");
        }
        logger.info("Cancelling trade with ID: {}", tradeId);

        Optional<Trade> tradeOpt = getTradeById(tradeId);
        if (tradeOpt.isEmpty()) {
            throw new RuntimeException("Trade not found: " + tradeId);
        }

        Trade trade = tradeOpt.get();
        TradeStatus cancelledStatus = tradeStatusRepository.findByTradeStatus("CANCELLED")
                .orElseThrow(() -> new RuntimeException("CANCELLED status not found"));

        trade.setTradeStatus(cancelledStatus);
        trade.setLastTouchTimestamp(LocalDateTime.now());

        return tradeRepository.save(trade);
    }

    private void validateTradeCreation(TradeDTO tradeDTO) {
        // Validate dates - Fixed to use consistent field names
        if (tradeDTO.getTradeStartDate() != null && tradeDTO.getTradeDate() != null) {
            if (tradeDTO.getTradeStartDate().isBefore(tradeDTO.getTradeDate())) {
                throw new RuntimeException("Start date cannot be before trade date");
            }
        }
        if (tradeDTO.getTradeMaturityDate() != null && tradeDTO.getTradeStartDate() != null) {
            if (tradeDTO.getTradeMaturityDate().isBefore(tradeDTO.getTradeStartDate())) {
                throw new RuntimeException("Maturity date cannot be before start date");
            }
        }

        // Validate trade has exactly 2 legs
        if (tradeDTO.getTradeLegs() == null || tradeDTO.getTradeLegs().size() != 2) {
            throw new RuntimeException("Trade must have exactly 2 legs");
        }
    }

    private Trade mapDTOToEntity(TradeDTO dto) {
        Trade trade = new Trade();
        trade.setTradeId(dto.getTradeId());
        trade.setTradeDate(dto.getTradeDate()); // Fixed field names
        trade.setTradeStartDate(dto.getTradeStartDate());
        trade.setTradeMaturityDate(dto.getTradeMaturityDate());
        trade.setTradeExecutionDate(dto.getTradeExecutionDate());
        trade.setUtiCode(dto.getUtiCode());
        trade.setValidityStartDate(dto.getValidityStartDate());
        trade.setLastTouchTimestamp(LocalDateTime.now());
        return trade;
    }

    private void createTradeLegsWithCashflows(TradeDTO tradeDTO, Trade savedTrade) {
        for (int i = 0; i < tradeDTO.getTradeLegs().size(); i++) {
            var legDTO = tradeDTO.getTradeLegs().get(i);

            TradeLeg tradeLeg = new TradeLeg();
            tradeLeg.setTrade(savedTrade);
            tradeLeg.setNotional(legDTO.getNotional());
            tradeLeg.setRate(legDTO.getRate());
            tradeLeg.setActive(true);
            tradeLeg.setCreatedDate(LocalDateTime.now());

            // Populate reference data for leg
            populateLegReferenceData(tradeLeg, legDTO);

            TradeLeg savedLeg = tradeLegRepository.save(tradeLeg);

            // Generate cashflows for this leg
            if (tradeDTO.getTradeStartDate() != null && tradeDTO.getTradeMaturityDate() != null) {
                generateCashflows(savedLeg, tradeDTO.getTradeStartDate(), tradeDTO.getTradeMaturityDate());
            }
        }
    }

    private void populateLegReferenceData(TradeLeg leg, TradeLegDTO legDTO) {
        // Populate currency by name or ID
        if (legDTO.getCurrency() != null) {
            currencyRepository.findByCurrency(legDTO.getCurrency())
                    .ifPresent(leg::setCurrency);
        } else if (legDTO.getCurrencyId() != null) {
            currencyRepository.findById(legDTO.getCurrencyId())
                    .ifPresent(leg::setCurrency);
        }

        // Populate leg type by name or ID
        if (legDTO.getLegType() != null) {
            legTypeRepository.findByType(legDTO.getLegType())
                    .ifPresent(leg::setLegRateType);
        } else if (legDTO.getLegTypeId() != null) {
            legTypeRepository.findById(legDTO.getLegTypeId())
                    .ifPresent(leg::setLegRateType);
        }

        // Populate index by name or ID
        if (legDTO.getIndexName() != null) {
            indexRepository.findByIndex(legDTO.getIndexName())
                    .ifPresent(leg::setIndex);
        } else if (legDTO.getIndexId() != null) {
            indexRepository.findById(legDTO.getIndexId())
                    .ifPresent(leg::setIndex);
        }

        // Populate holiday calendar by name or ID
        if (legDTO.getHolidayCalendar() != null) {
            holidayCalendarRepository.findByHolidayCalendar(legDTO.getHolidayCalendar())
                    .ifPresent(leg::setHolidayCalendar);
        } else if (legDTO.getHolidayCalendarId() != null) {
            holidayCalendarRepository.findById(legDTO.getHolidayCalendarId())
                    .ifPresent(leg::setHolidayCalendar);
        }

        // Populate schedule by name or ID
        if (legDTO.getCalculationPeriodSchedule() != null) {
            scheduleRepository.findBySchedule(legDTO.getCalculationPeriodSchedule())
                    .ifPresent(leg::setCalculationPeriodSchedule);
        } else if (legDTO.getScheduleId() != null) {
            scheduleRepository.findById(legDTO.getScheduleId())
                    .ifPresent(leg::setCalculationPeriodSchedule);
        }

        // Populate payment business day convention by name or ID
        if (legDTO.getPaymentBusinessDayConvention() != null) {
            businessDayConventionRepository.findByBdc(legDTO.getPaymentBusinessDayConvention())
                    .ifPresent(leg::setPaymentBusinessDayConvention);
        } else if (legDTO.getPaymentBdcId() != null) {
            businessDayConventionRepository.findById(legDTO.getPaymentBdcId())
                    .ifPresent(leg::setPaymentBusinessDayConvention);
        }

        // Populate fixing business day convention by name or ID
        if (legDTO.getFixingBusinessDayConvention() != null) {
            businessDayConventionRepository.findByBdc(legDTO.getFixingBusinessDayConvention())
                    .ifPresent(leg::setFixingBusinessDayConvention);
        } else if (legDTO.getFixingBdcId() != null) {
            businessDayConventionRepository.findById(legDTO.getFixingBdcId())
                    .ifPresent(leg::setFixingBusinessDayConvention);
        }

        // Populate pay/receive flag by name or ID
        if (legDTO.getPayReceiveFlag() != null) {
            payRecRepository.findByPayRec(legDTO.getPayReceiveFlag())
                    .ifPresent(leg::setPayReceiveFlag);
        } else if (legDTO.getPayRecId() != null) {
            payRecRepository.findById(legDTO.getPayRecId())
                    .ifPresent(leg::setPayReceiveFlag);
        }
    }

    /**
     * FIXED: Generate cashflows based on schedule and maturity date
     */
    private void generateCashflows(TradeLeg leg, LocalDate startDate, LocalDate maturityDate) {
        logger.info("Generating cashflows for leg {} from {} to {}", leg.getLegId(), startDate, maturityDate);

        // Use default schedule if not set
        String schedule = "3M"; // Default to quarterly
        if (leg.getCalculationPeriodSchedule() != null) {
            schedule = leg.getCalculationPeriodSchedule().getSchedule();
        }

        int monthsInterval = parseSchedule(schedule);
        List<LocalDate> paymentDates = calculatePaymentDates(startDate, maturityDate, monthsInterval);

        for (LocalDate paymentDate : paymentDates) {
            Cashflow cashflow = new Cashflow();
            cashflow.setTradeLeg(leg); // Fixed field name
            cashflow.setValueDate(paymentDate);
            cashflow.setRate(leg.getRate());

            // Calculate value based on leg type
            BigDecimal cashflowValue = calculateCashflowValue(leg, monthsInterval);
            cashflow.setPaymentValue(cashflowValue);

            cashflow.setPayRec(leg.getPayReceiveFlag());
            cashflow.setPaymentBusinessDayConvention(leg.getPaymentBusinessDayConvention());
            cashflow.setCreatedDate(LocalDateTime.now());
            cashflow.setActive(true);

            cashflowRepository.save(cashflow);
        }

        logger.info("Generated {} cashflows for leg {}", paymentDates.size(), leg.getLegId());
    }

    private int parseSchedule(String schedule) {
        if (schedule == null || schedule.trim().isEmpty()) {
            return 3; // Default to quarterly
        }

        schedule = schedule.trim();

        // Handle common schedule names
        switch (schedule.toLowerCase()) {
            case "monthly":
                return 1;
            case "quarterly":
                return 3;
            case "semi-annually":
            case "semiannually":
            case "half-yearly":
                return 6;
            case "annually":
            case "yearly":
                return 12;
            default:
                // Parse "1M", "3M", "12M" format
                if (schedule.endsWith("M") || schedule.endsWith("m")) {
                    try {
                        return Integer.parseInt(schedule.substring(0, schedule.length() - 1));
                    } catch (NumberFormatException e) {
                        throw new RuntimeException("Invalid schedule format: " + schedule);
                    }
                }
                throw new RuntimeException("Invalid schedule format: " + schedule + ". Supported formats: Monthly, Quarterly, Semi-annually, Annually, or 1M, 3M, 6M, 12M");
        }
    }

    private List<LocalDate> calculatePaymentDates(LocalDate startDate, LocalDate maturityDate, int monthsInterval) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate currentDate = startDate.plusMonths(monthsInterval);

        while (!currentDate.isAfter(maturityDate)) {
            dates.add(currentDate);
            currentDate = currentDate.plusMonths(monthsInterval);
        }

        return dates;
    }

    private BigDecimal calculateCashflowValue(TradeLeg leg, int monthsInterval) {
        if (leg.getLegRateType() == null) {
            return BigDecimal.ZERO;
        }

        String legType = leg.getLegRateType().getType();

        if ("Fixed".equals(legType)) {
            // Get the notional directly as a BigDecimal
            BigDecimal notional = leg.getNotional();
            
            // Get the rate as a primitive for the conversion logic
            double ratePrimitive = leg.getRate();

            // Convert the rate to a decimal (3.5% -> 0.035).
            // Convert it to BigDecimal after dividing by 100.
            BigDecimal rate = BigDecimal.valueOf(ratePrimitive).divide(
                new BigDecimal(100), 
                10, // Define a scale for precision after division
                RoundingMode.HALF_UP
            );
            
            // Convert the primitive 'monthsInterval' into BigDecimal for calculations.
            BigDecimal months = new BigDecimal(monthsInterval);

            // CORRECT CALCULATION LOGIC since it's now BigDecimal
            // Formula: (Notional * Rate * Months) / 12
            
            // Calculate (Notional * Rate)
            BigDecimal cashflowBase = notional.multiply(rate);

            // Calculate (CashflowBase * Months)
            BigDecimal cashflowValue = cashflowBase.multiply(months);

            // Calculate (Result / 12)
            return cashflowValue.divide(
                new BigDecimal(12), 
                10, 
                RoundingMode.HALF_UP
            );

        } else if ("Floating".equals(legType)) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.ZERO;
    }

    private void validateReferenceData(Trade trade) {
        // Validate essential reference data is populated
        if (trade.getBook() == null) {
            throw new RuntimeException("Book not found or not set");
        }
        if (trade.getCounterparty() == null) {
            throw new RuntimeException("Counterparty not found or not set");
        }
        if (trade.getTradeStatus() == null) {
            throw new RuntimeException("Trade status not found or not set");
        }

        logger.debug("Reference data validation passed for trade");
    }

    // NEW METHOD: Generate the next trade ID (sequential)
    private Long generateNextTradeId() {
        // For simplicity, using a static variable. In real scenario, this should be atomic and thread-safe.
        return 10000L + tradeRepository.count();
    }
    
    // Pagination support for trades
    public Page<Trade> findAll(Pageable pageable) {
        // Uses the built-in findAll(Pageable) method provided by JpaRepository
        return tradeRepository.findAll(pageable);
    }
    
    public Page<Trade> searchTrades(Specification<Trade> spec, Pageable pageable) {
        return tradeRepository.findAll(spec, pageable);
    }

    /**
     * Searches for active trades by matching a partial string against their
     * active Settlement Instructions (stored in AdditionalInfo table).
     * @param instructions The partial text to search for.
     * @return List of matching Trade entities.
     */
    @Transactional(readOnly = true)
    public List<Trade> searchTradesBySettlementInstructions(String instructions) {
        logger.info("Searching active trades by SI content: '{}'", instructions);
        
        if (instructions == null || instructions.isBlank()) {
            // Return an empty list if the search query is empty
            return List.of(); 
        }

        // --- START VALIDATION / SANITIZATION FOR SEARCH ---
    
        // 1. Length Check: Limit the search term length to prevent abuse/performance issues
        if (instructions.length() > 200) { // e.g., max 200 chars for a search query
            instructions = instructions.substring(0, 200);
            logger.warn("Search query was truncated to 200 characters to prevent abuse.");
        }
        
        // 2. Sanitize Content: Remove characters that could be malicious or interfere
        //    with the JPA/SQL LIKE clause, while keeping necessary search characters.
        //    (Example: Allow alphanumeric, spaces, dashes, commas. Remove quotes, semicolons, etc.)
        //    The underlying query uses UPPER() and LIKE '%%', direct injection is harder,
        //    but sanitization is still a good defensive layer.
        String sanitizedInstructions = instructions.replaceAll("[^a-zA-Z0-9\\s.,-]", ""); 
        
        // Use the sanitized string for the search
        if (sanitizedInstructions.isBlank()) {
        return List.of();
    }
    
        // --- END VALIDATION / SANITIZATION FOR SEARCH ---

        // REPOSITORY METHOD CALL using sanitized string
        return tradeRepository.findActiveTradesBySettlementInstructionsContaining(sanitizedInstructions);
    }


    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    
    if (authentication == null) {
        logger.warn("No Authentication object found in SecurityContext.");
        return "anonymous"; 
    }
    
    Object principal = authentication.getPrincipal();

    if (principal instanceof UserDetails) {
        return ((UserDetails) principal).getUsername();
    } 
    
    if (principal != null) {
        return principal.toString(); 
    }
    
    logger.warn("Principal object is null or of unknown type.");
    return "anonymous";
    }
}