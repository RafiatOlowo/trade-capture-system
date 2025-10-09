package com.technicalchallenge.controller;

import com.technicalchallenge.dto.TradeDTO;
import com.technicalchallenge.mapper.TradeMapper;
import com.technicalchallenge.model.Trade;
import com.technicalchallenge.service.TradeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import io.github.perplexhub.rsql.RSQLSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.persistence.criteria.Predicate;

import java.time.LocalDate;
import java.util.List;

import org.aspectj.apache.bcel.classfile.Code;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;

@RestController
@RequestMapping("/api/trades")
@Validated
@Tag(name = "Trades", description = "Trade management operations including booking, searching, and lifecycle management")
public class TradeController {
    private static final Logger logger = LoggerFactory.getLogger(TradeController.class);

    @Autowired
    private TradeService tradeService;
    @Autowired
    private TradeMapper tradeMapper;

    @GetMapping
    @Operation(summary = "Get all trades",
               description = "Retrieves a list of all trades in the system. Returns comprehensive trade information including legs and cashflows.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved all trades",
                    content = @Content(mediaType = "application/json",
                                     schema = @Schema(implementation = TradeDTO.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public List<TradeDTO> getAllTrades() {
        logger.info("Fetching all trades");
        return tradeService.getAllTrades().stream()
                .map(tradeMapper::toDto)
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get trade by ID",
               description = "Retrieves a specific trade by its unique identifier")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Trade found and returned successfully",
                    content = @Content(mediaType = "application/json",
                                     schema = @Schema(implementation = TradeDTO.class))),
        @ApiResponse(responseCode = "404", description = "Trade not found"),
        @ApiResponse(responseCode = "400", description = "Invalid trade ID format")
    })
    public ResponseEntity<TradeDTO> getTradeById(
            @Parameter(description = "Unique identifier of the trade", required = true)
            @PathVariable(name = "id") Long id) {
        logger.debug("Fetching trade by id: {}", id);
        return tradeService.getTradeById(id)
                .map(tradeMapper::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Search Endpoint
    @GetMapping("/search")
    @Operation(
        summary = "Multi-Criteria Trade Search",
        description = "Retrieves a paginated list of trades by filtering on any of these fields: Counterparty, Book, Trader, Status, and Date Ranges (start and end dates)."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Trades retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameter(s)"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Page<TradeDTO>> simpleSearch(
            @RequestParam(value = "counterparty", required = false) String counterpartyName,
            @RequestParam(value = "book", required = false) String bookName,
            @RequestParam(value = "trader", required = false) Long traderId,
            @RequestParam(value = "status", required = false) String tradeStatus,
            @RequestParam(value = "startDate", required = false) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) LocalDate endDate,
            Pageable pageable) {

        logger.info("Starting multi-criteria search. Params: [Counterparty: {}, Book: {}, Trader: {}, Status: {}, Startdate: {}, Enddate: {}]",
                counterpartyName, bookName, traderId, tradeStatus, startDate, endDate);
        Specification<Trade> specification = Specification.where(null);
        boolean filtersApplied = false;

        // 1. Trader Filter (Direct JPA Specification)
        if (traderId != null) {
            specification = specification.and((root, query, criteriaBuilder) -> 
                criteriaBuilder.equal(root.get("traderUser").get("id"), traderId)
            );
            filtersApplied = true;
        }
        
        // 2. Counterparty Filter (Direct JPA Specification)
        if (counterpartyName != null && !counterpartyName.isBlank()) {
            specification = specification.and((root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("counterparty").get("name"), counterpartyName)
            );
            filtersApplied = true;
        }
        
        // 3. Book Filter (Direct JPA Specification)
        if (bookName != null && !bookName.isBlank()) {
            specification = specification.and((root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("book").get("bookName"), bookName)
            );
            filtersApplied = true;
        }

        // 4. Status Filter (Direct JPA Specification)
        if (tradeStatus != null && !tradeStatus.isBlank()) {
            specification = specification.and((root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("tradeStatus").get("tradeStatus"), tradeStatus)
            );
            filtersApplied = true;
        }

        // 5. Date Range Filter (Direct JPA Specification)
        if (startDate != null || endDate != null) {
            specification = specification.and((root, query, criteriaBuilder) -> {
                Predicate datePredicate = criteriaBuilder.conjunction(); 
                
                if (startDate != null) {
                    datePredicate = criteriaBuilder.and(datePredicate, criteriaBuilder.greaterThanOrEqualTo(root.get("tradeDate"), startDate));
                }
                
                if (endDate != null) {
                    datePredicate = criteriaBuilder.and(datePredicate, criteriaBuilder.lessThanOrEqualTo(root.get("tradeDate"), endDate));
                }
                
                return datePredicate;
            });
            filtersApplied = true;
        }
        
        // Execute Search Logic
        
        // If no filter parameters were passed, use findAll
        if (!filtersApplied) {
            Page<Trade> tradePage = tradeService.findAll(pageable);
            return ResponseEntity.ok(tradePage.map(tradeMapper::toDto));
        }
        
        // Executes the final combined Specification.
        Page<Trade> tradePage = tradeService.searchTrades(specification, pageable);
        
        return ResponseEntity.ok(tradePage.map(tradeMapper::toDto));
    }
    
    // Filter Endpoint (Pagination Only)
    @GetMapping("/filter")
    @Operation(
        summary = "Paginated Retrieval of All Trades",
        description = "Retrieves all trades with optional pagination and sorting"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Trades retrieved successfully with pagination"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Page<TradeDTO>> paginatedFilter(Pageable pageable) {

        logger.info("Fetching all trades with pagination: {}", pageable);
        
        // Calls the simple findAll(Pageable) method
        Page<Trade> tradePage = tradeService.findAll(pageable);
        
        Page<TradeDTO> dtoPage = tradePage.map(tradeMapper::toDto);
        
        return ResponseEntity.ok(dtoPage);
    }        

    // RSQL Endpoint
    // Implement security later.
    @GetMapping("/rsql")
    @Operation(
        summary = "Advanced Trade Search using RSQL (REST Query Language)",
        description = "Allows power users to construct complex filtering queries using RSQL syntax (e.g., 'counterparty.name==ABC;tradeDate=gt=2024-01-01'). Results are paginated."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Trades retrieved successfully"),   
        @ApiResponse(responseCode = "400", description = "Invalid RSQL syntax"),
        @ApiResponse(responseCode = "500", description = "Internal server error") 
    })
    public ResponseEntity<Page<TradeDTO>> advancedSearch(
            @RequestParam(value = "query", required = false, defaultValue = "") String rsqlQuery,
            Pageable pageable) {
        
        logger.info("Starting RSQL trade search with query: '{}'", rsqlQuery);

        // If no query is provided, call the paginated find 
        if (rsqlQuery.isEmpty()) {
            Page<Trade> tradePage = tradeService.findAll(pageable);
            return ResponseEntity.ok(tradePage.map(tradeMapper::toDto));
        }

        // Otherwise, execute the RSQL Specification search.
         Specification<Trade> specification = RSQLSupport.toSpecification(rsqlQuery);
         Page<Trade> tradePage = tradeService.searchTrades(specification, pageable);
         return ResponseEntity.ok(tradePage.map(tradeMapper::toDto));
    }

    @PostMapping
    @Operation(summary = "Create new trade",
               description = "Creates a new trade with the provided details. Automatically generates cashflows and validates business rules.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Trade created successfully",
                    content = @Content(mediaType = "application/json",
                                     schema = @Schema(implementation = TradeDTO.class))),
        @ApiResponse(responseCode = "400", description = "Invalid trade data or business rule violation"),
        @ApiResponse(responseCode = "500", description = "Internal server error during trade creation")
    })
    public ResponseEntity<?> createTrade(
            @Parameter(description = "Trade details for creation", required = true)
            @Valid @RequestBody TradeDTO tradeDTO) {
        logger.info("Creating new trade: {}", tradeDTO);
        try {
            // Manual validation for Trade Date presence
            if (tradeDTO.getTradeDate() == null) {
            return ResponseEntity.badRequest().body("Trade date is required");
            }

            // Manual validation for Book and Counterparty presence
            if (tradeDTO.getBookName() == null || tradeDTO.getCounterpartyName() == null) {
            return ResponseEntity.badRequest().body("Book and Counterparty are required");
            }
            Trade trade = tradeMapper.toEntity(tradeDTO);
            tradeService.populateReferenceDataByName(trade, tradeDTO);
            Trade savedTrade = tradeService.saveTrade(trade, tradeDTO);
            TradeDTO responseDTO = tradeMapper.toDto(savedTrade);
            return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
        } catch (Exception e) {
            logger.error("Error creating trade: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error creating trade: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update existing trade",
               description = "Updates an existing trade with new information. Subject to business rule validation and user privileges.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Trade updated successfully",
                    content = @Content(mediaType = "application/json",
                                     schema = @Schema(implementation = TradeDTO.class))),
        @ApiResponse(responseCode = "404", description = "Trade not found"),
        @ApiResponse(responseCode = "400", description = "Invalid trade data or business rule violation"),
        @ApiResponse(responseCode = "403", description = "Insufficient privileges to update trade")
    })
    public ResponseEntity<?> updateTrade(
            @Parameter(description = "Unique identifier of the trade to update", required = true)
            @PathVariable Long id,
            @Parameter(description = "Updated trade details", required = true)
            @Valid @RequestBody TradeDTO tradeDTO) {
        logger.info("Updating trade with id: {}", id);
        
        // Ensure the path ID matches the DTO ID if provided
        if (tradeDTO.getTradeId() != null && !id.equals(tradeDTO.getTradeId())) {
        return ResponseEntity.badRequest().body("Trade ID in path must match Trade ID in request body");
        }

        try {
            tradeDTO.setTradeId(id); // Ensure the ID matches
            Trade amendedTrade = tradeService.amendTrade(id, tradeDTO);
            TradeDTO responseDTO = tradeMapper.toDto(amendedTrade);
            return ResponseEntity.ok(responseDTO);
        } catch (Exception e) {
            logger.error("Error updating trade: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error updating trade: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete trade",
               description = "Deletes an existing trade. This is a soft delete that changes the trade status.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Trade deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Trade not found"),
        @ApiResponse(responseCode = "400", description = "Trade cannot be deleted in current status"),
        @ApiResponse(responseCode = "403", description = "Insufficient privileges to delete trade")
    })
    public ResponseEntity<?> deleteTrade(
            @Parameter(description = "Unique identifier of the trade to delete", required = true)
            @PathVariable Long id) {
        logger.info("Deleting trade with id: {}", id);
        try {
            tradeService.deleteTrade(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting trade: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error deleting trade: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/terminate")
    @Operation(summary = "Terminate trade",
               description = "Terminates an existing trade before its natural maturity date")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Trade terminated successfully",
                    content = @Content(mediaType = "application/json",
                                     schema = @Schema(implementation = TradeDTO.class))),
        @ApiResponse(responseCode = "404", description = "Trade not found"),
        @ApiResponse(responseCode = "400", description = "Trade cannot be terminated in current status"),
        @ApiResponse(responseCode = "403", description = "Insufficient privileges to terminate trade")
    })
    public ResponseEntity<?> terminateTrade(
            @Parameter(description = "Unique identifier of the trade to terminate", required = true)
            @PathVariable Long id) {
        logger.info("Terminating trade with id: {}", id);
        try {
            Trade terminatedTrade = tradeService.terminateTrade(id);
            TradeDTO responseDTO = tradeMapper.toDto(terminatedTrade);
            return ResponseEntity.ok(responseDTO);
        } catch (Exception e) {
            logger.error("Error terminating trade: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error terminating trade: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel trade",
               description = "Cancels an existing trade by changing its status to cancelled")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Trade cancelled successfully",
                    content = @Content(mediaType = "application/json",
                                     schema = @Schema(implementation = TradeDTO.class))),
        @ApiResponse(responseCode = "404", description = "Trade not found"),
        @ApiResponse(responseCode = "400", description = "Trade cannot be cancelled in current status"),
        @ApiResponse(responseCode = "403", description = "Insufficient privileges to cancel trade")
    })
    public ResponseEntity<?> cancelTrade(
            @Parameter(description = "Unique identifier of the trade to cancel", required = true)
            @PathVariable Long id) {
        logger.info("Cancelling trade with id: {}", id);
        try {
            Trade cancelledTrade = tradeService.cancelTrade(id);
            TradeDTO responseDTO = tradeMapper.toDto(cancelledTrade);
            return ResponseEntity.ok(responseDTO);
        } catch (Exception e) {
            logger.error("Error cancelling trade: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error cancelling trade: " + e.getMessage());
        }
    }
}
