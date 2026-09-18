# Refactoring: Renaming 'Event' to 'Trip'

This plan details the steps to rename all occurrences of "Event" (as in ExpenseEvent) to "Trip" to improve code clarity and domain alignment.

## User Review Required

> [!CAUTION]
> **Database Reset**: Since I am changing the table name from `events` to `trips`, the existing local data will be reset (wiped) because we are using `fallbackToDestructiveMigration`.

## Proposed Changes

### Data Layer
- **[MODIFY] [Models.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/Models.kt)**: Rename `ExpenseEvent` class to `Trip` and update its `@Entity` table name.
- **[MODIFY] [ExpenseDao.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/ExpenseDao.kt)**: Rename methods (`getAllEvents` -> `getAllTrips`, etc.) and update SQL queries.
- **[MODIFY] [ExpenseDatabase.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/ExpenseDatabase.kt)**: Update entity list and bump version.
- **[MODIFY] [Converters.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/Converters.kt)**: Update any "Event" references if they exist.

### Logic Layer
- **[MODIFY] [SplitLogic.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/logic/SplitLogic.kt)**: Update parameter types and internal variable naming.
- **[MODIFY] [SplitLogicTest.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/test/java/com/example/divide/logic/SplitLogicTest.kt)**: Update test cases to use the `Trip` class.

### Repository
- **[MODIFY] [LocalRepository.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/LocalRepository.kt)**: Rename variables (`_eventsFlow` -> `_tripsFlow`) and methods (`addEvent` -> `addTrip`).

### UI Layer
- **[MODIFY] [MainScreen.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/ui/MainScreen.kt)**: Rename "Event" references to "Trip".
- **[MODIFY] [CreateEventScreen.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/ui/CreateEventScreen.kt)**: Rename UI components and parameters. (Will rename the file to `TripSetupScreen.kt` in the final step).
- **[MODIFY] [AddExpenseScreen.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/ui/AddExpenseScreen.kt)**: Update internal logic and parameter names (e.g., `eventId` -> `tripId`).

### Navigation & Files
- **[MODIFY] [MainActivity.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/MainActivity.kt)**: Update navigation routes (`create_event` -> `setup_trip`, etc.).
- **[RENAME] File Move**: Rename `CreateEventScreen.kt` to `TripSetupScreen.kt`.

## Verification Plan
- **Build**: Ensure the project compiles without errors.
- **Tests**: Run `SplitLogicTest` to verify that calculations still work with the new class name.
- **App Flow**: Verify navigating from Main -> Trip Setup -> Add Expense works as expected.
