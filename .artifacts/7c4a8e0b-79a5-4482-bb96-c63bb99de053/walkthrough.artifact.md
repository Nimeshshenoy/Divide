# Refactoring Walkthrough: From 'Event' to 'Trip'

I have successfully renamed the core concept of the app from "Event" to "Trip" throughout the entire project. This change improves code readability and aligns the technical terminology with the app's real-world use case (e.g., "Udaipur Trip").

## Changes Made

### Data & Persistence
- **[Models.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/Models.kt)**: Renamed `ExpenseEvent` to `Trip`.
- **[ExpenseDao.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/ExpenseDao.kt)**: Renamed all method signatures (e.g., `getAllTrips`, `insertTrip`) and updated SQL queries to target the `trips` table.
- **[ExpenseDatabase.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/ExpenseDatabase.kt)**: Updated the entity list and bumped the version to `4`.
- **[LocalRepository.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/LocalRepository.kt)**: Renamed variables and functions to use "Trip" nomenclature (e.g., `trips` flow, `addTrip` function).

### UI & Navigation
- **[MainScreen.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/ui/MainScreen.kt)**: Updated all UI labels and internal logic to use `Trip`.
- **[TripSetupScreen.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/ui/TripSetupScreen.kt)**: Created this file as the new home for trip creation (formerly `CreateEventScreen.kt`).
- **[AddExpenseScreen.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/ui/AddExpenseScreen.kt)**: Updated parameters and titles.
- **[MainActivity.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/MainActivity.kt)**: Updated all navigation routes (e.g., `setup_trip` instead of `create_event`) and arguments.

### Logic
- **[SplitLogic.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/logic/SplitLogic.kt)**: Updated the calculation engine to work with the `Trip` class.
- **[SplitLogicTest.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/test/java/com/example/divide/logic/SplitLogicTest.kt)**: Updated all unit tests to use the new naming.

## Verification Results
- **Build**: Successfully compiled with zero errors.
- **Unit Tests**: All 3 tests passed.
- **Database**: Verified that the new `trips` table is correctly initialized.

> [!NOTE]
> Since the table name changed, any existing test data on your device was reset during the migration to version 4.
