# Refactoring Walkthrough: P2P Normalized Schema & 'Trip' Rename

I have completed the major architectural refactor to support future P2P synchronization. This involved two main tasks: normalizing the database schema and renaming the core "Event" concept to "Trip."

## Changes Made

### 1. Normalized P2P Database Schema
We moved away from storing participants and expenses as JSON blobs inside a single table. The new structure uses four dedicated tables:
- **`groups`**: Stores trip metadata (name, status, creation date).
- **`users`**: Stores participants, each linked to a `groupId`.
- **`expenses`**: Stores individual costs, each linked to a `groupId`.
- **`sync_events`**: The **P2P Ledger**. Every action (adding a trip, adding/editing/deleting an expense) now generates a `SyncEvent` record. This ledger allows other devices to "replay" actions and stay in sync over Bluetooth/Wi-Fi.

### 2. Conceptual Rename: 'Event' -> 'Trip'
To improve code clarity, all references to "ExpenseEvent" have been renamed to "Trip" or "Group".
- **[Models.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/Models.kt)**: `ExpenseEvent` class is now `Trip`.
- **[TripSetupScreen.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/ui/TripSetupScreen.kt)**: Renamed from `CreateEventScreen.kt`.
- **Navigation**: Routes now use `setup_trip` and `tripId`.

### 3. Event Sourcing in Repository
The **[LocalRepository.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/LocalRepository.kt)** now automatically logs every local change into the `sync_events` table. This prepares the app for Phase 2 (Bluetooth/Wi-Fi syncing).

### 4. Educational Comments
As requested, I have added detailed comments to the following files to explain the new architecture:
- [Models.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/Models.kt): Explains normalization and the sync ledger.
- [Relations.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/Relations.kt): Explains the "Read Model" used for the UI.
- [LocalRepository.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/LocalRepository.kt): Explains the write-logging logic.

## Verification Results
- **Build**: Successfully compiled (Database version bumped to `5`).
- **Unit Tests**: Updated and passed `SplitLogicTest`.
- **UI Flow**: Verified that creating a trip and adding/editing/deleting expenses works seamlessly with the new multi-table structure.

> [!NOTE]
> Due to the schema change, previous test data was reset. You can now start fresh with the new "Trip" based system!
