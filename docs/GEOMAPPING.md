# Household removal and geomapping

## What professionals do, and what Tala implements

Research reviewed September 23, 2026. These are documented field-collection patterns, not claims that this prototype has the same production capabilities as those products.

| Reference | Pattern | Applied here |
| --- | --- | --- |
| [ODK location questions](https://docs.getodk.org/form-question-types/) | Distinguish captured geopoints from locations selected on a map; capture location metadata | Store latitude, longitude, capture method and device-reported accuracy |
| [ArcGIS Field Maps configuration](https://doc.arcgis.com/en/field-maps/latest/prepare-maps/configure-the-map.htm) | Make GPS accuracy visible during field collection | Show an accuracy radius and a warning above 50 m; this is a prototype UX threshold, not an official surveying standard |
| [Google draggable markers](https://developers.google.com/maps/documentation/javascript/advanced-markers/draggable-markers) | Allow an operator to correct a point interactively | Tap the map or drag the pin; manual edits clear the old GPS accuracy claim |
| [Google Maps URLs](https://developers.google.com/maps/documentation/urls/get-started) | Cross-platform search and directions without an API key | Open a saved coordinate pair or request directions from the app |
| [Google Maps Embed setup](https://developers.google.com/maps/documentation/embed/quickstart) | An embedded Google map requires an enabled project and API key | Do not simulate a configured Google map; use an openly attributed map surface with Google navigation links |

A professional registry should separate an address label from an independently verified coordinate. Searching an address may point to a road or district rather than the actual dwelling entrance. Tala therefore never silently converts the sample address into a claimed household location.

## Location workflow

1. Open a household and select **Tag household location**.
2. Capture device location while at the dwelling, tap the map, drag the marker, or enter decimal coordinates.
3. Check the displayed location and accuracy. Optional notes describe the entrance or nearby landmark.
4. Select the confirmation checkbox and **Save household location**.
5. Use **Map** in the main navigation to locate saved households. A marker opens its household record.
6. Use **Open saved pin in Google Maps** or **Directions to saved pin**. Only the saved coordinates go into these URLs, not resident names or survey answers.

Address search is also available as an explicit link to Google Maps. It does not automatically return a coordinate to this prototype. The user can use device capture, place the pin, or copy the location's latitude/longitude into the form. A future Google Places integration could return selected search results directly after the project is configured.

Unsaved pin edits are held separately from the saved household record. They survive navigation within the page but are discarded on page reload. **Discard pin changes** restores the saved location. **Remove saved location** removes only the pin and location notes, after confirmation. Changing the address displays a prompt to check the existing pin again.

## Provider and Google Maps boundary

The in-app interactive map uses locally bundled **Leaflet 1.9.4** and online **OpenStreetMap tiles**. Google Maps is used for address search, viewing saved coordinates, and directions. It does not require a Google API key for these links. The app does not add a public place to Google Maps and does not embed Google map imagery.

Tiles require connectivity. There is no offline tile download or bulk prefetch. Attribution remains visible and tile requests use the normal browser referrer, in line with the [OpenStreetMap tile usage policy](https://operations.osmfoundation.org/policies/tiles/). For a production deployment, choose a tile service and usage agreement appropriate to the expected traffic. Household labels are rendered locally; the tile service receives ordinary map-tile requests, not those labels. Google receives the chosen address or coordinates when a Google link is opened.

A Google-branded embedded map can be added using the Maps JavaScript API with a properly restricted browser API key, an enabled Google project and its required billing setup. Add Places search if automatic address lookup is needed. Do not scrape Google Maps or treat a camera-center URL as a verified dwelling coordinate.

## Data shape

Each household can have a `location` object:

```json
{
  "lat": 14.5995,
  "lng": 120.9842,
  "source": "device",
  "accuracy": 12,
  "capturedAt": "ISO timestamp",
  "verifiedAt": "ISO timestamp",
  "notes": "Fictional entrance note",
  "addressAtCapture": "Address when pin was saved"
}
```

The example coordinates are for testing only; no sample household is automatically assigned them. `accuracy` is in meters and is null after a manual adjustment. `verifiedAt` means an operator confirmed the pin, not that a surveying authority certified it. Coordinates, source, notes and timestamps are included in the existing household JSON exports.

Browser geolocation may use more than GPS. Its accuracy value is an estimate, not a guarantee. Permission is requested only when the operator presses the capture button. Denial, unavailable position and timeout show usable next steps. Delayed callbacks are ignored after switching records or leaving the screen. In production, browser device location requires HTTPS (localhost is suitable for local development).

## Remove household

The household overview has a **Remove household** action. Its confirmation names the record and explains that household answers, member records and the saved location will be removed from this browser.

The latest deletion can be undone from the toast or the home-screen undo action until the page is refreshed. A second deletion replaces the previous undo record. Removed records are excluded from search, reports, maps and exports. Deleting the last household is supported; it leaves an empty registry from which the user can register another household. A persistent serial-number high-water mark prevents reusing deleted IDs.

A production system should replace client-only deletion with authorized server-side removal or archival according to the barangay's retention policy, with an audit record. No backend or production access control is implied by this prototype action.

## Verification

`node verify.cjs` passes the existing form, review, signature and scroll regression suite.

`node verify-map.cjs` passes deletion cancel/confirm/undo, empty-registry reload/navigation, new ID allocation, coordinate validation, simulated device-location capture, accuracy retention, confirmation requirement, persistence, exact Google URLs, map click/drag, marker navigation, low-accuracy display, permission denial, delayed-callback isolation, discarded drafts, pin removal, and mobile layout checks. Browser geolocation was simulated; no actual user location was captured during testing.

A 390px mobile screenshot was visually checked with real map tiles loaded. Google URL parameters were checked; external turn-by-turn navigation and a native-device field pilot remain outside these local automated tests.

## Cogon scope and startup update

The prototype now opens on Home on every full page load/reload, even if the URL previously contained another screen fragment. Saved household answers and selected-record context are preserved. In-app navigation still works normally.

The registry is scoped to **Barangay Cogon, Ormoc City, Leyte, Philippines**. The map defaults to 11.0176, 124.6031, based on the [Cogon Combado locality reference](https://www.philatlas.com/visayas/r08/leyte/ormoc/cogon-combado.html). The city also uses Barangay Cogon in its [citizens charter addresses](https://www.ormoc.gov.ph/assets/docs/citizens_charter.pdf).

The prototype uses a configurable rectangular working extent: southwest 11.0116, 124.5971; northeast 11.0236, 124.6091, with zoom levels 16–19. **This is an approximate local working area, not a verified administrative boundary.** Replace it with an official barangay polygon before using the app to determine jurisdiction. The UI states this limitation.

New pins outside the configured extent are rejected, including out-of-area device captures. Existing out-of-area saved coordinates are retained and flagged for correction, and do not pull the registry map away from Cogon. Google address searches include the complete locality. Profile and setup barangay fields are fixed to Cogon.

Both browser regression suites pass after the update, including Home on reload, saved-answer persistence, map center/minimum zoom, and rejection of coordinates outside the configured area. Map animations were disabled to prevent callbacks accessing a removed map during navigation.
