# Grocery Compare

**Compare grocery prices across H-E-B, Walmart, Sam's Club, and Costco by scanning a product barcode or entering a UPC code.**

Built by [The IT Guy](https://calltheitguy.tech) • New Braunfels, TX

---

## Features

- **Barcode Scanner** — CameraX + ML Kit barcode detection. Supports UPC-A, UPC-E, EAN-13, EAN-8.
- **Parallel Price Lookup** — Checks all enabled stores simultaneously. Results stream in as each store responds.
- **Price Comparison** — Side-by-side cards showing price, stock status, and fulfillment options per store.
- **Savings Highlight** — Automatically identifies the best price and shows potential savings.
- **Stock Status** — In Stock, Out of Stock, Limited Stock indicators per store.
- **Fulfillment Options** — Pickup, Shipping, Delivery, In-Store Only badges.
- **Membership Flags** — Sam's Club and Costco results show "Members" badge.
- **Sort Options** — Price (low/high), Store Name, Availability.
- **Search History** — Recent UPC lookups with quick re-search.
- **Store Toggle** — Enable/disable individual stores in Settings.
- **Dark Theme** — Full dark UI with store brand color accents.

## Architecture

```
com.theitguy.grocerycompare/
├── data/
│   ├── models/       # Store, StoreResult, ComparisonResult, etc.
│   ├── scrapers/     # Per-store web scrapers (Walmart, HEB, Sam's, Costco)
│   └── repository/   # PriceRepository - orchestrates parallel lookups
├── viewmodel/        # CompareViewModel - MVVM state management
└── ui/
    ├── theme/        # Material3 dark color scheme, typography
    ├── components/   # StoreResultCard, SearchBar, BarcodeScannerView
    └── screens/      # Home, Results, Scanner, Settings
```

**Stack:** Kotlin, Jetpack Compose, Material3, CameraX, ML Kit, OkHttp, Jsoup, Coil, Coroutines

## Setup

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34
- Physical device recommended (camera for barcode scanning)

### Steps
1. Open the `GroceryCompare/` folder in Android Studio
2. Wait for Gradle sync to complete
3. Connect a device or start an emulator (API 26+)
4. Run the app

### Permissions
- **CAMERA** — Barcode scanning
- **INTERNET** — Store website lookups

## How the Scrapers Work

Each store has a dedicated scraper class that:

1. **Builds a search URL** using the UPC code
2. **Fetches the page** via OkHttp with mobile browser headers
3. **Parses the response** using multiple strategies:
   - Embedded JSON (`__NEXT_DATA__`, JSON-LD, internal APIs)
   - HTML parsing with Jsoup (product cards, price elements)
   - Fallback regex extraction for price data in scripts
4. **Returns a StoreResult** with price, stock, fulfillment, image, and product URL

All four stores are queried in parallel using Kotlin coroutines. Results appear as each store responds.

## Important Notes

- **Web scraping disclaimer:** Store websites change their HTML structure periodically. Scrapers may need updates when stores update their frontends. The multi-strategy approach (API → JSON → HTML → regex) provides resilience.
- **Pricing accuracy:** Online prices may differ from in-store prices. Prices shown are from each store's website at time of lookup.
- **Membership stores:** Sam's Club and Costco require valid membership for purchase. The app flags these results.
- **Location-based results:** Some stores show different pricing by location. Results reflect the default/national pricing unless the store detects your location.

## Extending

### Adding a New Store

1. Create a new scraper in `data/scrapers/` implementing `StoreScraper`
2. Add the store to the `Store` enum in `Models.kt`
3. Register it in `PriceRepository.scrapers`

### Switching to Official APIs

If a store provides an official API key, replace the HTML scraping in that store's scraper with proper API calls. The `StoreResult` model stays the same either way.

## License

Private / Internal Use — The IT Guy
