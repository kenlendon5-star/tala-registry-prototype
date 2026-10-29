# Map long-press household pinning

**Status:** Implemented in `android-native/` on 28 October 2026. The JVM behavior suite and the isolated physical-device interaction test pass. On 29 October 2026, `longPressPinsAnUntaggedHouseholdFromTheMap` passed on the OPPO CPH2529 / Android 15.

On 29 October 2026, the map viewport was also explicitly clipped at both the Compose container and osmdroid `AndroidView` boundaries. This prevents transient map frames from drawing over the status text or household list while panning. A live mid-drag screenshot on the normal app and `mapViewportRemainsBoundedAfterPan` verify the correction.

## User flow

1. On the Map screen, long-press a point inside the approximate Cogon working extent.
2. The proposed point is drawn as a highlighted pin and a **Pin a household** button appears over the map. No data is written yet.
3. Press **Pin a household** to open a bottom-sheet picker containing active, unarchived households that do not have coordinates.
4. Select a household. The app saves the staged coordinate through `HouseholdRepository.saveCoordinate` with `CoordinateSource.Manual`, no accuracy value, and the current capture time.
5. After a successful Room write, the household leaves the unpinned list and appears in the saved-pin list. A failed write keeps the point and reopens the picker so the enumerator can retry.

The action is available on both the online osmdroid surface and the offline extent plot. Long presses outside the configured working extent are rejected with `MapRules.OUTSIDE_MESSAGE`. Canceling from either the map action or picker discards the staged point without writing.

## Implementation ownership

| Concern | Owner |
|---|---|
| Gesture detection, proposed-pin rendering, action button, bottom-sheet list | `ui/map/MapScreen.kt` |
| Staged point, picker visibility, validation, save/retry/cancel events | `ui/map/MapViewModel.kt` |
| Screen event wiring | `ui/TalaApp.kt` |
| Durable coordinate write and archived-household refusal | `data/HouseholdRepository` implementation |
| JVM state/persistence regression | `ui/map/MapViewModelTest.kt` |
| Android gesture-to-save regression | `ui/MapDeviceTest.kt` |

`MapUiState.pendingPinLocation` means a valid point is staged. `showPinHouseholdPicker` is deliberately separate: a long press sets only the staged point, while `showPinHouseholdPicker()` is called by the visible button. Keep this two-step contract; opening the picker directly from the long press does not match the product interaction.

## Stable UI test tags

- `map-surface`: gesture target.
- `pin-household-action`: the **Pin a household** confirmation button.
- `cancel-pending-pin`: cancels the staged point.
- `pin-dropdown-title` and `pin-dropdown-location`: picker heading and staged coordinates.
- `pin-dropdown-HH-xxxxx`: one unpinned household row, keyed by display code.
- `pin-dropdown-close`: closes and cancels the flow.

## Invariants for future changes

- Never offer archived households or households that already have coordinates.
- Re-check that the selected household is still unpinned before saving; the list can change while the picker is open.
- Never report success until the repository returns `WriteResult.Saved`.
- Preserve the staged point after `WriteResult.Failed` so retry does not require another long press.
- Do not attach household names, answers, or other record data to map tile or external-map requests.
- Existing pin adjustment remains a separate flow and must cancel any staged new pin.
- Keep the map container's rounded `clip` and the osmdroid `AndroidView`'s `clipToBounds`; removing either can let native pan frames overdraw adjacent Compose content on physical devices.

## Verification

From `android-native/`:

```powershell
rtk proxy .\gradlew.bat :app:testDebugUnitTest --tests ph.tala.registry.ui.map.MapViewModelTest
rtk proxy .\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ph.tala.registry.ui.MapDeviceTest
```

The JVM suite covers staging before picker display, successful Manual persistence, failed-write retry, and outside-extent rejection. The device test exercises the complete long-press → button → household selection → saved pin interaction. Because the map is an osmdroid `AndroidView`, the device test injects a native stationary swipe at the map node's coordinates; Compose's synthetic long-click does not cross the `AndroidView` boundary reliably on this OPPO.
