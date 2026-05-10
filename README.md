# Grocery Compare

Compare grocery prices across H-E-B, Walmart, Sam's Club, Costco, Target, and Sprouts by scanning a product barcode or entering a UPC code.

## Features

- **Barcode Scanner** — CameraX + ML Kit barcode detection. Supports UPC-A, UPC-E, EAN-13, EAN-8
- **Parallel Price Lookup** — Checks all enabled stores simultaneously; results stream in as each store responds
- **Price Comparison** — Side-by-side cards showing price, stock status, and fulfillment options per store
- **Savings Highlight** — Automatically identifies the best price and shows potential savings
- **Stock Status** — In Stock, Out of Stock, Limited Stock indicators per store
- **Fulfillment Options** — Pickup, Shipping, Delivery, In-Store Only badges
- **Membership Flags** — Sam's Club and Costco results show a "Members" badge
- **Sort Options** — Price (low/high), Store Name, Availability
- **Search History** — Recent UPC lookups with product name, timestamp, and lowest price
- **Store Toggle** — Enable/disable individual stores in Settings
- **GPS Location** — Detects your city/ZIP for location-aware pricing
- **Dark Theme** — Full dark UI with per-store brand color accents

## Supported Stores

| Store | Lookup Method | Fulfillment |
|---|---|---|
| H-E-B | UPC API + HTML scrape | Pickup, Delivery |
| Walmart | UPC API + HTML scrape | Pickup, Shipping, Delivery |
| Sam's Club | UPC API + search link | Pickup, Shipping |
| Costco | UPC API + search link | Shipping, In-Store |
| Target | UPC API + HTML scrape | Pickup, Shipping, Delivery |
| Sprouts | HTML scrape | Pickup, Delivery |

## Tech Stack

| Layer | Library |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Barcode | CameraX 1.3 + ML Kit |
| Networking | OkHttp 4.12 |
| HTML Parsing | Jsoup 1.17 |
| Image Loading | Coil 2.5 |
| Product IDs | Open Food Facts API + UPC Item DB |
| Location | Google Play Services Location |
| Architecture | ViewModel + StateFlow, Compose Navigation |
| Build | Kotlin, Gradle (Kotlin DSL) |

## Requirements

- Android 8.0+ (API 26)
- Target SDK 34
- Physical device recommended for barcode scanning

## Getting Started

1. Clone the repo
2. Open the `GroceryCompare` folder in Android Studio
3. Allow Gradle to sync
4. Connect a device or start an emulator (API 26+)
5. Run the app

### Permissions

- `CAMERA` — barcode scanning
- `INTERNET` — store price lookups
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` — GPS-based location

## How Pricing Works

1. **Product identification** — UPC is resolved via Open Food Facts and UPC Item DB to get the product name and image
2. **API pricing** — UPC Item DB offer data is used for stores that appear in its response
3. **Web scraping fallback** — for stores without API pricing, the app scrapes the store's search results page using OkHttp + Jsoup with mobile browser headers and multiple CSS selectors
4. **Search link fallback** — if scraping fails, a direct search URL is returned so the user can open the store's site manually

All stores are queried in parallel via Kotlin coroutines.

## Architecture

```
com.theitguy.grocerycompare/
├── data/
│   ├── models/         # Store, StoreResult, ComparisonResult, UserLocation
│   ├── scrapers/       # Per-store scrapers + UpcLookupService + HttpClientProvider
│   ├── repository/     # PriceRepository — orchestrates parallel lookups
│   └── location/       # LocationService
├── viewmodel/          # CompareViewModel
└── ui/
    ├── theme/          # Material3 dark color scheme, typography
    ├── components/     # StoreResultCard, SearchBar, BarcodeScannerView, StoreLogo
    └── screens/        # Home, Results, Scanner, Settings, StoreWebView
```

## Notes

- Store websites change their HTML structure periodically — scrapers may need selector updates when stores redesign their frontends
- Online prices may differ from in-store prices
- Sam's Club and Costco require a valid membership for purchase

## Package

`com.theitguy.grocerycompare`
