# Implementation Plan: Robust P2P Conflict Resolution

This phase hardens the `EventProcessor` to handle edge cases like out-of-order events, "resurrection" of deleted items, and atomicity for user updates. We will implement a formal "Last-Writer-Wins" strategy using event metadata.

## User Review Required

> [!IMPORTANT]
> **State Tracking**: I will add a `lastEventId` field to our state tables (`Group`, `User`, `Expense`). This allows the processor to accurately compare an incoming event against the one that produced the *current* local state.

## Proposed Changes

### Data Layer (Models & DB)

#### [MODIFY] [Models.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/Models.kt)
- Add `lastEventId: String?` to `Group`, `User`, and `Expense`.
- This field stores the `eventId` of the `SyncEvent` that last modified this specific record.

#### [MODIFY] [ExpenseDatabase.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/ExpenseDatabase.kt)
- Bump version to `8`.

---

### Data Access (DAO)

#### [MODIFY] [ExpenseDao.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/ExpenseDao.kt)
- Add `@Transaction` method for `updateUserWithSync`.
- Add `@Transaction` method for `deleteExpenseWithSync` (to handle tombstones atomically).

---

### Logic Layer (Event Processor)

#### [MODIFY] [EventProcessor.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/sync/EventProcessor.kt)
- **Implement `isIncomingWinner(incoming: SyncEvent, currentEventId: String?)`**:
    - Fetches the `SyncEvent` associated with `currentEventId`.
    - Returns `true` if `incoming.createdAt > current.createdAt`.
    - If timestamps are equal, returns `true` if `incoming.eventId > current.eventId` (Lexicographical tie-break).
- **Refine `handleAddExpense`**:
    - Check if expense already exists. If it exists and is marked `isDeleted`, and the incoming event is "older" than the delete event, ignore the add.
- **Refine `handleDeleteExpense`**:
    - If the expense doesn't exist yet, create a "tombstone" record (isDeleted=true) to prevent future out-of-order adds from resurrecting it.
- **Refine `handleUpdateExpense`**:
    - Use `isIncomingWinner` to ensure we don't overwrite a newer state with an older one received late.

---

### Repository

#### [MODIFY] [LocalRepository.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/LocalRepository.kt)
- Update local write methods (`addExpense`, etc.) to generate and store the `lastEventId` immediately upon creation.

## Verification Plan

### Automated Tests
- **[MODIFY] [EventProcessorTest.kt]**:
    - **Test Late Arrival**: Send a "New" update, then an "Old" update. Verify the DB keeps the "New" values.
    - **Test Tombstone**: Send a "Delete", then an "Add". Verify the item remains deleted.
    - **Test Tie-Break**: Send two events with the exact same timestamp but different `eventId`s. Verify the winner is consistent.
