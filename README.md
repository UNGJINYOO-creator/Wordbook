# 단어장 (WordBook)

사진이나 스크린샷 속 영어 단어를 찾아 단어장을 만들고, Flashcards Deluxe처럼 플래시카드로 외우는 안드로이드 앱입니다.

## 기능
- **사진으로 단어장 만들기**: 카메라로 찍거나 앨범(스크린샷 포함)에서 골라 영어 단어를 인식(ML Kit, 기기 내 처리)
- **사진 속 한국어 뜻 그대로 사용**: 단어장·교재처럼 단어 옆(같은 줄, 오른쪽 열, 바로 아랫줄)에 적힌 한국어 뜻을 짝지어 저장. 번호·발음기호·품사 표시(n. v.)는 정리
- **뜻이 없는 단어만 자동 번역**: 영→한 기기 내 번역(필요할 때 약 30MB 모델을 한 번 다운로드). 저장 전에 뜻을 직접 고칠 수 있음
- **빈 단어장 만들기 / 카드 직접 추가**: 앞면(영어), 뒷면(뜻). 뜻을 비우면 자동 번역
- **편집**: 홈 우측 상단 '편집'으로 단어장 삭제, 카드를 눌러 수정·삭제, 제목을 눌러 이름 변경
- **학습**: 카드 탭 = 뒤집기, 왼쪽으로 밀기 = 알아요(초록 체크), 위로 밀기 = 확실히 알아요(파란 더블 체크, 다음 학습부터 제외), 아래로 밀기 = 몰라요(빨간 X), 버튼도 있음, 발음 듣기(TTS)
- **요약 리포트**: 처음에 맞힌 수, 틀린 단어 목록, 틀린 것만 다시 학습
- **단어장 옵션(톱니바퀴)**: 카드 섞기, 틀린 카드 반복, 뒷면(뜻) 먼저
- **내보내기(공유)**: `단어<TAB>뜻` 형식 텍스트로 메일·메신저 등에 백업

## 빌드 방법
1. [Android Studio](https://developer.android.com/studio) 설치 (Ladybug 2024.2 이상)
2. `File > Open`으로 이 `WordBook` 폴더 열기 → Gradle 동기화 대기
3. 휴대폰을 USB 디버깅으로 연결하거나 에뮬레이터 실행 후 ▶ Run
- APK 파일: `Build > Build App Bundle(s) / APK(s) > Build APK(s)`

## 구성
- Kotlin, Jetpack Compose(Material 3), Navigation Compose
- Room (단어장 `decks`, 카드 `words`)
- ML Kit Text Recognition(Korean, 영어 포함), ML Kit Translate(EN→KO)
- 최소 Android 8.0 (API 26)

```
app/src/main/java/com/wordbook/app/
  MainActivity.kt        화면 이동
  data/                  Room 엔티티·DAO·DB
  ml/WordExtractor.kt    사진 → 영어 단어 목록 (흔한 기능어 제외, 중복 제거)
  ml/Translator.kt       영→한 번역
  ui/HomeScreen.kt       단어장 목록, 사진 찍기/앨범, 빈 단어장, 편집
  ui/ScanScreen.kt       인식된 단어 고르기·뜻 수정·저장
  ui/DeckScreen.kt       카드 목록, 추가/수정, 옵션, 내보내기
  ui/StudyScreen.kt      플래시카드(뒤집기·스와이프)·요약
```
