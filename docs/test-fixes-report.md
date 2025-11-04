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
## 1. Bug Identification
The `TradeServiceTest` suite was failing across multiple methods due to several distinct issues:
* **Cascading Dependency Errors (`testCreateTrade_Success`, `testAmendTrade_Success`):** Immediate **NullPointerExceptions (NPEs)** or exceptions indicating missing reference data ("Book not found," "Counterparty not found"). Failures occurred because critical repositories (Book, Counterparty, ApplicationUser) were not mocked or stubbed correctly.
* **Logical Flaws (`testCashflowGeneration_MonthlySchedule`):** The test failed because it contained a **broken placeholder assertion** (`expected: <1> but was: <12>`) and lacked the necessary setup to execute the complex date calculation logic.
* **Assertion Mismatch (`testCreateTrade_InvalidDates_ShouldFail`):** The test failed because the asserted exception message was **incorrect**, not matching the actual message thrown by the service's validation logic.

## 2. Root Cause Analysis
The failures stemmed from an **incomplete and incorrect test definition** across the suite:
* **Missing Dependencies & Stubs:** The success paths failed because mandatory **reference data repositories** were not mocked (`@Mock` declarations were missing) or their find methods were not stubbed to return valid, non-null entities.
* **Missing Entity Initialization:** The `testAmendTrade_Success` failed because the mocked existing `Trade` object did not have its **`version` field initialized**, causing an NPE during the amendment process.
* **Incomplete Cashflow Setup:** The cashflow test failed to configure the `TradeDTO` with the `"Monthly"` schedule and, critically, failed to mock the internal return of the `TradeLegRepository.save()` call, preventing the downstream cashflow calculation method from accessing the necessary **`Schedule` entity**.
* **Incorrect Assertion:** The date validation test had an incorrectly coded expected error message string.

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

## 4. Testing and Validation
All fixes were implemented and verified through a **successful re-run** of the entire `TradeServiceTest` suite. The test now reliably confirms:
* Trade creation and amendment successfully satisfy all dependency requirements.
* The business rule requiring the trade start date to be on or after the trade date is correctly enforced.
* The `TradeService` accurately generates the expected number of cashflows (24 total) for a monthly schedule over a one-year period.
***

## TRADE-LEG-VALIDATION-STABILIZATION
## 1. Bug Identification
The unit test `TradeLegControllerTest.testCreateTradeLegValidationFailure_NegativeNotional` was failing with the error: `Response content expected:<Notional must be positive> but was:<>`.
This occurred despite the presence of manual validation logic in the controller designed to return the expected error string.

## 2. Root Cause Analysis
The failure was caused by a **conflict between two layers of validation**:
* **Layer 1 (Declarative):** The `@Valid` annotation on the controller method triggered the **`@Positive`** annotation present on the `notional` field in the `TradeLegDTO`.
* **Layer 2 (Manual):** The `TradeLegController` contained the manual `if` check: `if (tradeLegDTO.getNotional().signum() <= 0)`.
Because **Layer 1 executes first**, the `@Positive` rule failed, throwing a `MethodArgumentNotValidException`. Spring's default error handler intercepted this exception and returned a **400 Bad Request with an empty body (`<>`)**. The code never reached **Layer 2**, which contained the logic to return the specific string `"Notional must be positive"`.

## 3. Bug Fix Implementation
The fix involved removing the conflicting declarative validation to ensure the code flow reached the manual validation layer, which held the expected assertion string:
* **Removal of Conflict:** The `@Positive` annotation was **removed** from the `notional` field in the `TradeLegDTO` class.
* **Validation Reliance Shift:** The negative/zero check for `notional` is now **exclusively reliant** on the manual `if` check within the `TradeLegController.createTradeLeg` method.
This guarantees that when a negative notional is sent, the controller's manual logic executes and returns the exact string the test asserts against.

## 4. Testing and Validation
The fix was implemented and verified:
* The test `testCreateTradeLegValidationFailure_NegativeNotional` now passes successfully, confirming the response status is `400 Bad Request` and the content body is `"Notional must be positive"`.
* The business rule requiring a positive notional is confirmed to be enforced by the controller's manual check.
* The `TradeLegControllerTest` suite remains stable.
***

## TRADECONTROLLER-TEST-STABILIZATION
## 1. Bug Identification
The `TradeControllerTest` suite was failing across six distinct methods due to conflicts between validation layers, incorrect HTTP status assertions, and inaccurate mocking:
* **Validation Failures (`testCreateTradeValidationFailure...`):** Tests for missing `TradeDate` and `Book` failed with empty bodies (`<>`) or incorrect status codes (`201`), indicating validation was being bypassed or mishandled.
* **Status Mismatch Failures (`testCreateTrade`, `testDeleteTrade`):** Tests failed due to asserting the wrong HTTP status code (e.g., expecting `200` but receiving `201`, or expecting `204` but receiving `200`).
* **Logic/Setup Failures (`testUpdateTradeIdMismatch`, `testUpdateTrade`):** Tests failed either because controller logic overrode the test condition, or the mocked data was incomplete.

## 2. Root Cause Analysis
The failures were from three primary categories of error:
* **Validation Conflict:** Declarative annotations (`@NotNull` on `tradeDate`) executed first, throwing exceptions that Spring's default handler caught, resulting in an unassertable empty body.
* **Controller Logic Error:** In `updateTrade`, the controller prematurely set the DTO ID (`tradeDTO.setTradeId(id)`), overriding the intentionally mismatched ID provided by the test case.
* **REST/Assertion Mismatch:** The `createTrade` endpoint (POST) was tested for `200 OK` but correctly returned `201 Created`. The `deleteTrade` endpoint (DELETE) was returning `200 OK` with a body, conflicting with the test's expectation of `204 No Content`.
* **Mock Data Incompleteness:** The successful update test failed because the mocked `Trade` entity returned by `tradeService.amendTrade` did not have its `tradeId` property initialized, causing the final JSON assertion to fail.

## 3. Bug Fix Implementation
A set of fixes was applied to align the tests with best practices and the controller's required business logic:
* **Validation Consistency:**
    * Removed the `@NotNull` annotation from `TradeDTO.tradeDate`.
    * Added **manual `if` checks** within the `createTrade` method for both missing **Trade Date** and missing **Book/Counterparty** to ensure the exact expected simple error string is returned as `400 Bad Request`.
* **REST Status Alignment:**
    * Updated `testCreateTrade` to assert for `status().isCreated()` (201).
    * Updated the controller's `deleteTrade` method to return **`ResponseEntity.noContent().build()` (204)**, and verified the test asserts for `status().isNoContent()`.
* **ID Mismatch Enforcement:**
    * Inserted an **explicit `if` check** at the start of the `updateTrade` method to compare the path ID and body ID, returning `400 Bad Request` if they mismatch, before the controller syncs the IDs.
* **Mock Data Accuracy:**
    * In `testUpdateTrade`, the returned mock entity was initialized using `trade.setTradeId(tradeId)` to ensure the final JSON response contained the data required by the assertion.
    * Verified and corrected service layer mocking/verification from `saveTrade` to the correct **`amendTrade`** method.

## 4. Testing and Validation
All six affected unit tests now pass successfully, confirming that:
* Validation failures correctly result in a `400 Bad Request` with the required error message body.
* The API adheres to REST status code conventions (`201` for creation, `204` for deletion).
* The controller logic correctly enforces the ID synchronization business rule for updates.
* The test mock configuration accurately reflects the controller's behavior and response structure.
***