# Split Expenses App Implementation Plan - Phase 3 (Enhanced Expense Flow)

This phase streamlines the navigation between trips and individual expenses, making the "Add Expense" screen the primary dashboard for a trip.

## User Review Required

> [!IMPORTANT]
> **Redirection Logic**: Tapping "Save Expense" will no longer return you to the previous screen. It will clear the input form so you can quickly add multiple expenses (e.g., Lunch, then Dinner) without leaving the page.

## Proposed Changes

### UI Layer (Compose)

#### [MODIFY] [MainScreen.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/ui/MainScreen.kt)
- **Navigation Update**: Change the click action on trip items (e.g., "Udaipur Trip") to navigate directly to the **Add Expense** screen instead of the Trip Details screen.

#### [MODIFY] [AddExpenseScreen.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/ui/AddExpenseScreen.kt)
- **Header Refinement**: Change the TopAppBar title to dynamically show **"[Event Name] Expenses"** (e.g., "Udaipur Trip Expenses").
- **New Section: "expenses"**:
    - Add a section below the "Shared By" dropdown.
    - Title "expenses" with an underline (matching the Main Screen style).
    - A list (LazyColumn) showing all expenses already added to this trip (e.g., "Lunch: ₹500").
- **Behavior Update**:
    - When "Save Expense" is clicked:
        1. Save the expense to the repository.
        2. **Clear the input fields** (Name, Amount) so the form is ready for the next entry.
        3. The new expense will immediately appear in the "expenses" section below.
        4. No screen redirection.

### Logic & Navigation

#### [MODIFY] [MainActivity.kt](file:///C:/Users/dnime/AndroidStudioProjects/Divide/app/src/main/java/com/example/divide/MainActivity.kt)
- Update the `main` -> `add_expense` connection.
- Ensure the `AddExpenseScreen` callback doesn't force a back navigation on every save.

---

## Verification Plan

### Manual Verification
- **Navigation**: Click "Udaipur Trip" on Main Screen -> Verify you land on the "Udaipur Trip Expenses" screen.
- **Continuous Adding**:
    1. Add "Lunch", "500" -> Click Save.
    2. Verify the form clears and "Lunch" appears in the list below.
    3. Add "Dinner", "1000" -> Click Save.
    4. Verify both "Lunch" and "Dinner" are visible.
- **Header**: Verify the title is correct.
