<p align="center">
  <img src="docs/assets/oneledger_brand_master_v2.png" width="132" alt="OneLedger logo" />
</p>

<h1 align="center">OneLedger</h1>

<p align="center">
  A fast, clear, local-first personal finance app for Android
</p>

<p align="center">
  <a href="README.md">简体中文</a> · <strong>English</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-23%2B-3DDC84?logo=android&logoColor=white" alt="Android 23+" />
  <img src="https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 2.3.21" />
  <img src="https://img.shields.io/badge/Jetpack-Compose-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Storage-Room%20%2F%20SQLite-145CC4" alt="Room / SQLite" />
  <img src="https://img.shields.io/badge/License-Apache--2.0-D22128" alt="Apache-2.0" />
</p>

OneLedger is a local-first Android bookkeeping app under active development. It brings quick entry, monthly budgets, an income/expense calendar, asset summaries, and spending statistics into one consistent Compose interface. Core bookkeeping works without an account or cloud service.

> [!IMPORTANT]
> The current release is a `v0.1.0` local MVP intended for development, learning, and testing. Export and backup are not production-ready yet. Uninstalling the app may remove all local ledger data, so do not use this build as the only copy of important financial records.

## Why OneLedger

- **Local first:** transactions live in the device's Room/SQLite database, and the current app declares no network permission.
- **Fast entry:** record expenses, income, transfers, categories, accounts, notes, and time in one flow.
- **Plan ahead:** browse past and future months and configure total or category budgets before a month starts.
- **Calendar at a glance:** each day shows both expenses and income; empty days show a Chinese festival or lunar date.
- **Reliable money math:** amounts are stored as `Long` minor units, while addition/subtraction expressions are evaluated with `BigDecimal`.
- **Consistent interaction:** the custom amount keypad and system IME hand off exclusively, with one visual and motion language across light and dark themes.

## Screenshots

All pages below use the light theme, and every image comes directly from the project's Compose screenshot regression suite.

### Main destinations

<table>
  <tr>
    <td align="center" width="25%">
      <img src="previews/01-ledger.png" width="205" alt="Ledger screen" /><br />
      <strong>Ledger</strong><br />
      <sub>Budget summary, daily entries, and quick add</sub>
    </td>
    <td align="center" width="25%">
      <img src="previews/02-assets.png" width="205" alt="Assets screen" /><br />
      <strong>Assets</strong><br />
      <sub>Net worth, assets, liabilities, and balances</sub>
    </td>
    <td align="center" width="25%">
      <img src="previews/03-savings.png" width="205" alt="Savings screen" /><br />
      <strong>Savings</strong><br />
      <sub>Savings methods and plan progress prototype</sub>
    </td>
    <td align="center" width="25%">
      <img src="previews/04-statistics.png" width="205" alt="Statistics screen" /><br />
      <strong>Statistics</strong><br />
      <sub>Cash-flow summaries, trends, and categories</sub>
    </td>
  </tr>
</table>

### Core bookkeeping flow

<table>
  <tr>
    <td align="center" width="33%">
      <img src="previews/05-budget-details.png" width="250" alt="Budget details screen" /><br />
      <strong>Budget track</strong><br />
      <sub>Total, spent, available, and category budgets</sub>
    </td>
    <td align="center" width="33%">
      <img src="previews/07-income-expense-calendar.png" width="250" alt="Income and expense calendar" /><br />
      <strong>Cash-flow calendar</strong><br />
      <sub>Daily income, expenses, and transaction details</sub>
    </td>
    <td align="center" width="33%">
      <img src="previews/08-quick-add.png" width="250" alt="Quick-add transaction screen" /><br />
      <strong>Quick add</strong><br />
      <sub>Expense, income, transfer, and category selection</sub>
    </td>
  </tr>
</table>

### Input and editing

<table>
  <tr>
    <td align="center" width="33%">
      <img src="previews/06-budget-editor.png" width="250" alt="Budget editor" /><br />
      <strong>Budget editor</strong><br />
      <sub>Compact amount keypad with immediate updates</sub>
    </td>
    <td align="center" width="33%">
      <img src="previews/14-quick-add-expression.png" width="250" alt="Amount expression input" /><br />
      <strong>Amount expressions</strong><br />
      <sub>Add, subtract, delete, use decimals, then evaluate explicitly</sub>
    </td>
    <td align="center" width="33%">
      <img src="previews/09-transaction-date-time.png" width="250" alt="Transaction date and time picker" /><br />
      <strong>Date and time</strong><br />
      <sub>Change the transaction date and exact time</sub>
    </td>
  </tr>
</table>

<details>
  <summary><strong>More UI states</strong></summary>
  <br />
  <table>
    <tr>
      <td align="center" width="25%">
        <img src="previews/10-calendar-selected-day.png" width="205" alt="Selected calendar day" /><br />
        <strong>Selected calendar day</strong>
      </td>
      <td align="center" width="25%">
        <img src="previews/11-calendar-future-month.png" width="205" alt="Future calendar month" /><br />
        <strong>Future calendar month</strong>
      </td>
      <td align="center" width="25%">
        <img src="previews/15-budget-future-month.png" width="205" alt="Future month budget" /><br />
        <strong>Future month budget</strong>
      </td>
      <td align="center" width="25%">
        <img src="previews/13-transaction-edit-light.png" width="205" alt="Transaction editor in light mode" /><br />
        <strong>Transaction editor</strong>
      </td>
    </tr>
  </table>
</details>

<details>
  <summary><strong>Brand icon</strong></summary>
  <br />
  <p align="center">
    <img src="previews/16-app-icon-centered.png" width="280" alt="Centered OneLedger app icon" /><br />
    <strong>App icon safe area</strong>
  </p>
</details>

## Current status

| Module | Status | Available today |
| --- | --- | --- |
| Ledger | Usable | Month navigation, budget summary, daily entries, editing, soft delete, undo, and quick add |
| Quick add | Usable | Expense, income, transfer, category, account, note, date/time, expressions, and continuous entry |
| Budgets | Usable | ¥0.00 default, total/category budgets, spent/available/overspent states, and future-month planning |
| Calendar | Usable | Six-row paging, daily cash flow, festival/lunar labels, date selection, and past/future months |
| Assets | Partial | Net worth, assets/liabilities, monthly inflow/outflow, and aggregated account balances |
| Savings | Prototype | Four method entries, plan list, and progress; transaction history and complete CRUD are pending |
| Statistics | Basic | Week/month/year/range summaries, trends, and category ranking; large-data aggregation needs work |
| Data layer | Baseline complete | Room schema v3, ledger isolation, foreign keys, budget uniqueness, and explicit migrations |

Major work still pending includes account/category management, complete search and filters, import/export, multi-ledger UI, savings transactions, encrypted backup, a full accessibility audit, and optional sync. See the [implementation roadmap](docs/IMPLEMENTATION_ROADMAP.md) for sequencing.

## Privacy and data boundaries

- No account, login, or network connection is required today.
- The Android Manifest does not declare the `INTERNET` permission.
- `android:allowBackup="false"` prevents automatic Android backup of ledger data.
- Core data stays in the app-private Room/SQLite database by default.
- Export and restore are not production-ready; clearing app data or uninstalling may cause irreversible data loss.

## Architecture

```text
Jetpack Compose UI
        ↓
Immutable UiState + ViewModel
        ↓
Repository (active-ledger boundary)
        ↓
Room DAO
        ↓
SQLite
```

| Area | Technology |
| --- | --- |
| Language | Kotlin 2.3.21, Java 17 |
| UI | Jetpack Compose, Material 3, Compose BOM 2026.06.01 |
| Data | Room 2.8.4, SQLite, schema v3 |
| Architecture | Single Activity, MVVM, Repository, unidirectional data flow |
| Platform | minSdk 23, target/compileSdk 36 |
| Testing | JVM unit tests, Room device tests, Compose Screenshot Test, Lint |

## Run locally

### Requirements

- Android Studio with AGP 9.3 support
- JDK 17
- Android SDK 36

### Clone and build

```bash
git clone https://github.com/Sirens007/OneLedger-Android.git
cd OneLedger-Android
./gradlew assembleDebug
```

On Windows PowerShell:

```powershell
.\gradlew.bat assembleDebug
```

The Debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Quality checks

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug validateDebugScreenshotTest compileDebugAndroidTestKotlin
```

When the database or DAO changes, also run the device suite on a device that allows test APK installation:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

After an approved visual change, use `updateDebugScreenshotTest` to refresh the 17 reference images. Normal commits should run `validateDebugScreenshotTest` so visual differences are not overwritten accidentally.

## Documentation

- [Project standards](docs/README.md)
- [Product and page framework](docs/PRODUCT_FRAMEWORK.md)
- [Implementation roadmap and branch map](docs/IMPLEMENTATION_ROADMAP.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Data integrity baseline](docs/DATA_INTEGRITY.md)
- [Visual design system](docs/DESIGN_SYSTEM.md)
- [Motion and content guidelines](docs/MOTION_AND_CONTENT.md)
- [Contributing guide](CONTRIBUTING.md)

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) and the [Git workflow](docs/GIT_WORKFLOW.md) before starting a feature. Each feature should use a short-lived branch and update its requirement document, tests, and screenshot baselines when needed.

## License

OneLedger is available under the [Apache License 2.0](LICENSE). Third-party components and notices are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
