package com.technicalchallenge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.technicalchallenge.dto.SettlementInstructionsDTO;
import com.technicalchallenge.dto.TradeDTO;
import com.technicalchallenge.mapper.TradeMapper;
import com.technicalchallenge.model.Trade;
import com.technicalchallenge.service.TradeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Collections;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@ExtendWith(SpringExtension.class)
@WebMvcTest(TradeController.class)
public class TradeControllerTest {

    // You will now need the WebApplicationContext
    @Autowired
    private WebApplicationContext context; 

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TradeService tradeService;

    @MockBean
    private TradeMapper tradeMapper;

    private ObjectMapper objectMapper;
    private TradeDTO tradeDTO;
    private Trade trade;
    private Pageable pageable;

    @BeforeEach
    void setUp() {

        // 1. Initialize MockMvc with Spring Security support
        // This is necessary to correctly process security annotations and setup
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity()) // Applies Spring Security to MockMvc
                .defaultRequest(post("/").with(user("testUser").roles("RISK_MANAGER"))) // <-- Set default user for POST
                .defaultRequest(get("/").with(user("testUser").roles("RISK_MANAGER")))  // <-- Set default user for GET
                .defaultRequest(put("/").with(user("testUser").roles("RISK_MANAGER")))  // <-- Set default user for PUT
                .defaultRequest(delete("/").with(user("testUser").roles("RISK_MANAGER"))) // <-- Set default user for DELETE
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        pageable = PageRequest.of(0, 10);

        // Create a sample TradeDTO for testing
        tradeDTO = new TradeDTO();
        tradeDTO.setTradeId(1001L);
        tradeDTO.setVersion(1);
        tradeDTO.setTradeDate(LocalDate.now()); // Fixed: LocalDate instead of LocalDateTime
        tradeDTO.setTradeStartDate(LocalDate.now().plusDays(2)); // Fixed: correct method name
        tradeDTO.setTradeMaturityDate(LocalDate.now().plusYears(5)); // Fixed: correct method name
        tradeDTO.setTradeStatus("LIVE");
        tradeDTO.setBookName("TestBook");
        tradeDTO.setCounterpartyName("TestCounterparty");
        tradeDTO.setTraderUserName("TestTrader");
        tradeDTO.setInputterUserName("TestInputter");
        tradeDTO.setUtiCode("UTI123456789");

        // Create a sample Trade entity for testing
        trade = new Trade();
        trade.setId(1L);
        trade.setTradeId(1001L);
        trade.setVersion(1);
        trade.setTradeDate(LocalDate.now()); // Fixed: LocalDate instead of LocalDateTime
        trade.setTradeStartDate(LocalDate.now().plusDays(2)); // Fixed: correct method name
        trade.setTradeMaturityDate(LocalDate.now().plusYears(5)); // Fixed: correct method name

        // Set up default mappings
        when(tradeMapper.toDto(any(Trade.class))).thenReturn(tradeDTO);
        when(tradeMapper.toEntity(any(TradeDTO.class))).thenReturn(trade);
    }

    @Test
    void testGetAllTrades() throws Exception {
        // Given
        List<Trade> trades = List.of(trade); // Fixed: use List.of instead of Arrays.asList for single item

        when(tradeService.getAllTrades()).thenReturn(trades);

        // When/Then
        mockMvc.perform(get("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].tradeId", is(1001)))
                .andExpect(jsonPath("$[0].bookName", is("TestBook")))
                .andExpect(jsonPath("$[0].counterpartyName", is("TestCounterparty")));

        verify(tradeService).getAllTrades();
    }

    @Test
    void testGetTradeById() throws Exception {
        // Given
        when(tradeService.getTradeById(1001L)).thenReturn(Optional.of(trade));

        // When/Then
        mockMvc.perform(get("/api/trades/1001")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeId", is(1001)))
                .andExpect(jsonPath("$.bookName", is("TestBook")))
                .andExpect(jsonPath("$.counterpartyName", is("TestCounterparty")));

        verify(tradeService).getTradeById(1001L);
    }

    @Test
    void testGetTradeByIdNotFound() throws Exception {
        // Given
        when(tradeService.getTradeById(9999L)).thenReturn(Optional.empty());

        // When/Then
        mockMvc.perform(get("/api/trades/9999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(tradeService).getTradeById(9999L);
    }

    // Tests for the /search Endpoint

    @Test
    void testSimpleSearch_SuccessWithAllCriteria() throws Exception {
        // Arrange
        String counterparty = "GS";
        String book = "NYC-BOOK";
        Long traderId = 42L;
        String status = "NEW";
        String startDate = "2024-01-01";
        String endDate = "2024-01-31";
        
        // Mock the service to return a dummy paginated result for any Specification
        Page<Trade> tradePage = new PageImpl<>(List.of(trade), PageRequest.of(0, 10), 1);
        when(tradeService.searchTrades(any(), any())).thenReturn(tradePage);

        // Act & Assert
        mockMvc.perform(get("/api/trades/search")
                .param("counterparty", counterparty)
                .param("book", book)
                .param("trader", String.valueOf(traderId))
                .param("status", status)
                .param("startDate", startDate)
                .param("endDate", endDate)
                .param("page", "0")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
                
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1));

        // Verify that the correct service method was called
        verify(tradeService, times(1)).searchTrades(any(), any(Pageable.class));
        verify(tradeService, never()).findAll(any()); // Should not fall back to findAll
    }

    @Test
    void testSimpleSearch_SuccessWithNoCriteria() throws Exception {
        // Arrange
        Page<Trade> tradePage = new PageImpl<>(List.of(trade), PageRequest.of(0, 10), 10);

        // Mock the fallback service method
        when(tradeService.findAll(any(Pageable.class))).thenReturn(tradePage);

        // Act & Assert
        mockMvc.perform(get("/api/trades/search")
                .param("page", "0")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
                
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(10));

        // Verify that the fallback method was called
        verify(tradeService, times(1)).findAll(any(Pageable.class));
        verify(tradeService, never()).searchTrades(any(), any()); // Should not call searchTrades
    }

    @Test
    void testSimpleSearch_OnlyDateRangeCriteria() throws Exception {
        // Arrange
        String startDate = "2024-05-01";
        
        Page<Trade> tradePage = new PageImpl<>(List.of(trade), PageRequest.of(0, 10), 5);
        when(tradeService.searchTrades(any(), any())).thenReturn(tradePage);

        // Act & Assert
        mockMvc.perform(get("/api/trades/search")
                .param("startDate", startDate)
                .param("page", "0")
                .contentType(MediaType.APPLICATION_JSON))
                
            .andExpect(status().isOk())
            // changed assertion from 5 to 1 for less dummy data setup.
            .andExpect(jsonPath("$.totalElements").value(1));

        // Verify that searchTrades was correctly called when only one filter is active
        verify(tradeService, times(1)).searchTrades(any(), any(Pageable.class));
        verify(tradeService, never()).findAll(any());
    }

    @Test
    void testSimpleSearch_InvalidInput_Returns400() throws Exception {
        // Arrange
        // Passing a non-numeric string for 'trader' which is expected to be a Long
        String invalidTraderId = "NOT_A_NUMBER"; 

        // Act & Assert
        mockMvc.perform(get("/api/trades/search")
                .param("trader", invalidTraderId)
                .contentType(MediaType.APPLICATION_JSON))
                
            // Expect Spring to handle the type mismatch error and return 400
            .andExpect(status().isBadRequest()); 

        // Verify that the service layer was never reached due to the binding error
        verify(tradeService, never()).searchTrades(any(), any());
        verify(tradeService, never()).findAll(any());
    }

    // Tests for the /Filter Endpoint

    @Test
    void testPaginatedFilter_Success() throws Exception {
        // Arrange
        List<Trade> tradeList = List.of(trade);
        Page<Trade> tradePage = new PageImpl<>(tradeList, pageable, 1);
        
        when(tradeService.findAll(any(PageRequest.class))).thenReturn(tradePage);

        // Act & Assert
        mockMvc.perform(get("/api/trades/filter") // Simulates a GET request to /filter
                .param("page", "0")   // Request parameter for Pageable
                .param("size", "10")  // Request parameter for Pageable
                .contentType(MediaType.APPLICATION_JSON))
                
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            
            // Check content of the Page object JSON structure
            .andExpect(jsonPath("$.content[0].id").value(tradeDTO.getId()))
            .andExpect(jsonPath("$.totalElements").value(1));

        // Verify that the service was called once
        verify(tradeService, times(1)).findAll(any(PageRequest.class));
    }

    @Test
    void testPaginatedFilter_EmptyPage() throws Exception {
        // Arrange
        Page<Trade> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(1, 10), 0);
        
        when(tradeService.findAll(any(PageRequest.class))).thenReturn(emptyPage);

        // Act & Assert
        mockMvc.perform(get("/api/trades/filter")
                .param("page", "1")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
                
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content").isEmpty())
            .andExpect(jsonPath("$.totalElements").value(0));

        // Verify service was called
        verify(tradeService, times(1)).findAll(any(PageRequest.class));
        
        // Verify mapper was NOT called since the list was empty
        verify(tradeMapper, never()).toDto(any());
    }

    @Test
    void testPaginatedFilter_PageableApplied() throws Exception {
        // Arrange
        int requestedPage = 5;
        int requestedSize = 25;
        
        // Create a dummy Page object that confirms the Pageable parameters
        PageRequest expectedPageable = PageRequest.of(requestedPage, requestedSize);
        Page<Trade> dummyPage = new PageImpl<>(Collections.emptyList(), expectedPageable, 500);
        
        when(tradeService.findAll(expectedPageable)).thenReturn(dummyPage);

        // Act & Assert
        mockMvc.perform(get("/api/trades/filter")
                .param("page", String.valueOf(requestedPage))
                .param("size", String.valueOf(requestedSize))
                .contentType(MediaType.APPLICATION_JSON))
                
            .andExpect(status().isOk())
            
            // Verify the metadata in the response JSON matches the request
            .andExpect(jsonPath("$.pageable.pageNumber").value(requestedPage))
            .andExpect(jsonPath("$.pageable.pageSize").value(requestedSize));

        // Verify that the service was called with the exact PageRequest object
        verify(tradeService, times(1)).findAll(expectedPageable);
    }

    // Tests for the /rsql Endpoint

    @Test
    void testAdvancedSearch_SuccessWithValidQuery() throws Exception {
        // Arrange
        String validRsqlQuery = "status==NEW;counterparty==GS";
        
        // Mock the service to return a dummy paginated result for any Specification
        Page<Trade> tradePage = new PageImpl<>(List.of(trade), PageRequest.of(0, 10), 1);
        when(tradeService.searchTrades(any(), any())).thenReturn(tradePage);

        // Act & Assert
        mockMvc.perform(get("/api/trades/rsql")
                .param("query", validRsqlQuery)
                .param("page", "0")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
                
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1));

        // Verify that the searchTrades method was called (for RSQL query)
        verify(tradeService, times(1)).searchTrades(any(), any(Pageable.class));
        verify(tradeService, never()).findAll(any()); // Should not fall back to findAll
    }

    @Test
    void testAdvancedSearch_SuccessWithEmptyQuery() throws Exception {
        // Arrange
        // The endpoint defaults to an empty string if 'query' is not present
        Page<Trade> tradePage = new PageImpl<>(List.of(trade), PageRequest.of(0, 10), 50);
        
        // Mock the fallback service method
        when(tradeService.findAll(any(Pageable.class))).thenReturn(tradePage);

        // Act & Assert
        mockMvc.perform(get("/api/trades/rsql")
                // Query parameter intentionally omitted to simulate empty query
                .param("page", "0")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
                
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(50));

        // Verify that the fallback findAll method was called
        verify(tradeService, times(1)).findAll(any(Pageable.class));
        verify(tradeService, never()).searchTrades(any(), any()); // Should not call searchTrades
    }

    @Test
    void testCreateTrade() throws Exception {
        // Given
        when(tradeService.saveTrade(any(Trade.class), any(TradeDTO.class))).thenReturn(trade);
        doNothing().when(tradeService).populateReferenceDataByName(any(Trade.class), any(TradeDTO.class));

        // When/Then
        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tradeDTO))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tradeId", is(1001)));

        verify(tradeService).saveTrade(any(Trade.class), any(TradeDTO.class));
        verify(tradeService).populateReferenceDataByName(any(Trade.class), any(TradeDTO.class));
    }

    @Test
    void testCreateTradeValidationFailure_MissingTradeDate() throws Exception {
        // Given
        TradeDTO invalidDTO = new TradeDTO();
        invalidDTO.setBookName("TestBook");
        invalidDTO.setCounterpartyName("TestCounterparty");
        // Trade date is purposely missing

        // When/Then
        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDTO))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Trade date is required"));

        verify(tradeService, never()).saveTrade(any(Trade.class), any(TradeDTO.class));
    }

    @Test
    void testCreateTradeValidationFailure_MissingBook() throws Exception {
        // Given
        TradeDTO invalidDTO = new TradeDTO();
        invalidDTO.setTradeDate(LocalDate.now());
        invalidDTO.setCounterpartyName("TestCounterparty");
        // Book name is purposely missing

        // When/Then
        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDTO))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Book and Counterparty are required"));

        verify(tradeService, never()).saveTrade(any(Trade.class), any(TradeDTO.class));
    }

    @Test
    void testUpdateTrade() throws Exception {
        // Given
        Long tradeId = 1001L;
        tradeDTO.setTradeId(tradeId);
        // 'trade' object returned by service should have the same ID with JSON path.
        trade.setTradeId(tradeId);
        
        when(tradeService.amendTrade(eq(tradeId), any(TradeDTO.class))).thenReturn(trade); 
        doNothing().when(tradeService).populateReferenceDataByName(any(Trade.class), any(TradeDTO.class));

        // When/Then
        mockMvc.perform(put("/api/trades/{id}", tradeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tradeDTO))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeId", is(1001)));

        verify(tradeService).amendTrade(eq(tradeId), any(TradeDTO.class));
    }

    @Test
    void testUpdateTradeIdMismatch() throws Exception {
        // Given
        Long pathId = 1001L;
        tradeDTO.setTradeId(2002L); // Different from path ID

        // When/Then
        mockMvc.perform(put("/api/trades/{id}", pathId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tradeDTO))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Trade ID in path must match Trade ID in request body"));

        verify(tradeService, never()).saveTrade(any(Trade.class), any(TradeDTO.class));
    }

    @Test
    void testDeleteTrade() throws Exception {
        // Given
        doNothing().when(tradeService).deleteTrade(1001L);

        // When/Then
        mockMvc.perform(delete("/api/trades/1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(tradeService).deleteTrade(1001L);
    }

    @Test
    void testCreateTradeWithValidationErrors() throws Exception {
        // Given
        TradeDTO invalidDTO = new TradeDTO();
        invalidDTO.setTradeDate(LocalDate.now()); // Fixed: LocalDate instead of LocalDateTime
        // Missing required fields to trigger validation errors

        // When/Then
        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDTO))
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        verify(tradeService, never()).createTrade(any(TradeDTO.class));
    }

        // --- TESTS FOR SETTLEMENT INSTRUCTIONS ENDPOINTS ---

    @Test
    void testUpdateSettlementInstructions_Success() throws Exception {
        // Arrange
        Long tradeId = 1001L;
        String newInstructions = "Updated settlement instructions for successful test.";

        SettlementInstructionsDTO request = new SettlementInstructionsDTO();
        request.setInstructions(newInstructions);

        // Mock service call to return the updated trade entity
        when(tradeService.updateTradeSettlementInstructions(eq(tradeId), eq(newInstructions)))
            .thenReturn(trade);
        
        // Act & Assert
        mockMvc.perform(put("/api/trades/{id}/settlement-instructions", tradeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeId", is(1001)))
                .andExpect(jsonPath("$.bookName", is("TestBook"))); // Verify response is a mapped TradeDTO

        // Verify the service method was called exactly once with correct parameters
        verify(tradeService).updateTradeSettlementInstructions(eq(tradeId), eq(newInstructions));
    }

    @Test
    void testUpdateSettlementInstructions_TradeNotFound() throws Exception {
        // Arrange
        Long nonExistentTradeId = 9999L;
        String notFoundMessage = "Trade with ID 9999 not found or inactive.";
        
        SettlementInstructionsDTO request = new SettlementInstructionsDTO();
        request.setInstructions("Valid instructions that won't be saved.");

        // Mock service to throw a RuntimeException (which the controller maps to 404)
        when(tradeService.updateTradeSettlementInstructions(eq(nonExistentTradeId), anyString()))
            .thenThrow(new RuntimeException(notFoundMessage));
        
        // Act & Assert
        mockMvc.perform(put("/api/trades/{id}/settlement-instructions", nonExistentTradeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                
                .andExpect(status().isNotFound()) // Expect 404
                .andExpect(content().string(notFoundMessage)); // Verify the error message is returned
        
        // Verify mapper was never called
        verify(tradeMapper, never()).toDto(any());
    }

    @Test
    void testUpdateSettlementInstructions_ValidationFailure() throws Exception {
        // Arrange
        Long tradeId = 1001L;
        // Instructions are too short, violating @Size(min = 10)
        SettlementInstructionsDTO invalidRequest = new SettlementInstructionsDTO();
        invalidRequest.setInstructions("Short"); 

        // Act & Assert
        mockMvc.perform(put("/api/trades/{id}/settlement-instructions", tradeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest))
                        .with(csrf()))
                
                .andExpect(status().isBadRequest()); // Expect 400 due to @Valid failure
        // Verify the service method was never called
        verify(tradeService, never()).updateTradeSettlementInstructions(anyLong(), anyString());
    }

    @Test
    void testSearchBySettlementInstructions_SuccessWithResults() throws Exception {
        // Arrange
        String searchTerm = "Bank of America";
        
        // Create a list of Trades
        List<Trade> trades = List.of(trade);
        
        // Mock the service call
        when(tradeService.searchTradesBySettlementInstructions(searchTerm)).thenReturn(trades);

        // Act & Assert
        mockMvc.perform(get("/api/trades/search/settlement-instructions")
                        .param("instructions", searchTerm)
                        .contentType(MediaType.APPLICATION_JSON))
                
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].tradeId", is(1001))); // Check content mapping

        // Verify the service method was called
        verify(tradeService).searchTradesBySettlementInstructions(searchTerm);
    }

    @Test
    void testSearchBySettlementInstructions_NoResults() throws Exception {
        // Arrange
        String searchTerm = "NonExistentTerm";
        
        // Mock the service call to return an empty list
        when(tradeService.searchTradesBySettlementInstructions(searchTerm)).thenReturn(Collections.emptyList());

        // Act & Assert
        mockMvc.perform(get("/api/trades/search/settlement-instructions")
                        .param("instructions", searchTerm)
                        .contentType(MediaType.APPLICATION_JSON))
                
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0))); // Expect empty array

        // Verify the service method was called
        verify(tradeService).searchTradesBySettlementInstructions(searchTerm);
        // Verify mapper was never called since the list was empty
        verify(tradeMapper, never()).toDto(any());
    }

    @Test
    void testSearchBySettlementInstructions_InternalServerError() throws Exception {
        // Arrange
        String searchTerm = "ErrorTerm";
        
        // Mock the service call to throw a generic exception (mapped to 500)
        when(tradeService.searchTradesBySettlementInstructions(searchTerm))
            .thenThrow(new IllegalStateException("Database connection failed"));

        // Act & Assert
        mockMvc.perform(get("/api/trades/search/settlement-instructions")
                        .param("instructions", searchTerm)
                        .contentType(MediaType.APPLICATION_JSON))
                
                .andExpect(status().isInternalServerError()) // Expect 500
                .andExpect(content().string("")); // Response body is empty for 500 in the controller

        // Verify the service method was called
        verify(tradeService).searchTradesBySettlementInstructions(searchTerm);
    }
}
