# Grocery Compare and Save

An Android app for comparing grocery prices across multiple stores in real time. Scan a product barcode or type a UPC code, and the app queries H-E-B, Walmart, Sam's Club, Costco, Target, and Sprouts simultaneously — showing prices, stock status, and fulfillment options side by side so you always find the best deal.

No accounts required. No API keys. Pricing is sourced from free public APIs and web scraping with Jsoup.

---

## Stores Supported

| Store      | Price Source                   | Membership |
|------------|--------------------------------|------------|
| H-E-B      | Internal commerce API + scrape | No         |
| Walmart    | `__NEXT_DATA__` JSON + scrape  | No         |
| Target     | Web scrape                     | No         |
| Sprouts    | Web scrape                     | No         |
| Sam's Club | Internal API + in-app login    | Yes        |
| Costco     | Web scrape + in-app login      | Yes        |

Sam's Club and Costco require a membership. An in-app WebView lets you log in with your credentials so member pricing is visible without leaving the app.

---

## Features

- **Barcode scanner** — ML Kit barcode scanning via CameraX; supports UPC-A, UPC-E, EAN-13, EAN-8
- **UPC / text search** — enter a code manually or search by product name
- **Real-time results** — stores load asynchronously and appear as they resolve
- **Product identification** — Open Food Facts and UPC Item DB APIs resolve product name and image from UPC
- **Auto-expand search** — if no results match your filters, the radius expands automatically and retries
- **Price comparison summary** — shows maximum savings (highest vs. lowest price found)
- **Sort options** — by price (low/high), store name, or availability
- **Stock status** — in stock / limited / out of stock per store
- **Fulfillment types** — pickup, shipping, delivery, or in-store only
- **Best price highlight** — winning store card gets a green glow border
- **Open in app** — tap to launch the store's native app if installed
- **Membership WebView** — in-app login for Sam's Club and Costco with persistent cookie storage
- **Search history** — last 50 searches with product name, UPC, and lowest price found
- **Store toggle** — enable or disable individual stores in Settings
- **Dark UI** — dark-only Material 3 color scheme

---

## Tech Stack

| Component        | Library / Tool                          |
|------------------|-----------------------------------------|
| Language         | Kotlin 1.9.24                           |
| UI               | Jetpack Compose + Material 3            |
| Architecture     | ViewModel + StateFlow (MVVM)            |
| Navigation       | Compose Navigation 2.7.6                |
| Barcode scanning | ML Kit Barcode Scanning 17.2.0          |
| Camera           | CameraX 1.3.1                           |
| HTTP client      | OkHttp 4.12.0                           |
| HTML parsing     | Jsoup 1.17.2                            |
| Image loading    | Coil 2.5.0                              |
| Location         | Google Play Services Location 21.1.0    |
| Coroutines       | kotlinx-coroutines-android 1.7.3        |
| Min SDK          | 26 (Android 8.0)                        |
| Target SDK       | 34                                      |
| Build            | Gradle 8.5, AGP 8.2.2, Java 17         |

---

## How It Works

### UPC Search Flow

1. **Product identification** — Open Food Facts API is queried first for product name and image. UPC Item DB is tried as a fallback and also provides store offer data from participating merchants.
2. **Parallel store fetching** — for stores not covered by the APIs, `async` coroutines run store-specific scrapers in parallel using Jsoup with mobile user-agent headers.
3. **Result streaming** — each store's result is posted to the UI as it arrives via an `onStoreResult` callback, so you see prices populate in real time rather than waiting for all stores.
4. **Price filtering** — applied client-side using OpenStreetMap's `price_level` and `average_price` tags.
5. **Fallback** — if a scraper fails, the store card shows a "Search →" link to the store's search results page.

### Scraping Strategy (per store)

Each scraper tries multiple extraction strategies in order:

| Store      | Strategy 1                      | Strategy 2           | Strategy 3      |
|------------|---------------------------------|----------------------|-----------------|
| Walmart    | `__NEXT_DATA__` JSON blob       | Product card HTML    | No-results check|
| H-E-B      | Internal commerce API JSON      | Product card HTML    | JSON-LD schema  |
| Sam's Club | Internal Vivaldi API            | Product card HTML    | `__NEXT_DATA__` |
| Costco     | Product tile HTML               | JSON-LD schema       | Embedded script |
| Target     | CSS selectors                   | —                    | —               |
| Sprouts    | CSS selectors                   | —                    | —               |

### Text Search

When you search by name instead of UPC, the app generates direct store search URLs for each enabled store. No pricing data is fetched — each card opens the store's own search results.

---

## Building

### Requirements

- Android Studio Iguana or later
- JDK 17
- Android SDK with API 34

### Steps

```bash
git clone https://github.com/The-IT-Guy/grocery-compare.git
cd grocery-compare

# Build debug APK
./gradlew assembleDebug

# Install on connected device or emulator
./gradlew installDebug
```

The debug build requires no additional configuration. For a signed release build, create a `keystore.properties` file in the project root pointing to your keystore (see `app/build.gradle.kts` for the expected keys).

---

## Project Structure

```
app/src/main/java/com/theitguy/grocerycompare/
├── MainActivity.kt                    # Entry point, edge-to-edge, app navigation
├── data/
│   ├── models/Models.kt               # Store enum, StoreResult, ComparisonResult, data classes
│   ├── location/LocationService.kt    # Fused location + reverse geocoding
│   ├── repository/PriceRepository.kt  # Orchestrates UPC vs text search
│   └── scrapers/
│       ├── StoreScraper.kt            # Interface: lookupByUpc, buildSearchUrl
│       ├── HttpClientProvider.kt      # OkHttp singleton, mobile user-agent, timeouts
│       ├── UpcLookupService.kt        # UPC resolution + parallel scrape coordination
│       ├── HebScraper.kt              # H-E-B scraper
│       ├── WalmartScraper.kt          # Walmart scraper
│       ├── SamsClubScraper.kt         # Sam's Club scraper
│       └── CostcoScraper.kt           # Costco scraper
├── viewmodel/
│   └── CompareViewModel.kt            # UI state, search, sort, history, store toggles
└── ui/
    ├── AppNavigation.kt               # NavHost, 5 routes, location permission
    ├── theme/                         # Color.kt, Theme.kt, Type.kt (dark-only M3)
    ├── components/
    │   ├── BarcodeScannerView.kt      # CameraX + ML Kit + scan confirmation sheet
    │   ├── SearchBar.kt               # Unified search input + sort bar
    │   ├── StoreLogo.kt               # Clearbit logo + fallback letter badge
    │   └── StoreResultCard.kt         # Loading / text-search / price card variants
    └── screens/
        ├── HomeScreen.kt              # Search entry, location bar, recent history
        ├── ResultsScreen.kt           # Sorted results, savings summary
        ├── ScannerScreen.kt           # Barcode scanner wrapper
        ├── SettingsScreen.kt          # Store toggles, membership logins, about
        └── StoreWebViewScreen.kt      # In-app WebView for Sam's / Costco login
```

---

## Permissions

| Permission              | Reason                                   |
|-------------------------|------------------------------------------|
| `INTERNET`              | API calls and web scraping               |
| `CAMERA`                | Barcode scanner                          |
| `ACCESS_NETWORK_STATE`  | Network availability check               |
| `ACCESS_FINE_LOCATION`  | GPS-based location for nearby store context |
| `ACCESS_COARSE_LOCATION`| Fallback location accuracy               |

Camera is declared as `required=false` — the app works without a camera via manual UPC entry.

---

## Notes on Web Scraping

Store websites change their HTML structure frequently. If a store card shows "Search →" instead of a price, the scraper's selectors likely need an update. Open an issue or PR with the updated CSS selectors and the store name — the scraper structure is straightforward to patch.

Search history is in-memory only (held in the ViewModel, max 50 entries). It does not persist across app restarts.

---

## Roadmap

- [ ] Price history tracking with local Room database
- [ ] Persistent search history across sessions
- [ ] Push notifications for price drops on saved items
- [ ] Share comparison results
- [ ] Additional stores
- [ ] Tablet / landscape layout
