# Implementation Plan: Database Refactoring for P2P Sync (Event Sourcing)

This phase refactors the database from a single "Trip" entity with JSON blobs into a normalized structure with four tables: `groups`, `users`, `expenses`, and `sync_events`. This architecture is designed to support robust, conflict-free synchronization over Bluetooth/Wi-Fi.

## User Review Required

> [!WARNING]
> **Database Wipe**: This refactor involves a fundamental change to the schema. Existing local data will be reset.
> [!IMPORTANT]
> **Event Sourcing**: We are moving towards a system where the "Source of Truth" is the `sync_events` ledger. The other tables effectively become a "Read Model" derived from these events.
> [!NOTE]
> **Educational Comments**: I will include detailed comments throughout the new code to explain how the normalized schema, relations, and sync ledger work.

## Proposed Changes

### Data Layer (Entities)

#### [MODIFY] [Models.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/Models.kt)
Define the new normalized structure:
- **`Group`**: Replaces the basic metadata of `Trip`.
- **`User`**: Now a separate entity table (to support global user tracking).
- **`Expense`**: Now a separate entity table linked to `groupId`.
- **`SyncEvent`**: The new ledger table with:
    - `eventId` (Primary Key, unique UUID)
    - `groupId`
    - `authorId`
    - `type` (e.g., "ADD_EXPENSE", "UPDATE_GROUP", "ADD_USER")
    - `payload` (JSON blob containing the data for that event)
    - `createdAt`
    - `signature` (For future security verification)

#### [NEW] [Relations.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/Relations.kt)
Define Room "POJOs" to handle relationships:
- `TripWithDetails`: A class that combines a `Group`, its list of `User`s, and its list of `Expense`s for UI consumption.

---

### Data Access (DAOs)

#### [MODIFY] [ExpenseDao.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/ExpenseDao.kt)
- Add separate insert/update methods for each table.
- Add complex queries using `@Transaction` to fetch `TripWithDetails`.
- Add methods to query the `sync_events` table (essential for Bluetooth syncing later).

#### [MODIFY] [ExpenseDatabase.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/ExpenseDatabase.kt)
- Add the new entities to the `@Database` annotation.
- Bump version to `5`.

---

### Repository & Logic

#### [MODIFY] [LocalRepository.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/LocalRepository.kt)
- Update `addTrip` and `addExpense` logic.
- **Key Change**: When adding data, the repository should now:
    1. Insert the data into the specific table (`Group` or `Expense`).
    2. **Automatically generate and save a `SyncEvent`** representing this action. This ensures that any local change is ready to be broadcasted to friends.

#### [MODIFY] [SplitLogic.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/logic/SplitLogic.kt)
- Update the calculation engine to accept the new `TripWithDetails` structure.

---

### UI Layer

#### [MODIFY] UI Screens
- Update `MainScreen.kt`, `TripSetupScreen.kt`, and `AddExpenseScreen.kt` to consume the new `TripWithDetails` objects.
- Most UI logic remains similar, but the way data is passed and collected from the repository will change to reflect the multi-table structure.

## Verification Plan

### Automated Tests
- **Unit Tests**: Update `SplitLogicTest.kt` for the new models.
- **Database Tests**: Verify that inserting a `SyncEvent` works and that relationships (fetching expenses for a group) are correct.

### Manual Verification
1. Create a trip -> Verify a `SyncEvent` of type "ADD_TRIP" is generated in the DB (via logs).
2. Add an expense -> Verify a `SyncEvent` of type "ADD_EXPENSE" is generated.
3. Verify that the UI correctly displays totals by joining data from the separate tables.
