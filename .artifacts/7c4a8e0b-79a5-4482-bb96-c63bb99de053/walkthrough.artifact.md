# P2P Engine Hardening Walkthrough

I have implemented a robust conflict resolution system in the `EventProcessor` to handle the complex scenarios of Peer-to-Peer synchronization.

## Changes Made

### 1. Conflict Tracking in State Tables
I added a `lastEventId` field to [Models.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/Models.kt) for `Group`, `User`, and `Expense`. This field records which event currently "owns" that record, allowing the processor to compare incoming data against the current state accurately.

### 2. Deterministic "Last-Writer-Wins" Logic
Implemented `isIncomingWinner` in [EventProcessor.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/sync/EventProcessor.kt):
- **Primary Check**: The event with the later `createdAt` timestamp wins.
- **Tie-Breaker**: If timestamps are identical, the event with the lexicographically larger `eventId` string wins.
- This ensures every phone in the group will pick the exact same "winner" for an update, regardless of the order they receive messages.

### 3. Protection Against "Resurrection"
- **Tombstones**: If a delete event arrives for an expense that hasn't even been added yet, the app now creates a "Deleted Tombstone" record.
- **Add Validation**: The `handleAddExpense` logic now checks if a tombstone already exists. If an "ADD" event arrives that is *older* than a known "DELETE" event, the add is rejected.

### 4. Atomic Sync Transactions
Added new transactional methods to [ExpenseDao.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/ExpenseDao.kt) (like `updateUserWithSync`). This ensures that updating a user's name or an expense amount always happens in the same database breath as logging the event in the ledger.

## Verification Results
I added and ran new unit tests in [EventProcessorTest.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/test/java/com/example/divide/sync/EventProcessorTest.kt):
- `test conflict resolution - newer event wins`: **PASSED**
- `test conflict resolution - late arriving old event is ignored`: **PASSED**
- `test tombstone - delete prevents resurrection from late add`: **PASSED**

> [!TIP]
> The database has been bumped to **Version 8**. Existing test data has been cleared to allow for the new `lastEventId` architecture.
