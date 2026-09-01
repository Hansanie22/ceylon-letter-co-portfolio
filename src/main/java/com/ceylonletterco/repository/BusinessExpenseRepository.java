package com.ceylonletterco.repository;

import com.ceylonletterco.entity.BusinessExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface BusinessExpenseRepository extends JpaRepository<BusinessExpense, Long> {

    /** All expenses between two dates, sorted newest first */
    List<BusinessExpense> findByExpenseDateBetweenOrderByExpenseDateDescCreatedAtDesc(
            LocalDate startDate, LocalDate endDate);

    /** Sum of all amounts in a date range */
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM BusinessExpense e WHERE e.expenseDate BETWEEN :start AND :end")
    BigDecimal sumAmountByDateRange(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /** Sum per category in a date range (returns Object[] rows: [category, sum]) */
    @Query("SELECT e.category, COALESCE(SUM(e.amount), 0) FROM BusinessExpense e " +
           "WHERE e.expenseDate BETWEEN :start AND :end GROUP BY e.category ORDER BY SUM(e.amount) DESC")
    List<Object[]> sumByCategoryAndDateRange(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
