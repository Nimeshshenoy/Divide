# Split Expenses App Implementation Plan - Profile Section

This phase introduces a Profile section where the user can set their own name, which will then be used throughout the app (e.g., in "Paid by" dropdowns and event participation) instead of a hardcoded "Me".

## User Review Required

> [!NOTE]
> **Persistence**: I will use a separate table in the Room database or a simple `DataStore` to persist your profile name so it survives app restarts.

## Proposed Changes

### Data Layer
#### [NEW] [Profile.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/Profile.kt)
Define a simple `Profile` entity to store local user settings.
- `id`: Fixed ID (e.g., 0) for the single local profile.
- `name`: The user's display name.

#### [MODIFY] [ExpenseDao.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/ExpenseDao.kt)
Add methods to get and update the local profile.

#### [MODIFY] [ExpenseDatabase.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/ExpenseDatabase.kt)
Include the new `Profile` entity in the database.

#### [MODIFY] [LocalRepository.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/LocalRepository.kt)
Add a `profile` StateFlow to observe the local user's name.

### UI Layer (Compose)
#### [NEW] [ProfileScreen.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/ui/ProfileScreen.kt)
- A simple form with a "Username" text field.
- A "Save" button to update the profile.

#### [MODIFY] [MainScreen.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/ui/MainScreen.kt)
- Add a "Profile" icon or button in the `TopAppBar` to navigate to the Profile screen.

#### [MODIFY] [CreateEventScreen.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/ui/CreateEventScreen.kt)
- Update the default user list to use the name from the saved profile instead of "Me".

#### [MODIFY] [AddExpenseScreen.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/ui/AddExpenseScreen.kt)
- Ensure the "Me" user displays the correct name from the profile.

### Logic & Navigation
#### [MODIFY] [MainActivity.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/MainActivity.kt)
- Add the `profile` route to the NavHost.

## Verification Plan

### Manual Verification
1. Open the app and navigate to the **Profile** screen via the icon in the TopAppBar.
2. Enter a new name (e.g., "John Doe") and click **Save**.
3. Go to "Create expense event". Verify that the first participant slot shows "John Doe" instead of "Me".
4. Add an expense. Verify that "Paid by" dropdown shows "John Doe".
5. Close and restart the app. Verify that the name "John Doe" is still preserved.
