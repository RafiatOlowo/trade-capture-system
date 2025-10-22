# Report:Cashflow Calculation Bug 

**Bug ID:** TRD-2025-001

**Date:** October 21, 2025

**Prepared By:** Rafiat Olowo

---

## 1. Executive Summary

A bug (TRD-2025-001) caused fixed-leg cashflows to be calculated **$\approx 100\times$ too high** (e.g., $\$875,000$ instead of $\$87,500$), distorting P\&L. The issue was due to two compounding errors in the `calculateCashflowValue` method within `TradeService.java`.

1.  **Rate Error:** Failing to convert the percentage rate to a decimal.
2.  **Precision Error:** Using imprecise `double` types for financial math.

The fix was successfully implemented using the `BigDecimal` class and correcting the percentage division, ensuring accurate financial calculations.

---

## 2. Root Causes and Impact

| Issue | Root Cause Detail | Impact |
| :--- | :--- | :--- |
| **$100 \times$ Error** | The `rate` (e.g., 3.5) was used directly in the formula instead of the required decimal value (0.035). | Cashflow results were precisely **100 times larger** than the correct value. |
| **Precision Error** | Monetary values were calculated using **`double`** instead of **`BigDecimal`**. | Led to small, unacceptable rounding errors, violating financial integrity standards. |

---

## 3. Solution Implemented

The `calculateCashflowValue` method was refactored to use `BigDecimal` for all calculations and the percentage conversion was corrected.

### Corrected Java Logic

```java
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
