"""Two "devices" on one emulator: device A's data is saved and put back around device B, to check the family sync."""
import os, re, subprocess, sys, time, xml.etree.ElementTree as ET

OUT = sys.argv[1]
APP = "com.wordbook.app"
os.makedirs(OUT, exist_ok=True)
log = open(f"{OUT}/sync-check.txt", "w")


def say(*a):
    line = " ".join(str(x) for x in a)
    print(line, flush=True)
    log.write(line + "\n"); log.flush()


def adb(*args, stdin=None):
    return subprocess.run(["adb", *args], capture_output=True, input=stdin).stdout


def shot(name):
    time.sleep(1.5)
    with open(f"{OUT}/sync-{name}.png", "wb") as f:
        f.write(adb("exec-out", "screencap", "-p"))


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    raw = adb("exec-out", "cat", "/sdcard/ui.xml").decode("utf-8", "ignore")
    try:
        return list(ET.fromstring(raw).iter("node"))
    except ET.ParseError:
        return []


def tap(text, tries=7):
    for k in range(tries):
        for n in nodes():
            t = (n.get("text") or "") + "|" + (n.get("content-desc") or "")
            if text in t:
                x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
                adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
                time.sleep(1.5)
                return True
        # Maybe off screen: look further down first, then back up.
        if k < 3:
            adb("shell", "input", "swipe", "540", "1500", "540", "600", "600")
        else:
            adb("shell", "input", "swipe", "540", "600", "540", "1500", "600")
        time.sleep(2)
    seen = [n.get("text") for n in nodes() if n.get("text")]
    say("  (not found:", text + ") seen:", " / ".join(seen)[:400])
    shot("missing-" + re.sub(r"\W", "", text)[:12])
    return False


def typ(text):
    adb("shell", "input", "text", text)
    time.sleep(0.8)
    adb("shell", "input", "keyevent", "111")  # escape: hide the keyboard
    time.sleep(0.8)


def pref(file, key):
    raw = adb("exec-out", "run-as", APP, "cat", f"shared_prefs/{file}.xml").decode("utf-8", "ignore")
    m = re.search(r'name="%s"(?: value="([^"]*)")?>?([^<]*)' % re.escape(key), raw)
    if not m:
        return None
    return m.group(1) if m.group(1) is not None else m.group(2)


def start():
    adb("shell", "am", "force-stop", APP)
    adb("shell", "am", "start", "-n", f"{APP}/.MainActivity")
    time.sleep(8)


def fresh_device():
    adb("shell", "pm", "clear", APP)
    start()
    for _ in range(5):
        texts = [n.get("text") for n in nodes()]
        if "복습·통계" in texts:
            break
        tap("건너뛰기", tries=1)
        time.sleep(3)


def save_device(name):
    adb("shell", "am", "force-stop", APP)
    data = adb("exec-out", "run-as", APP, "tar", "-cf", "-", "shared_prefs", "databases", "files")
    open(f"/tmp/{name}.tar", "wb").write(data)
    say(f"  saved device {name}: {len(data)} bytes")


def load_device(name):
    adb("shell", "pm", "clear", APP)
    adb("shell", "run-as", APP, "tar", "-xf", "-", stdin=open(f"/tmp/{name}.tar", "rb").read())


def check(label, ok):
    say(("PASS " if ok else "FAIL ") + label)


say("== Device A: new family, Telegram settings, some study ==")
fresh_device()
tap("설정")
tap("기기 동기화")
tap("새 가족 코드 만들기")
time.sleep(8)
code = pref("sync", "code")
say("  family code:", code)
check("A made a family code", bool(code))
tap("텔레그램 알림")
tap("봇 토큰")
typ("TESTTOKEN123")
tap("받는 사람 1")
typ("111")
tap("설정 저장")
time.sleep(10)
shot("A-settings")
check("A saved the Telegram token", pref("study", "tgToken") == "TESTTOKEN123")
tap("홈")
if tap("오늘 학습 시작"):
    time.sleep(3)
    for _ in range(4):
        adb("shell", "input", "swipe", "540", "1300", "540", "300", "150")
        time.sleep(1.5)
    shot("A-study")
    adb("shell", "input", "keyevent", "4"); time.sleep(1)
    adb("shell", "input", "keyevent", "4"); time.sleep(1)
adb("shell", "input", "keyevent", "3")  # home key: the app syncs when it goes to the background
time.sleep(12)
save_device("A")

say("== Device B: joins with the code ==")
fresh_device()
tap("설정")
tap("기기 동기화")
tap("다른 기기에 나온 가족 코드")
typ(code or "")
tap("이 코드로 연결")
time.sleep(15)
shot("B-joined")
check("B got A's Telegram token", pref("study", "tgToken") == "TESTTOKEN123")
check("B got A's Telegram chat id", pref("study", "tgChat") == "111")
tap("홈")
time.sleep(2)
shot("B-home")
say("  B changes 하루 목표 to 10 words")
tap("설정")
tap("하루 목표")
tap("10개")
tap("설정 저장")
time.sleep(10)
check("B saved 10 words", pref("study", "dailyWords") == "10")
save_device("B")

say("== Back to device A: opens the app ==")
load_device("A")
start()
time.sleep(10)
adb("shell", "input", "keyevent", "3")
time.sleep(10)
check("A got B's 하루 목표 (10)", pref("study", "dailyWords") == "10")
check("A still has its Telegram token", pref("study", "tgToken") == "TESTTOKEN123")
start()
shot("A-home-after")
say("== done ==")
