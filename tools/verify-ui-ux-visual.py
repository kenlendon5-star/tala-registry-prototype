"""
Follow-up visual verification for the 20 Oct 2026 native UI/UX review.

Drives the running API 35 emulator (emulator-5554) against the ISOLATED build
(ph.tala.registry.d08test) and captures screenshots plus the accessible tree for the
rendered issues UX-03, UX-04, UX-05, UX-07 and UX-09. Never run concurrently with
instrumentation. Font scale and rotation are restored at the end.

Usage (repo root): rtk proxy python tools/verify-ui-ux-visual.py --evidence-directory planning/03-evidence/NEW-DIRECTORY
"""
import argparse, datetime, json, pathlib, re, struct, subprocess, sys, time, xml.etree.ElementTree as ET

sys.stdout.reconfigure(encoding="utf-8")
ROOT = pathlib.Path(__file__).resolve().parents[1]
PKG = "ph.tala.registry.d08test"
ACTIVITY = f"{PKG}/ph.tala.registry.MainActivity"
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--device", default="emulator-5554")
parser.add_argument("--evidence-directory", default="planning/03-evidence/ui-ux-final-" + datetime.datetime.now().strftime("%Y-%m-%d-%H%M%S"))
args = parser.parse_args()
if not re.fullmatch(r"emulator-\d+", args.device):
    parser.error("Use an emulator, not the normal target phone.")
OUT = ROOT / args.evidence_directory
OUT.mkdir(parents=True, exist_ok=False)
CUR = OUT / "current.xml"
EXPECTED_ROTATION = None


def adb(*args):
    p = subprocess.run(["rtk", "proxy", "adb", "-s", globals()["args"].device, *args], capture_output=True, text=True,
                       encoding="utf-8", errors="replace")
    if p.returncode:
        raise RuntimeError(f"adb {' '.join(args)} -> {p.stderr or p.stdout}")
    return p.stdout


def dump(path):
    for _ in range(6):
        try:
            adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
            adb("shell", "rm", "-f", "/sdcard/tala-ui-ux.xml")
            adb("shell", "uiautomator", "dump", "/sdcard/tala-ui-ux.xml")
            adb("pull", "/sdcard/tala-ui-ux.xml", str(path))
            return ET.parse(path).getroot()
        except Exception:
            time.sleep(0.6)
    raise RuntimeError("uiautomator dump failed repeatedly")


def nodes(root, text=None, desc=None, contains=False):
    attr = "content-desc" if desc is not None else "text"
    want = desc if desc is not None else text
    out = []
    for n in root.iter("node"):
        val = n.get(attr, "")
        if (want in val) if contains else (val == want):
            out.append(n)
    return out


def _bounds(n):
    return list(map(int, re.findall(r"\d+", n.get("bounds", "0,0,0,0"))))


def _tap_node(n):
    x1, y1, x2, y2 = _bounds(n)
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(0.9)


def scroll_down():
    w, h = viewport()
    root = dump(CUR)
    regions = [_bounds(n) for n in root.iter("node") if n.get("scrollable") == "true"]
    regions = [b for b in regions if b[2]-b[0] >= w*.5 and b[3]-b[1] > 80]
    x1, y1, x2, y2 = max(regions, key=lambda b: (b[2]-b[0])*(b[3]-b[1])) if regions else (0, 0, w, h)
    x = (x1+x2)//2
    adb("shell", "input", "swipe", str(x), str(int(y1+(y2-y1)*.8)), str(x), str(int(y1+(y2-y1)*.25)), "350")
    time.sleep(0.7)


def viewport():
    adb("shell", "screencap", "-p", "/sdcard/tala-viewport.png")
    adb("pull", "/sdcard/tala-viewport.png", str(OUT / "viewport.png"))
    return struct.unpack(">II", (OUT / "viewport.png").read_bytes()[16:24])


def wait_tap(text=None, desc=None, contains=False, index=None, timeout=30.0, tag="", scrolls=4):
    deadline = time.time() + timeout
    last = 0
    done = 0
    while time.time() < deadline:
        root = dump(CUR)
        found = nodes(root, text=text, desc=desc, contains=contains)
        last = len(found)
        if found:
            if index is not None and -len(found) <= index < len(found):
                _tap_node(found[index]); return
            if index is None and len(found) == 1:
                _tap_node(found[0]); return
        if done < scrolls:
            scroll_down(); done += 1
        time.sleep(0.6)
    raise RuntimeError(f"tap {tag or text or desc!r}: found {last}")


def capture(name):
    path = OUT / f"{name}.xml"
    root = dump(path)
    adb("shell", "screencap", "-p", "/sdcard/tala-ui-ux.png")
    adb("pull", "/sdcard/tala-ui-ux.png", str(OUT / f"{name}.png"))
    w, h = struct.unpack(">II", (OUT / f"{name}.png").read_bytes()[16:24])
    if EXPECTED_ROTATION is not None:
        assert (w > h) == (EXPECTED_ROTATION % 2 == 1), f"{name}: actual {w}x{h} does not match requested rotation {EXPECTED_ROTATION}"
    lines = []
    for n in root.iter("node"):
        if n.get("text") or n.get("content-desc"):
            lines.append(f"{n.get('class','')}\ttext={n.get('text','')!r}\tdesc={n.get('content-desc','')!r}\tselected={n.get('selected')}\tbounds={n.get('bounds')}")
    (OUT / f"{name}.txt").write_text("\n".join(lines), encoding="utf-8")
    print(f"captured {name} ({len(lines)} labelled nodes)")


def force_launch():
    adb("shell", "am", "force-stop", PKG)
    time.sleep(0.5)
    adb("shell", "am", "start", "-n", ACTIVITY, "-f", "0x10008000")
    time.sleep(3.0)
    if EXPECTED_ROTATION is not None:
        set_rotation(EXPECTED_ROTATION)


def set_font(scale):
    adb("shell", "settings", "put", "system", "font_scale", str(scale))
    time.sleep(2)


def set_rotation(deg):
    global EXPECTED_ROTATION
    EXPECTED_ROTATION = deg
    adb("shell", "settings", "put", "system", "accelerometer_rotation", "0")
    adb("shell", "settings", "put", "system", "user_rotation", str(deg))
    adb("shell", "cmd", "window", "user-rotation", "lock", str(deg))
    for _ in range(20):
        w, h = viewport()
        if (w > h) == (deg % 2 == 1):
            return
        time.sleep(.25)
    raise AssertionError("Display did not rotate to the requested orientation")


def open_workspace(code):
    wait_tap(text="Browse households", tag="browse-households", scrolls=12, timeout=90)
    wait_tap(text=code, tag=f"household {code}", contains=True, scrolls=6)
    wait_tap(text="Continue questionnaire", tag="continue-questionnaire", scrolls=12, timeout=90)


def go_section(title):
    wait_tap(desc="Open section directory", tag="directory-button", scrolls=1)
    time.sleep(0.7)
    deadline = time.time() + 90
    done = 0
    while time.time() < deadline:
        root = dump(CUR)
        found = nodes(root, text=title)
        if found:
            _tap_node(found[-1])  # modal sheet is the topmost match
            time.sleep(1.0)
            return
        if done < 12:
            scroll_down(); done += 1
        time.sleep(0.5)
    raise RuntimeError(f"section {title} not in directory")


def environment():
    keys = [("system", "font_scale"), ("system", "accelerometer_rotation"),
            ("system", "user_rotation"), ("secure", "show_ime_with_hard_keyboard"),
            ("global", "stay_on_while_plugged_in"), ("system", "screen_off_timeout")]
    return [dict(namespace=n, key=k, value=adb("shell", "settings", "get", n, k).strip()) for n, k in keys]


def first_name():
    # Scroll the form into view; the root's current bounds, not wm's unrotated size,
    # determine swipe coordinates. Never silently capture a missing field.
    for _ in range(20):
        root = dump(CUR)
        for n in root.iter("node"):
            if n.get("class") == "android.widget.EditText" and any(c.get("text") == "First Name" for c in n.iter("node")):
                x1, y1, x2, y2 = _bounds(n)
                if y2 - y1 >= 80:
                    return n
        scroll_down()
    raise AssertionError("First Name field is not reachable")


def keyboard_shown():
    return "mInputShown=true" in adb("shell", "dumpsys", "input_method")


def main():
    original = environment()
    original_display = {key: adb("shell", "wm", key) for key in ("size", "density")}
    original_ac = "AC powered: true" in adb("shell", "dumpsys", "battery")
    (OUT / "power-before.json").write_text(json.dumps({"ac": original_ac}), encoding="utf-8")
    (OUT / "environment-before.json").write_text(json.dumps(original, indent=2), encoding="utf-8")
    (OUT / "display-before.json").write_text(json.dumps(original_display, indent=2), encoding="utf-8")
    results = []
    try:
        adb("emu", "power", "ac", "on")
        adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
        adb("shell", "wm", "dismiss-keyguard")
        adb("shell", "settings", "put", "global", "stay_on_while_plugged_in", "3")
        adb("shell", "settings", "put", "system", "screen_off_timeout", "1800000")
        adb("shell", "settings", "put", "secure", "show_ime_with_hard_keyboard", "1")
        set_rotation(0)
        # UX-09: Home metric row at 200% (adaptive columns keep whole labels).
        set_font(2.0); force_launch(); time.sleep(1.5)
        # Home's metric grid is below the hero at large text.
        wait_tap(text="Browse all", scrolls=12, timeout=90)
        # Return to the same scroll position; this action also verifies the count link.
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        capture("ux09-home-200")

        # Baseline Home at 100%.
        set_font(1.0); force_launch(); time.sleep(1.5)
        capture("home-100")

        # UX-04: canonical numbering — workspace directory vs Sections tab (both show 05).
        force_launch()
        open_workspace("HH-00452")
        wait_tap(desc="Open section directory", tag="directory-button", scrolls=1); time.sleep(0.8)
        capture("ux04-directory")
        adb("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(0.8)  # dismiss sheet only
        wait_tap(text="Save & back", tag="save-back", scrolls=2); time.sleep(1.0)
        wait_tap(text="View interview sections", tag="sections-link", scrolls=2); time.sleep(1.0)
        capture("ux04-sections-tab")

        # UX-05: member-scoped empty state on a household with no members.
        force_launch()
        open_workspace("HH-00453")
        go_section("Demographics")
        capture("ux05-empty-member")

        # UX-07: signature accessible-alternative label (choose consent Yes so the pad renders).
        force_launch()
        open_workspace("HH-00452")
        go_section("Consent & review")
        wait_tap(text="Yes", tag="consent-yes", scrolls=1); time.sleep(1.0)
        capture("ux07-consent-signature")

        # Navigate at the baseline, then keep the same member/section during resizing.
        set_font(1.0); set_rotation(0); force_launch()
        open_workspace("HH-00452"); go_section("Demographics")
        for scale in (1.0, 2.0):
            set_font(scale)
            for rotation in (0, 1):
                set_rotation(rotation)
                name = f"ux03-{'landscape' if rotation else 'portrait'}-{int(scale * 100)}"
                field = first_name()
                original_value = field.get("text", "")
                capture(name + "-field")
                _tap_node(field)
                deadline = time.time() + 15
                while not keyboard_shown() and time.time() < deadline:
                    time.sleep(.3)
                assert keyboard_shown(), "The IME did not open; screenshot is not keyboard evidence"
                if not original_value:
                    original_value = "UXSample"
                    adb("shell", "input", "text", original_value)
                root = dump(CUR)
                focused = [n for n in root.iter("node") if n.get("class") == "android.widget.EditText" and n.get("focused") == "true"]
                assert focused and _bounds(focused[0])[3] - _bounds(focused[0])[1] >= 80, "Focused input disappeared above IME"
                assert nodes(root, text="First Name"), "Focused field label is missing"
                capture(name + "-keyboard")
                wait_tap(text="Save draft", tag="save-with-keyboard", scrolls=40, timeout=240)
                assert keyboard_shown(), "Keyboard was dismissed before the save path was checked"
                capture(name + "-keyboard-save")
                # Dismissal must recover the same answer and allow navigation/save.
                adb("shell", "input", "keyevent", "KEYCODE_BACK")
                time.sleep(.5)
                capture(name + "-save")
                results.append(dict(scale=scale, rotation=rotation, viewport=viewport(), keyboard=True, saved=True))
                # A short form viewport may be at its footer; reset position without losing scope.
                set_rotation(0); set_font(1.0)
                force_launch(); open_workspace("HH-00452"); go_section("Demographics")
                assert first_name().get("text", "") == original_value, "Answer changed during IME/rotation/save"
                set_font(scale)
        (OUT / "visual-results.json").write_text(json.dumps(results, indent=2), encoding="utf-8")
        # Wide sidebar: its member list has its own bounded scroll region.
        set_font(1.0); set_rotation(0)
        adb("shell", "wm", "size", "1600x2560")
        adb("shell", "wm", "density", "240")
        force_launch(); open_workspace("HH-00452")
        # Wide layout has no directory button: select directly in the index.
        wait_tap(text="Demographics", scrolls=0)
        root = dump(CUR)
        assert nodes(root, text="Member scope"), "Wide member switcher was not surfaced"
        capture("ux02-10-wide-member-index")
        # Reach the last fictional seed member within the member list, not by scrolling
        # through the section index. Assert scope selection after the switch.
        scope = nodes(root, text="Member scope")[0]
        sx1, sy1, sx2, sy2 = _bounds(scope)
        adb("shell", "input", "swipe", str((sx1+sx2)//2), str(sy1+300), str((sx1+sx2)//2), str(sy2+10), "350")
        wait_tap(text="Pedro Santos", scrolls=0)
        root = dump(CUR)
        assert any(n.get("selected") == "true" and nodes(n, text="Pedro Santos") for n in root.iter("node")), "Last member selection was not retained"
        capture("ux02-10-wide-last-member")
    finally:
        adb("shell", "am", "force-stop", PKG)
        adb("emu", "power", "ac", "on" if original_ac else "off")
        for key, value in original_display.items():
            override = re.search(r"Override (?:size|density): (\S+)", value)
            adb("shell", "wm", key, override.group(1) if override else "reset")
        for s in original:
            if s["value"] == "null":
                adb("shell", "settings", "delete", s["namespace"], s["key"])
            else:
                adb("shell", "settings", "put", s["namespace"], s["key"], s["value"])
        after = environment()
        (OUT / "environment-after.json").write_text(json.dumps(after, indent=2), encoding="utf-8")
        display_after = {key: adb("shell", "wm", key) for key in ("size", "density")}
        (OUT / "display-after.json").write_text(json.dumps(display_after, indent=2), encoding="utf-8")
        assert original == after, "Emulator settings were not restored exactly"
        assert original_display == display_after, "Display overrides were not restored exactly"
        after_ac = "AC powered: true" in adb("shell", "dumpsys", "battery")
        (OUT / "power-after.json").write_text(json.dumps({"ac": after_ac}), encoding="utf-8")
        assert original_ac == after_ac, "Virtual AC power was not restored"
    print(f"evidence written to {OUT}")


if __name__ == "__main__":
    main()


