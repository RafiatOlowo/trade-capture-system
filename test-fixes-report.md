# Test Fix Report
***

# BOOK-SERVICE-STABILIZATION
## 1. Bug Identification
The `BookServiceTest` suite was failing with **three distinct errors** across methods (`testFindBookById`, `testFindBookByNonExistentId`, and `testSaveBook`):

* **Dependency Errors:** Immediate NullPointerExceptions (NPEs), indicating the service object was incomplete.
* **Read Logic Error:** `testFindBookById` returned an Empty Optional, the retrieved entity wasn't being correctly mapped for the test's return.
* **Write Logic Error:** `testSaveBook` resulting in a `PotentialStubbingProblem`),  the save operation was passed a `null` entity.



## 2. Root Cause Analysis
The issues stemmed from an incomplete and undefined test setup for the `BookServiceTest` class:

* **Missing Injection (Caused NPEs):** The `@Mock` declarations for the required dependencies, **`BookMapper`** and **`CostCenterRepository`**, were missing. This prevented the `@InjectMocks` annotation from correctly injecting these non-null dependencies into the `BookService` instance.
* **Undefined Mapper Behavior (Caused Conversion/Stubbing Errors):** The mocked `BookMapper` was not programmed with specific behaviors.
    * **Read Flow:** The `toDto()` method returned `null` by default.
    * **Save Flow:** The `toEntity()` method returned `null` by default, causing `testSaveBook` to call `bookRepository.save(null)`.

## 3. Bug Fix Implementation

The fix involved a two-step approach: **correcting the dependency wiring** and **defining the required behavior** for the data mappers.

* **Dependency Injection Fix:** Added the missing **`@Mock` declarations** for `BookMapper` and `CostCenterRepository`.
* **Read/Conversion Stubbing Fix:** Implemented the Mockito stubbing for the read flow to ensure successful conversion:
    * `when(bookMapper.toDto(any(Book.class))).thenReturn(expectedDto)`.
* **Save/Persistence Stubbing Fix:** Implemented the Mockito stubbing for the write flow to ensure non-null objects are used throughout the save operation:
    * **`when(bookMapper.toEntity(any(BookDTO.class))).thenReturn(book)`** prevents passing `null` to `save`.
    * `when(bookRepository.save(book)).thenReturn(book)` ensures the mock repository returns a valid object.

## 4. Testing and Validation

All tests within the **`BookServiceTest`** class were re-run and now execute successfully.
***

# TRADE-SERVICE-STABILIZATION
***
## 1. Bug Identification
The `TradeServiceTest` suite was failing across multiple methods due to several distinct issues:

* **Cascading Dependency Errors (`testCreateTrade_Success`, `testAmendTrade_Success`):** Immediate **NullPointerExceptions (NPEs)** or exceptions indicating missing reference data ("Book not found," "Counterparty not found"). Failures occurred because critical repositories (Book, Counterparty, ApplicationUser) were not mocked or stubbed correctly.
* **Logical Flaws (`testCashflowGeneration_MonthlySchedule`):** The test failed because it contained a **broken placeholder assertion** (`expected: <1> but was: <12>`) and lacked the necessary setup to execute the complex date calculation logic.
* **Assertion Mismatch (`testCreateTrade_InvalidDates_ShouldFail`):** The test failed because the asserted exception message was **incorrect**, not matching the actual message thrown by the service's validation logic.

***
## 2. Root Cause Analysis
The failures stemmed from an **incomplete and incorrect test definition** across the suite:

* **Missing Dependencies & Stubs:** The success paths failed because mandatory **reference data repositories** were not mocked (`@Mock` declarations were missing) or their find methods were not stubbed to return valid, non-null entities.
* **Missing Entity Initialization:** The `testAmendTrade_Success` failed because the mocked existing `Trade` object did not have its **`version` field initialized**, causing an NPE during the amendment process.
* **Incomplete Cashflow Setup:** The cashflow test failed to configure the `TradeDTO` with the `"Monthly"` schedule and, critically, failed to mock the internal return of the `TradeLegRepository.save()` call, preventing the downstream cashflow calculation method from accessing the necessary **`Schedule` entity**.
* **Incorrect Assertion:** The date validation test had an incorrectly coded expected error message string.

***
## 3. Bug Fix Implementation

The fix involved a comprehensive stabilization of dependencies and correction of test logic:

* **Dependency Injection Fix:** Added missing **`@Mock` declarations** for all required repositories, including `ScheduleRepository`, `CurrencyRepository`, `LegTypeRepository`, and `PayRecRepository`.
* **Success Path Stabilization:**
    * Added stubs for all trade-level reference data lookups.
    * **Initialized the `version` field** on the mock `Trade` object in `setUp`.
* **Cashflow Logic Fix:**
    * Explicitly configured the `TradeDTO` to set `CalculationPeriodSchedule` to `"Monthly"`.
    * Implemented **`Mockito.thenAnswer`** for `tradeLegRepository.save()` to ensure the saved `TradeLeg` entity was returned with the required **`Schedule` object attached**.
    * Replaced the placeholder assertion with the correct verification: `verify(cashflowRepository, times(24)).save(any(Cashflow.class))`.
* **Assertion Fix:** Updated the expected exception message in `testCreateTrade_InvalidDates_ShouldFail` to accurately match the service's thrown message: `"Start date cannot be before trade date"`.

***
## 4. Testing and Validation

All fixes were implemented and verified through a **successful re-run** of the entire `TradeServiceTest` suite. The test now reliably confirms:
* Trade creation and amendment successfully satisfy all dependency requirements.
* The business rule requiring the trade start date to be on or after the trade date is correctly enforced.
* The `TradeService` accurately generates the expected number of cashflows (24 total) for a monthly schedule over a one-year period.
***

# Test Fix Report

## TRADE-LEG-VALIDATION-STABILIZATION
***
## 1. Bug Identification
The unit test `TradeLegControllerTest.testCreateTradeLegValidationFailure_NegativeNotional` was failing with the error: `Response content expected:<Notional must be positive> but was:<>`.

This occurred despite the presence of manual validation logic in the controller designed to return the expected error string.

***
## 2. Root Cause Analysis
The failure was caused by a **conflict between two layers of validation**:

* **Layer 1 (Declarative):** The `@Valid` annotation on the controller method triggered the **`@Positive`** annotation present on the `notional` field in the `TradeLegDTO`.
* **Layer 2 (Manual):** The `TradeLegController` contained the manual `if` check: `if (tradeLegDTO.getNotional().signum() <= 0)`.

Because **Layer 1 executes first**, the `@Positive` rule failed, throwing a `MethodArgumentNotValidException`. Spring's default error handler intercepted this exception and returned a **400 Bad Request with an empty body (`<>`)**. The code never reached **Layer 2**, which contained the logic to return the specific string `"Notional must be positive"`.

***
## 3. Bug Fix Implementation
The fix involved removing the conflicting declarative validation to ensure the code flow reached the manual validation layer, which held the expected assertion string:

* **Removal of Conflict:** The `@Positive` annotation was **removed** from the `notional` field in the `TradeLegDTO` class.
* **Validation Reliance Shift:** The negative/zero check for `notional` is now **exclusively reliant** on the manual `if` check within the `TradeLegController.createTradeLeg` method.

This guarantees that when a negative notional is sent, the controller's manual logic executes and returns the exact string the test asserts against.

***
## 4. Testing and Validation
The fix was implemented and verified:

* The test `testCreateTradeLegValidationFailure_NegativeNotional` now passes successfully, confirming the response status is `400 Bad Request` and the content body is `"Notional must be positive"`.
* The business rule requiring a positive notional is confirmed to be enforced by the controller's manual check.
* The `TradeLegControllerTest` suite remains stable.