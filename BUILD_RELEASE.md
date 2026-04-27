# Building Release APK for Beta Testing

## App Features

### Membership Store Login (Sam's Club & Costco)
The app includes persistent login for membership stores:

1. Go to **Settings** → scroll to **MEMBERSHIP LOGINS**
2. Tap "**Login to Sam's Club**" or "**Login to Costco**"
3. A secure WebView opens - log in with your membership credentials
4. Your login persists via cookies - you stay logged in between app sessions
5. When searching products, tap "**Login & Search**" on membership store cards
6. The WebView opens already logged in, showing member-only prices

**How it works:**
- Cookies are stored securely on-device using Android's CookieManager
- Your credentials never leave your device or get sent to our servers
- Login sessions persist until you clear app data or log out on the store's website

### Store Price Checking
- **API pricing** (when available): Target, Walmart, H-E-B show prices from UPC Item DB
- **Manual checking**: All stores have "**Open App**" / "**Open Web**" / "**Login & Search**" buttons
- **WebView for membership stores**: Sam's Club and Costco open in-app with your saved login

---

## One-Time Setup: Create Signing Key

### Option 1: Android Studio (Recommended)
1. **Build** → **Generate Signed Bundle / APK**
2. Select **APK** → **Next**
3. Click **Create new...**
4. Fill in:
   - Key store path: `/home/jeff/Documents/GroceryCompare/grocery-compare.keystore`
   - Password: [YOUR STRONG PASSWORD]
   - Alias: `grocery-compare-key`
   - Key password: [SAME PASSWORD]
   - Validity: 25 years
   - First/Last name: Jeff Lemons

### Option 2: Command Line
```bash
cd ~/Documents/GroceryCompare
keytool -genkey -v -keystore grocery-compare.keystore \
  -alias grocery-compare-key \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Jeff Lemons, O=The IT Guy, L=New Braunfels, ST=Texas, C=US"
```

**IMPORTANT**: Save the keystore file and password somewhere safe. You need them to update the app forever.

## Create keystore.properties File

Create a file called `keystore.properties` in the project root (same level as `app/` folder):

```properties
storeFile=/home/jeff/Documents/GroceryCompare/grocery-compare.keystore
storePassword=YOUR_PASSWORD_HERE
keyAlias=grocery-compare-key
keyPassword=YOUR_PASSWORD_HERE
```

## Build the APK

### Method 1: Android Studio
1. **Build** → **Generate Signed Bundle / APK**
2. Select **APK** → **Next**
3. Select your keystore → Enter passwords
4. Select **release** build variant
5. Click **Finish**

APK will be in: `app/release/app-release.apk`

### Method 2: Command Line
```bash
cd ~/Documents/GroceryCompare
./gradlew assembleRelease
```

APK will be in: `app/build/outputs/apk/release/app-release.apk`

## Distribute to Beta Testers

### Direct Distribution
1. Upload APK to Google Drive, Dropbox, or your website
2. Share the link with testers
3. Testers must enable **Settings → Security → Install unknown apps** for their browser/file manager

### Google Play Internal Testing (Recommended)
1. Go to [Google Play Console](https://play.google.com/console)
2. Create app → Upload APK to Internal Testing track
3. Add testers by email
4. They get a link to download from Play Store (no "unknown sources" needed)

## Version Updates

Before each new build:
1. Open `app/build.gradle.kts`
2. Increment `versionCode` (e.g., 1 → 2 → 3...)
3. Update `versionName` (e.g., "1.0.0" → "1.0.1" → "1.1.0")
4. Build new APK

## File Sizes
- Debug APK: ~15-20 MB
- Release APK: ~8-12 MB (minified & optimized)
