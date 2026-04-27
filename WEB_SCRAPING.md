# Web Scraping Implementation

## Overview

The app attempts to scrape product pricing from store websites when API data isn't available. This is a **best-effort fallback** with significant limitations.

## How It Works

1. **Product Identification:** Open Food Facts + UPC Item DB APIs (fast, reliable)
2. **API Pricing:** UPC Item DB offers (when available - mostly Target)
3. **Web Scraping:** For stores without API pricing, load their search page and try to extract the first price
4. **Fallback:** If scraping fails, show "Search on Web" button for manual checking

## Limitations

### Why Scraping Often Fails

**JavaScript-Heavy Sites:**
- All stores load prices dynamically with JavaScript
- Jsoup (our HTML parser) can't execute JavaScript
- We only see the initial HTML, not the rendered page
- **Result:** Most prices won't be in the HTML we can access

**Bot Detection:**
- Stores actively block automated requests
- They fingerprint user agents, headers, IP addresses
- Aggressive rate limiting (>5 requests = blocked)
- **Result:** Frequent CAPTCHA challenges and IP bans

**Authentication Required:**
- Sam's Club and Costco require login for member prices
- WebView login cookies don't transfer to Jsoup requests
- **Result:** Membership stores can't be scraped without complex cookie handling

**HTML Changes Constantly:**
- Stores redesign their websites frequently
- CSS selectors break every few weeks
- **Result:** Scrapers need constant maintenance

### Performance Impact

- Each scrape attempt: **3-8 seconds**
- Scraping 4 stores: **12-32 seconds total**
- Battery drain from network requests
- **vs API lookup:** ~2 seconds for all stores

## Maintenance: Updating Selectors

When scraping stops finding prices (which will happen), you need to update the CSS selectors in `UpcLookupService.kt`.

### How to Find New Selectors

1. **Open the store website in Chrome**
2. **Search for any product**
3. **Right-click the price → Inspect**
4. **Find the HTML element containing the price:**
   ```html
   <span class="price-main">$6.89</span>
   ```
5. **Note the selector** (class, data attribute, or ID)
6. **Update the code:**

### Example: Updating Walmart Selectors

```kotlin
// File: UpcLookupService.kt
// Line: ~165

private fun extractWalmartPrice(doc: org.jsoup.nodes.Document): Double? {
    return try {
        val selectors = listOf(
            "span[data-automation-id='product-price']",  // Current selector
            "[data-testid='price-integer']",             // Backup
            ".price-characteristic",                      // Backup
            "YOUR_NEW_SELECTOR_HERE"                     // Add new one
        )
        // ... rest of function
    }
}
```

### Selector Examples by Store

**Walmart (changes monthly):**
- `span[data-automation-id='product-price']`
- `[data-testid='price-integer']`
- `.price-characteristic`

**Target (changes quarterly):**
- `[data-test='product-price']`
- `.h-text-bs`
- `span[class*='Price']`

**H-E-B (relatively stable):**
- `.product-price`
- `[data-testid='price']`

**Sprouts (changes infrequently):**
- `.ProductCard-sellPrice`
- `[data-test='product-price']`

## Alternative: JavaScript-Based Scraping

For better results, you could replace Jsoup with a WebView-based scraper, but this has major downsides:

**WebView Scraper Approach:**
```kotlin
// Load page in hidden WebView
// Wait for JavaScript to execute
// Extract price from rendered DOM
```

**Pros:**
- Can see JavaScript-rendered prices
- More reliable than Jsoup

**Cons:**
- 10-15 seconds per store (vs 3-8 with Jsoup)
- Heavy battery drain
- Memory intensive
- Still gets blocked by bot detection
- Complex implementation

**Recommendation:** Stick with current Jsoup approach. When it fails (which is often), users get "Search on Web" buttons. This is faster and more reliable than waiting 60+ seconds for 6 stores to scrape.

## Expected Success Rates

Based on real-world testing:

- **Target:** 40-60% (depends on API vs scraping)
- **Walmart:** 10-30% (heavy bot detection)
- **H-E-B:** 20-40% (JS-heavy site)
- **Sprouts:** 15-35% (varies by region)
- **Sam's Club:** 0% (login required)
- **Costco:** 0% (login required)

When scraping fails, users see "Search in App" or "Search on Web" buttons - they can check manually in 2-3 seconds.

## Future Improvements

1. **Cache successful selectors** - Learn which ones work
2. **Rate limiting** - Avoid getting blocked
3. **Proxy rotation** - Distribute requests
4. **Store-specific delays** - Respect rate limits
5. **User feedback** - "Did you find pricing?" → Update selectors

For now, the scraper is intentionally simple: try once, fail gracefully, let users finish the job.
