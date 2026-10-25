"""Walks the main screens on a running emulator and saves screenshots (used by the tablet-shots workflow)."""
import os, re, subprocess, sys, time, xml.etree.ElementTree as ET

OUT = sys.argv[1]
TAG = sys.argv[2]
os.makedirs(OUT, exist_ok=True)


def adb(*args, check=False):
    return subprocess.run(["adb", *args], capture_output=True, check=check).stdout


def shot(name):
    time.sleep(1.5)
    with open(f"{OUT}/{TAG}-{name}.png", "wb") as f:
        f.write(adb("exec-out", "screencap", "-p"))
    print("shot", name)


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    raw = adb("exec-out", "cat", "/sdcard/ui.xml").decode("utf-8", "ignore")
    try:
        return list(ET.fromstring(raw).iter("node"))
    except ET.ParseError:
        return []


def tap(text, exact=False):
    for _ in range(3):
        for n in nodes():
            t = (n.get("text") or "") + "|" + (n.get("content-desc") or "")
            if (text in t.split("|")) if exact else (text in t):
                x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
                adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
                time.sleep(1.5)
                return True
        time.sleep(1.5)
    print("not found:", text)
    return False


def back():
    adb("shell", "input", "keyevent", "4")
    time.sleep(1.2)


def scroll():
    size = re.findall(r"(\d+)x(\d+)", adb("shell", "wm", "size").decode())[-1]
    w, h = int(size[0]), int(size[1])
    adb("shell", "input", "swipe", str(w // 2), str(h * 3 // 4), str(w // 2), str(h // 4), "400")
    time.sleep(1)


def home():
    tap("홈", exact=True)


adb("shell", "am", "start", "-n", "com.wordbook.app/.MainActivity")
time.sleep(8)
shot("01-onboarding")
tap("다음", exact=True)
shot("02-onboarding2")
tap("건너뛰기")
time.sleep(4)
shot("03-home")
scroll(); shot("04-home-scrolled")
scroll(); shot("05-home-bottom")
adb("shell", "am", "start", "-n", "com.wordbook.app/.MainActivity")
time.sleep(2)
if tap("Lv."):
    shot("06-level-dialog"); back()
if tap("오늘의 낚시"):
    shot("07-fishing-guide"); back(); shot("08-fishing")
    if tap("도감"):
        shot("09-fishbook"); back()
    back()
if tap("대양 원정"):
    shot("10-voyage-guide"); back(); shot("11-voyage"); scroll(); shot("12-voyage-scrolled"); back()
if tap("오늘 학습 시작") or tap("새 단어 더 배우기"):
    shot("13-study"); adb("shell", "input", "tap", "500", "900"); shot("14-study-flip")
    back(); shot("15-study-exit"); back()
home()
tap("복습·통계"); shot("16-review"); scroll(); shot("17-review-scrolled")
tap("설정", exact=True); shot("18-settings"); scroll(); shot("19-settings-scrolled"); scroll(); shot("20-settings-bottom")
