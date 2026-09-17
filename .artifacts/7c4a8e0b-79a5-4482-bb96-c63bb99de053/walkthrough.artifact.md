# Split Expenses App Implementation Walkthrough - Profile Section

I have implemented the Profile section, allowing users to set a custom name that persists across app restarts and is used throughout the expense splitting flow.

## Changes Made

### Data Layer
- **[Profile.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/Profile.kt)**: Created a new Room entity to store the local user's name.
- **[ExpenseDao.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/ExpenseDao.kt)**: Added methods to query and update the profile.
- **[ExpenseDatabase.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/ExpenseDatabase.kt)**: Included the `Profile` entity in the database schema.
- **[LocalRepository.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/data/LocalRepository.kt)**: Added a reactive `profile` StateFlow and an `updateProfile` method.

### UI Screens
- **[ProfileScreen.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/ui/ProfileScreen.kt)**: A new screen where users can enter and save their name.
- **[MainScreen.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/ui/MainScreen.kt)**: Added a profile icon to the top bar for easy access.
- **[CreateEventScreen.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/ui/CreateEventScreen.kt)**: Updated to use the profile name as the default participant instead of "Me".
- **[AddExpenseScreen.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/ui/AddExpenseScreen.kt)**: Updated all dropdowns and lists to display the user's custom name for the local player.

### Infrastructure
- **[MainActivity.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/MainActivity.kt)**: Added the `profile` route to the navigation graph.

## Verification Results
- **Unit Tests**: Passed successfully.
- **Persistence**: Verified that the profile name is correctly saved to Room and loaded on app startup.
- **UI Reactivity**: Verified that updating the profile name immediately reflects in the Create Event and Add Expense screens.
