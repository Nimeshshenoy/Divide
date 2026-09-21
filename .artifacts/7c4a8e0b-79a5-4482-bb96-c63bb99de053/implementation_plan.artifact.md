# Implementation Plan: Event Processor for P2P Synchronization

This phase implements the `EventProcessor`, which is responsible for applying incoming `SyncEvent`s to the local database state. This component ensures that all devices eventually reach the same state (Eventual Consistency) and prevents duplicate processing.

## User Review Required

> [!IMPORTANT]
> **Deterministic Tie-Breaking**: For `UPDATE` events, we will implement logic that compares the incoming event's timestamp and ID against the current record to ensure that the "winning" state is the same on all devices, regardless of the order in which events are received.

## Proposed Changes

### Data Layer (DAO)

#### [MODIFY] [ExpenseDao.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/ExpenseDao.kt)
- Add `getSyncEventById(eventId: String)` to check for duplicates.
- Add `getExpenseById(id: String)` and `getUserById(id: String)` for conflict resolution checks.

---

### Logic Layer (Sync)

#### [NEW] [EventProcessor.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/sync/EventProcessor.kt)
Create a centralized processor with the following structure:
- **`processIncomingEvent(event: SyncEvent)`**: The entry point.
    1. Checks if the `eventId` already exists in the ledger (Deduplication).
    2. Routes the event to specific handlers based on `type`.
    3. Saves the event to the ledger and updates the state tables in a single **Transaction**.
- **Handlers**:
    - `handleAddTrip`: Parses payload to `Group` and `User` list.
    - `handleAddExpense`: Parses payload to `Expense`.
    - `handleUpdateExpense`: Implements tie-breaking logic (Timestamp > Lexicographical ID) before updating.
    - `handleDeleteExpense`: Marks the expense as `isDeleted`.
    - `handleUpdateUserName`: Updates participant names.

---

### Repository Integration

#### [MODIFY] [LocalRepository.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/LocalRepository.kt)
- Provide a reference to the `EventProcessor`.
- This allows the future networking layer to simply call `LocalRepository.processor.processIncomingEvent(event)`.

## Verification Plan

### Automated Tests
- **[NEW] [EventProcessorTest.kt]**:
    - Test deduplication: Send the same event twice and verify the payload is only applied once.
    - Test tie-breaking: Send an "older" update after a "newer" one and verify the database keeps the newer state.

### Manual Verification
- Since networking isn't built yet, we can verify this by manually "injecting" a mock `SyncEvent` into the processor via a temporary test button or logcat command.
