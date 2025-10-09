package com.technicalchallenge.controller;

import com.technicalchallenge.BackendApplication;
import com.technicalchallenge.model.*;
import com.technicalchallenge.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

// Use SpringBootTest to load the full application context
@SpringBootTest(classes = BackendApplication.class)
@AutoConfigureMockMvc 
@ActiveProfiles("test") // Use 'test' profile to isolate test configurations

public class TradeControllerIT {

    private static final String BASE_URL = "/api/trades";

    @Autowired private MockMvc mockMvc;
    @Autowired private TradeRepository tradeRepository;

    // --- Repositories for Dependent Entities ---
    @Autowired private CounterpartyRepository counterpartyRepository;
    @Autowired private BookRepository bookRepository;
    @Autowired private TradeStatusRepository tradeStatusRepository;
    @Autowired private CostCenterRepository costCenterRepository;
    @Autowired private SubDeskRepository subDeskRepository;
    @Autowired private ApplicationUserRepository applicationUserRepository;
    @Autowired private TradeTypeRepository tradeTypeRepository;
    @Autowired private TradeSubTypeRepository tradeSubTypeRepository;
    @Autowired private DeskRepository deskRepository;

    // --- Helper Objects for Setup ---
    private ApplicationUser userTrader;
    private Desk deskFX;
    private SubDesk subDesk;
    private CostCenter costCenter;
    private Book bookX;
    private Counterparty cpA;
    private TradeStatus statusNew;
    private TradeStatus statusDead;
    private TradeType typeFX;
    private TradeSubType subTypeSpot;
    
    // These will hold the Trade objects after they are persisted, including their actual DB ID
    private Trade trade1, trade2, trade3; 

    @BeforeEach
    void setup() {
        // --- 1. Clear Data in reverse dependency order ---
        tradeRepository.deleteAll();
        
        // Clear all dependent repositories
        tradeSubTypeRepository.deleteAll();
        tradeTypeRepository.deleteAll();
        tradeStatusRepository.deleteAll();
        applicationUserRepository.deleteAll();
        bookRepository.deleteAll();
        counterpartyRepository.deleteAll();
        costCenterRepository.deleteAll();
        subDeskRepository.deleteAll();
        deskRepository.deleteAll(); // Lowest dependency cleared last

        // --- 2. Create and Save Dependencies ---
        
        // A. Desk
        deskFX = new Desk();
        deskFX.setId(1000L);
        deskFX.setDeskName("FX Desk"); 
        deskRepository.save(deskFX);
        
        // B. SubDesk (Now depends on Desk)
        subDesk = new SubDesk();
        subDesk.setId(900L);
        subDesk.setDesk(deskFX); 
        subDesk.setSubdeskName("SubDesk_Risk"); 
        subDeskRepository.save(subDesk);

        // C. CostCenter (depends on SubDesk)
        costCenter = new CostCenter();
        costCenter.setId(400L);
        costCenter.setCostCenterName("CC_FX_EUROPE");
        costCenter.setSubDesk(subDesk);
        costCenterRepository.save(costCenter);

        // D. ApplicationUser (Trader)
        userTrader = new ApplicationUser();
        userTrader.setId(800L);
        userTrader.setLoginId("trader_bob");
        applicationUserRepository.save(userTrader);

        // E. TradeType and SubType
        typeFX = new TradeType();
        typeFX.setId(700L);
        typeFX.setTradeType("FX");
        tradeTypeRepository.save(typeFX);

        subTypeSpot = new TradeSubType();
        subTypeSpot.setId(701L);
        subTypeSpot.setTradeSubType("SPOT");
        tradeSubTypeRepository.save(subTypeSpot);


        // --- 3. Create and Save INTERMEDIATE Dependencies ---

        // F. TradeStatus
        statusNew = new TradeStatus();
        statusNew.setId(1000L); 
        statusNew.setTradeStatus("NEW"); 
        
        statusDead = new TradeStatus();
        statusDead.setId(1005L);
        statusDead.setTradeStatus("DEAD"); 

        tradeStatusRepository.saveAll(List.of(statusNew, statusDead));

        // G. Counterparty
        cpA = new Counterparty();
        cpA.setId(500L);
        cpA.setName("CP-A");
        
        Counterparty cpB = new Counterparty();
        cpB.setId(501L);
        cpB.setName("CP-B");
        counterpartyRepository.saveAll(List.of(cpA, cpB));

        // H. Book (depends on CostCenter)
        bookX = new Book();
        bookX.setId(600L);
        bookX.setBookName("Book-X");
        bookX.setCostCenter(costCenter);
        
        Book bookY = new Book();
        bookY.setId(601L);
        bookY.setBookName("Book-Y");
        bookY.setCostCenter(costCenter); 
        bookRepository.saveAll(List.of(bookX, bookY));


        // --- 4. Create and Save Trade Entities (Using ALL Dependencies) ---
        
        // Create trades with distinct TradeId (business key) values (100, 101, 102)
        // Primary key 'id' is left null to be auto-generated by the database
        Trade t1 = createTrade(100L, cpA, bookX, statusNew, LocalDate.of(2025, 1, 15), userTrader, typeFX, subTypeSpot); // Status: NEW
        Trade t2 = createTrade(101L, cpB, bookY, statusNew, LocalDate.of(2025, 2, 20), userTrader, typeFX, subTypeSpot); // Status: NEW
        Trade t3 = createTrade(102L, cpA, bookX, statusDead, LocalDate.of(2025, 3, 25), userTrader, typeFX, subTypeSpot); // Status: DEAD

        // Save and capture the actual persisted entities which contain the database-assigned IDs
        List<Trade> savedTrades = tradeRepository.saveAll(List.of(t1, t2, t3));
        
        // Assign the saved trades back to the class fields for use in assertions
        trade1 = savedTrades.get(0); 
        trade2 = savedTrades.get(1);
        trade3 = savedTrades.get(2);
    }

    // Helper method to create a Trade object easily
    private Trade createTrade(
        // The primary key 'id' will be set by the database upon save
        Long tradeIdValue, Counterparty cp, Book book, TradeStatus status, LocalDate date, 
        ApplicationUser trader, TradeType type, TradeSubType subType) {
        
        Trade trade = new Trade();
        
        // Set the business ID field
        trade.setTradeId(tradeIdValue); 
        trade.setVersion(1);
        
        // Dependent objects
        trade.setCounterparty(cp);
        trade.setBook(book);
        trade.setTradeStatus(status);
        trade.setTraderUser(trader);
        trade.setTradeInputterUser(trader); // For simplicity, inputter is same as trader
        trade.setTradeType(type);
        trade.setTradeSubType(subType);
        
        // Other fields
        trade.setTradeDate(date);
        trade.setUtiCode("UTI" + tradeIdValue);
        trade.setActive(true);
        trade.setCreatedDate(java.time.LocalDateTime.now());
        
        return trade;
    }

    // --- Tests ---

    @Test
    @DisplayName("RSQL Search: Valid RSQL query returns correct trade(s)")
    void testAdvancedSearch_ValidRsqlQuery() throws Exception {
        // Find trade with Counterparty 'CP-A' AND status 'DEAD' (should be Trade 3)
        String rsqlQuery = String.format(
            "counterparty.name=='%s';tradeStatus.tradeStatus=='%s'", 
            cpA.getName(), 
            statusDead.getTradeStatus()
        );

        mockMvc.perform(get(BASE_URL + "/rsql")
                .param("query", rsqlQuery)
                .param("sort", "id,asc") // Ensure deterministic result order
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            // Assert on the actual persisted ID of trade3
            .andExpect(jsonPath("$.content[0].id").value(trade3.getId()));
    }
    
    @Test
    @DisplayName("RSQL Search: Invalid RSQL syntax returns 400 Bad Request")
    void testAdvancedSearch_InvalidRsqlSyntax_Returns400() throws Exception {
        // The query is invalid because '===' is not a valid RSQL operator
        String invalidRsqlQuery = "counterparty.name==='CP-A'"; 

        mockMvc.perform(get(BASE_URL + "/rsql")
                .param("query", invalidRsqlQuery)
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isBadRequest()) // Expect 400
            .andExpect(jsonPath("$.message").exists()); // Ensure an error message is returned
    }
    
    @Test
    @DisplayName("Simple Search: Multiple criteria filter returns correct trade(s)")
    void testSimpleSearch_MultipleCriteria() throws Exception {
        // Find Trade 1: CP-A, Book-X, NEW, Trader=trader_bob, Date=2025-01-15
        
        mockMvc.perform(get(BASE_URL + "/search")
                .param("counterparty", cpA.getName()) // Use "CP-A" from setup object
                .param("book", bookX.getBookName())   // Use "Book-X" from setup object
                .param("trader", String.valueOf(userTrader.getId())) // Use 800L converted to "800"
                .param("status", statusNew.getTradeStatus()) // Use "NEW" from setup object
                .param("startDate", "2025-01-01") // Date range containing trade 1
                .param("endDate", "2025-02-01")
                .param("sort", "id,asc") // Added sort by ID to guarantee Trade 1 is the first result (index 0)
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1))) // Should only find trade 1
            // FIX: Assert on the actual persisted ID of trade1
            .andExpect(jsonPath("$.content[0].id").value(trade1.getId()));
    }
    
    // Test filtering by date range only
    @Test
    @DisplayName("Simple Search: Filtering by date range only")
    void testSimpleSearch_DateRange() throws Exception {
        // Search for trades between Feb 1st and Mar 1st (should only find Trade 2)
        
        mockMvc.perform(get(BASE_URL + "/search")
                .param("startDate", "2025-02-01")
                .param("endDate", "2025-03-01")
                .param("sort", "id,asc") // Added sort by ID to guarantee Trade 2 is the first result (index 0)
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1))) // Should only find trade 2
            // FIX: Assert on the actual persisted ID of trade2
            .andExpect(jsonPath("$.content[0].id").value(trade2.getId()));
    }

    @Test
    @DisplayName("Simple Search: Finding all trades when no parameters are provided")
    void testSimpleSearch_NoParameters() throws Exception {
        // Should return all 3 trades
        mockMvc.perform(get(BASE_URL + "/search")
                .param("sort", "id,asc") // Added sort by ID to guarantee consistent order
                .contentType(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(3)))
            .andExpect(jsonPath("$.totalElements").value(3));
    }
}
