# STEP DOCUMENTATION
Documentation of all Steps of the project challenge that has been completed

## STEP 1
The trading application was succefully set up in my local environment  with both the  backend and frontend are running correctly.

## STEP 2
Detailed, individual test-fix documentation is available in `docs/test-fixes-documentation.md`. This file was expanded from the initial grouping in `docs/test-fixes-report.md`, and maintains a numbering structure based on the original test class failures.

## STEP 3
The search capabilities, validation systems and personalized dashboard views have all been implemented and are paginated based on the Business Enhancement Request by Trading Desk Operations Team.

In order to optimized the Performance for high-volume trading data the trade entity was equipped with specific indexes on all foreign key columns, including composite indexes like idx_trade_cp_date (counterparty and trade date) and idx_trade_trader_status to to accelerate lookups on foreign key relationships (book_id, counterparty_id) and optimize multi-criteria filtering.

For the validation of legs, (both legs must have identical maturity dates), this date is calculated by deriving the maturity date from the latest cash flow date on each leg (i.e., implicit maturity).

For the fixed leg requiring a valid rate, it is defined by a non-null value that must strictly be above $0\%$ and not exceed $50\%$.

Implemented and configured Spring Boot Security to utilize the Security Context. This enables the system to reliably retrieve the currently authenticated user via the `SecurityContextHolder`. This retrieval  is necessary for implementing user-specific features, such as personalized dashboards and authorization checks based on the logged-in user's identity and roles.

A mandatory password hashing policy has been implemented for all user credentials. All passwords are now stored in the database using the BCrypt hashing algorithm. Existing unhashed passwords were replaced with their BCrypt hashed equivalents. The standard Spring Security format is used for persistence: `{bcrypt}$2a$10$...`.

## STEP 4
Detailed documentation of the critical bug fix is available in `docs/TRD-2025-001_CashflowFix.md`

## STEP 5
The Additional Info (Key-Value) structure was chosen for implementing Settlement Instructions (SI) because it ensures the database schema remains stable. New fields can be added to the trading system, without tampering with the main Trade database table or requiring downtime for schema migrations. Also, the initial AdditionalInfo structure provides versioning and audit history. Every change to the SI is tracked independently of other trade amendments.

The Settlement Instruction field has been successfully integrated across both the back-end and front-end. SI is treated as an optional input field during trade booking. A specialized textarea component was used on the front-end to ensure the text is easily viewed and scrolled, and includes a live character count to manage text limits.

The system correctly retrieves and re-asserts the existing SI value, preventing the instruction from being accidentally wiped out when a non-Trader/Sales user amends a trade

Security against SQL injection is enforced in the back-end by validating the text content and adhering to parameterized query best practices, ensuring the raw text input does not compromise the database.

Settlement instructions is included in the quick search with the add of a search SI button and it displays the first result found from list returned from the SI search 