## 1. BookServiceTest Fixes

### 1.1 Fix: BookServiceTest NullPointer on Missing Dependencies (Find/Save Methods)
| Requirement | Documentation |
| :--- | :--- |
| **Problem Description** | Multiple tests in `BookServiceTest` (including `testFindBookById`, `testFindBookByNonExistentId`, and `testSaveBook`) were failing immediately with **`NullPointerException`** when attempting to use the `BookMapper` or `CostCenterRepository` dependencies. |
| **Root Cause Analysis** | The test class was missing the necessary **`@Mock`** annotations for its required dependencies (`BookMapper` and `CostCenterRepository`). Consequently, when **`@InjectMocks`** attempted to initialize `BookService`, these dependencies remained `null`, leading to the failure as soon as any service method tried to invoke them. |
| **Solution Implemented** | Added the **`@Mock`** annotation declarations for both the `BookMapper` and `CostCenterRepository` to the `BookServiceTest` class. This allowed Mockito to correctly initialize these mock objects and subsequently inject them into the `BookService` instance, resolving the `NullPointerException`s. |
| **Verification** | 1 of the affected tests now proceed past the dependency injection phase and execute their logic successfully. |

### 1.2 Fix: BookServiceTest DTO Conversion Failure in findBookById
| Requirement | Documentation |
| :--- | :--- |
| **Problem Description** | The test `testFindBookById` failed with an assertion error (`expected: <true> but was: <false>`). The service method returned an empty `Optional` instead of a populated `Optional<BookDTO>`. |
| **Root Cause Analysis** | This failure was a **consequence of the previous fix (1.1 Fix)**. After resolving the `NullPointerException` by adding the `@Mock BookMapper`, the mapper was initialized but had **no defined behavior**. Mockito's default behavior caused the `bookMapper.toDto()` call to return **`null`**, preventing the final `Optional` from being correctly populated with the DTO. |
| **Solution Implemented** | A complete stubbing setup was implemented for the `BookMapper.toDto` method. This involved creating an `expectedDto` object and adding the Mockito stub: `when(bookMapper.toDto(book)).thenReturn(expectedDto)`. This ensures the conversion returns a non-null and valid result. |
| **Verification** | The test now passes, confirming successful reading of the entity from the repository and accurate mapping to the DTO. |

### 1.3 Fix: BookServiceTest PotentialStubbingProblem in `testSaveBook`
| Requirement | Documentation |
| :--- | :--- |
| **Problem Description** | The test `testSaveBook` failed with a **`PotentialStubbingProblem`** error. The error message indicated that the `bookRepository.save()` method was called with a `null` argument. |
| **Root Cause Analysis** | This was as a result of missing mock behavior. The service method calls the mapper's `toEntity` method to convert the incoming DTO into a storable entity. Since the `bookMapper.toEntity()` method behavior was undefined, Mockito returned **`null`** by default. This `null` value was then passed as the argument to the `bookRepository.save()` call, causing the failure. |
| **Solution Implemented** | Added Mockito stubs for the **`bookMapper.toEntity()`** method to ensure it returns a valid, non-null `Book` entity object. Additionally, ensured **`bookMapper.toDto()`** was also stubbed for the final return mapping. This completes the full flow of DTO $\rightarrow$ Entity $\rightarrow$ Save $\rightarrow$ DTO with valid objects. |
| **Verification** | The test now passes successfully, confirming that the `BookService.saveBook()` method correctly handles the conversion and persistence flow. |
***

## 2. TradeServiceTest Fixes

### 2.1 Fix: TradeServiceTest Stabilize createTrade Success Path Dependencies
| Requirement | Documentation |
| :--- | :--- |
| **Problem Description** | The test `testCreateTrade_Success` failed due to a cascade of errors indicating missing mandatory reference data, specifically: "Book not found or not set," "Counterparty not found or not set," and subsequent `NullPointerException`s (NPEs) on `ApplicationUserRepository` and during `TradeLeg.getLegId()` access. |
| **Root Cause Analysis** | The trade service relies on various repositories to populate mandatory reference data (Book, Counterparty, User, etc.) before saving a trade. The test setup was incomplete, lacking the necessary **`@Mock` declarations** for these required repositories and failing to **stub the `findBy*` methods** to return valid, non-null entities. This led to conditional failures and direct NPEs. |
| **Solution Implemented** | **1. Dependency Injection:** Added missing `@Mock` declarations for `BookRepository`, `CounterpartyRepository`, and `ApplicationUserRepository`. **2. Stubbing:** Implemented Mockito stubs for all necessary repository lookups to return valid mock entities (Book, Counterparty, User, TradeStatus). **3. TradeLeg Mocking:** Added stubbing for `tradeLegRepository.save()` to ensure it returns a valid, non-null `TradeLeg` object, preventing the final NPE. |
| **Verification** | The test now executes and passes completely, verifying the successful completion of the trade creation process. |

### 2.2 Fix: TradeServiceTest Stabilize amendTrade Success Path
| Requirement | Documentation |
| :--- | :--- |
| **Problem Description** | The test `testAmendTrade_Success` failed due to cascading **`NullPointerException`s (NPEs)**. The failures occurred when the service tried to access the **`version`** field of the existing `Trade` entity and subsequently when it processed the **`TradeLeg`** without a valid ID. |
| **Root Cause Analysis** | The failures stemmed from incomplete initialization of the mock objects. **1.** The mock `Trade` entity that was looked up for amendment was missing the crucial **`version` field initialization**, which is mandatory for optimistic locking or version incrementing in the service logic. **2.** The subsequent trade leg processing failed because the nested **`tradeLegRepository.save()`** call was un-stubbed, returning `null` or a faulty object that lacked a valid `LegId`. |
| **Solution Implemented** | **1. Version Initialization:** Initialized the `version` field (e.g., `trade.setVersion(1)`) on the mock `Trade` object used in the test. **2. Nested Stubbing:** Added stubbing for the **`tradeLegRepository.save()`** call to ensure it returns a valid, non-null mock `TradeLeg` object with an initialized `LegId`, preventing the NPE. |
| **Verification** | The test now passes, confirming that the trade amendment process can correctly handle versioning and nested entity updates without failing. |

### 2.3 Fix: TradeServiceTest Correct Date Validation Assertion
| Requirement | Documentation |
| :--- | :--- |
| **Problem Description** | The test `testCreateTrade_InvalidDates_ShouldFail` was failing due to an assertion error. The error message indicated a mismatch between the expected exception text and the actual exception text thrown: `expected: <Wrong error message> but was: <Start date cannot be before trade date>`. |
| **Root Cause Analysis** | The root cause was with the test assertion itself. The test's expected exception message was incorrect ("Wrong error message") and did not match the precise exception message string thrown by the `TradeService` validation logic. |
| **Solution Implemented** | Updated the `assertEquals` call within the test to accurately assert the message thrown by the service. The expected value was changed to the correct string: `"Start date cannot be before trade date"`. |
| **Verification** | The test now passes, confirming that the trade date validation business rule is correctly enforced and the service throws the expected exception message upon failure. |


### 2.4 Fix: TradeServiceTest Correctly Verify Cashflow Generation Count
| Requirement | Documentation |
| :--- | :--- |
| **Problem Description** | The test `testCashflowGeneration_MonthlySchedule` was failing with a placeholder assertion error: `expected: <1> but was: <12>`. The test was not verifying the actual cashflow saving logic. |
| **Root Cause Analysis** | This test failed due to **three combined issues**: **1.** The test contained an incorrect assertion (`assertEquals(1, 12)`). **2.** The DTO lacked explicit configuration for the `"Monthly"` payment schedule, hindering the calculation logic. **3.** Necessary reference data repositories (`ScheduleRepository`, etc.) and the return values for entity saving (`tradeLegRepository.save()`) were un-stubbed, preventing the cashflow generation method from accessing the mandatory nested entities like `Schedule`. |
| **Solution Implemented** | **1. Configuration:** Configured the `TradeDTO` with the required `"Monthly"` schedule. **2. Dependency Setup:** Added and stubbed all missing reference data repositories. **3. Nested Mocking:** Used **`Mockito.thenAnswer`** on `tradeLegRepository.save()` to ensure the saved `TradeLeg` mock entity was returned with the necessary **`Schedule` object attached**. **4. Assertion Correction:** Replaced the broken placeholder with the appropriate verification: `verify(cashflowRepository, times(24)).save(any(Cashflow.class))` (2 trade legs $\times$ 12 monthly payments). |
| **Verification** | The test now passes, successfully verifying that the `TradeService` correctly calls the `cashflowRepository.save()` method 24 times, confirming the complex monthly cashflow generation logic works as intended. |
***

## 3. TradeLegControllerTest

### 3.1. Fix: TradeLeg Validation Failure (Negative Notional)
| Requirement | Documentation |
| :--- | :--- |
| **Problem Description** | The unit test `TradeLegControllerTest.testCreateTradeLegValidationFailure_NegativeNotional` was failing with `Response content expected:<Notional must be positive> but was:<>`. The test expected a specific error string but received an empty response body. |
| **Root Cause Analysis** | A **validation conflict** existed. The declarative **`@Positive`** annotation on `TradeLegDTO.notional` executed first via the `@Valid` annotation. This threw a `MethodArgumentNotValidException`, which Spring's default handler caught, resulting in an unassertable empty response body (`<>`). This prevented the manual controller `if` check (which returned the expected string) from executing. |
| **Solution Implemented** | Removed the conflicting **`@Positive`** annotation from the `TradeLegDTO.notional` field. This ensures the code flow proceeds to the manual `if` check in `TradeLegController`, guaranteeing the return of the expected error string. This approach was chosen to align the execution flow with the existing assertion logic. |
| **Verification** | The test now passes successfully, confirming a response status of `400 Bad Request` and the correct content body: `"Notional must be positive"`. |
***

## 4. TradeControllerTest

### 4.1 Fix: Create Trade Status Code Mismatch
| Requirement | Documentation |
| :--- | :--- |
| **Problem Description** | The test `testCreateTrade()` was failing with `Status expected:<200> but was:<201>`. The test expected a successful status code for the creation of a new trade. |
| **Root Cause Analysis** | The root cause was an **incorrect test assertion**. The controller correctly implements the REST standard for creating a new resource (via a POST request) by returning **`201 Created`**. The test, however, incorrectly asserted for `status().isOk()` (HTTP 200). |
| **Solution Implemented** | Updated the assertion in `testCreateTrade()` from `status().isOk()` to **`status().isCreated()` (HTTP 201)**. This approach was chosen to align the test's expectation with the correct and standard behavior of the REST API endpoint for resource creation. |
| **Verification** | The test now passes successfully, verifying the correct `201 Created` status is returned for a successful trade creation. |

### 4.2 Fix: Create Trade Validation Failure (Missing Book)
| Requirement | Documentation |
| :--- | :--- |
| **Problem Description** | The test `testCreateTradeValidationFailure_MissingBook` was failing with `Status expected:<400> but was:<201>`. The server was incorrectly returning a success status (201 Created) for an invalid request missing the mandatory 'Book' field. |
| **Root Cause Analysis** | The validation for the required 'Book' field was **missing in the controller's immediate checks** before the trade processing logic. The invalid request bypassed validation and successfully executed the trade creation service logic, leading to the incorrect `201 Created` success status. |
| **Solution Implemented** | Added explicit **manual validation** inside the `createTrade` method (`if (tradeDTO.getBookName() == null \|\| tradeDTO.getCounterpartyName() == null)`). This check now intercepts the invalid request and immediately returns the expected `400 Bad Request` status. |
| **Verification** | The test now passes, successfully confirming the immediate return of the `400 Bad Request` status when the mandatory Book field is absent. |


### 4.3 Fix: Update Trade ID Mismatch Validation
| Requirement | Documentation |
| :--- | :--- |
| **Problem Description** | The test `testUpdateTradeIdMismatch` was failing with `Status expected:<400> but was:<200>`. The update operation was incorrectly succeeding when the ID provided in the URL path did not match the ID in the request body. |
| **Root Cause Analysis** | The controller's `updateTrade` method contained the line `tradeDTO.setTradeId(id)`, which **overrode the request body's ID** with the path ID immediately upon entry. This action synchronized the IDs before any validation could check for the intended mismatch, causing the trade to be processed successfully and return `200 OK`. |
| **Solution Implemented** | Added an explicit **manual validation check** (`if (tradeDTO.getTradeId() != null && !id.equals(tradeDTO.getTradeId()))`) at the start of the `updateTrade` method. This check executes before ID synchronization, intercepting any mismatch and immediately returning the expected `400 Bad Request` status. |
| **Verification** | The test now passes, confirming that the business rule enforcing ID congruence for update operations is correctly validated and reported. |

### 4.4 Fix: Delete Trade Status Code Mismatch
| Requirement | Documentation |
| :--- | :--- |
| **Problem Description** | The test `testDeleteTrade` was failing with `Status expected:<204> but was:<200>`. The controller was returning a success code with a body message for a deletion. |
| **Root Cause Analysis** | The `DELETE` endpoint in `TradeController` was explicitly returning `ResponseEntity.ok().body(...)` (HTTP 200). The **RESTful standard** for a successful `DELETE` operation when no content or resource representation is returned in the response body is **`204 No Content`**. This mismatch caused the test assertion to fail. |
| **Solution Implemented** | Updated the controller's `deleteTrade` method to return **`ResponseEntity.noContent().build()` (HTTP 204)**. This approach was chosen to align the API's behavior with established REST conventions for a successful resource deletion. |
| **Verification** | The test now passes successfully, confirming that the API correctly returns `204 No Content` upon successful trade deletion. |

### 4.5 Fix: Create Trade Validation Failure (Missing Trade Date)
| Requirement | Documentation |
| :--- | :--- |
| **Problem Description** | The test `testCreateTradeValidationFailure_MissingTradeDate` was failing with `Response content expected:<Trade date is required> but was:<>`. The test expected a specific error string but received an empty response body. |
| **Root Cause Analysis** | The declarative **`@NotNull`** annotation on the `TradeDTO.tradeDate` field triggered first via the `@Valid` annotation. This failed validation and threw an unhandled `MethodArgumentNotValidException`, which Spring's default error handler caught. This resulted in an unassertable **empty response body (`<>`)**, preventing the expected error string from being returned. |
| **Solution Implemented** | Removed the conflicting **`@NotNull`** annotation from `TradeDTO.tradeDate`. Then, a corresponding **manual `if` check** (`if (tradeDTO.getTradeDate() == null)`) was implemented in the `createTrade` controller method. This ensures the code path executes the logic that returns the exact required string `"Trade date is required"`. |
| **Verification** | The test now passes successfully, confirming that the correct `400 Bad Request` status and the expected error message are returned when the trade date is missing. |

### 4.6 Fix: Update Trade Response Mocking Accuracy

| Requirement | Documentation |
| :--- | :--- |
| **Problem Description** | The test `testUpdateTrade` was failing with the error **`No value at JSON path "$.tradeId"`**. This occurred when the test tried to assert the presence of the ID in the successful JSON response. |
| **Root Cause Analysis** | The failure had **two parts**: **1.** The mocked `Trade` entity object, which the service returned, was missing its required **`tradeId` initialization**. **2.** The test incorrectly stubbed and verified `tradeService.saveTrade` when the controller's logic correctly calls **`tradeService.amendTrade`**. The missing ID caused the primary failure; the incorrect method name caused a testing fidelity issue. |
| **Solution Implemented** | **1. Data Fix:** Explicitly called **`trade.setTradeId(tradeId)`** within the test setup to ensure the mocked entity contained the necessary ID before mapping. **2. Mocking Fix:** Updated the Mockito stubbing (`when`) and verification (`verify`) calls to use the correct service method, **`.amendTrade`**, aligning the test with the actual controller logic. |
| **Verification** | The test now passes, successfully confirming that the returned JSON response contains the correct `tradeId` and that the test accurately verifies the controller's interaction with the correct service method. |
***