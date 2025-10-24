package com.technicalchallenge.repository;

import com.technicalchallenge.model.Trade;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long>, JpaSpecificationExecutor<Trade> {
    // Existing methods
    List<Trade> findByTradeId(Long tradeId);

    @Query("SELECT MAX(t.tradeId) FROM Trade t")
    Optional<Long> findMaxTradeId();

    @Query("SELECT MAX(t.version) FROM Trade t WHERE t.tradeId = :tradeId")
    Optional<Integer> findMaxVersionByTradeId(@Param("tradeId") Long tradeId);

    // NEW METHODS for service layer compatibility
    Optional<Trade> findByTradeIdAndActiveTrue(Long tradeId);

    List<Trade> findByActiveTrueOrderByTradeIdDesc();

    @Query("SELECT t FROM Trade t WHERE t.tradeId = :tradeId AND t.active = true ORDER BY t.version DESC")
    Optional<Trade> findLatestActiveVersionByTradeId(@Param("tradeId") Long tradeId);

    /**
     * Retrieves all trades associated with a specific trader, paginated.
     * Used by the /my-trades endpoint.
     */
    Page<Trade> findByTraderUserId(Long traderUserId, Pageable pageable);

     /**
     * Retrieves all trades associated with a specific trading book, paginated.
     * Used by the /book/{id}/trades endpoint.
     */
    Page<Trade> findByTraderUserIdAndBookId(Long traderUserId, Long bookId, Pageable pageable);

    /**
     * Finds all trades for a specific trader that are currently considered 'active' 
     * based on their TradeStatus name (e.g., LIVE, NEW, AMENDED).
     * Used for the personalized Portfolio Summary calculation.
     */
    List<Trade> findByTraderUser_IdAndTradeStatus_TradeStatusIn(Long traderUserId, List<String> tradeStatusNames);
    
    /**
     * Custom query to get trades for daily activity tracking.
     * @param traderUserId The ID of the trader.
     * @param date The date to search for.
     */
    @Query("SELECT t FROM Trade t WHERE t.traderUser.id = :traderUserId AND t.tradeDate = :date")
    List<Trade> findTradesByTraderAndDate(@Param("traderUserId") Long traderUserId, @Param("date") java.time.LocalDate date);

    // Method for Settlement Instructions search
    @Query("SELECT t FROM Trade t " +
           "JOIN AdditionalInfo ai ON t.id = ai.entityId " +
           "WHERE ai.entityType = 'TRADE' " +
           "AND ai.fieldName = 'SETTLEMENT_INSTRUCTIONS' " +
           "AND ai.active = TRUE " +
           "AND UPPER(ai.fieldValue) LIKE UPPER(CONCAT('%', :instructions, '%'))")
    List<Trade> findActiveTradesBySettlementInstructionsContaining(@Param("instructions") String instructions);
}
