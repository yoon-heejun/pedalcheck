# 페달체크 · PedalCheck

**팀 SA | 현재 입력된 액셀과 브레이크를 큰 글씨·색·페달 그림으로 보여 주고, 설정한 입력 패턴을 화면과 한국어 음성으로 알리는 차량용 보조 앱 프로토타입입니다.**

공모전 제출 버전은 **0.4.1 / versionCode 5**입니다. 이 저장소는 Android 소스와 실행 가능한 서명된 release APK를 함께 제공합니다.

> 현재 구현은 모의 신호를 사용하는 정차·에뮬레이터 시연용입니다. 실제 Pleos 차량 신호 연동과 실차 안전 효과는 검증하지 않았습니다.

## 제출 APK

- 파일 경로: [`apk/SA_페달체크_release.apk`](apk/SA_페달체크_release.apk)
- [APK 다운로드](https://github.com/yoon-heejun/pedalcheck/raw/refs/heads/main/apk/SA_%ED%8E%98%EB%8B%AC%EC%B2%B4%ED%81%AC_release.apk)
- 패키지: `io.pedalcheck.app`
- 실행 Activity: `io.pedalcheck.app.MainActivity`
- release variant, `debuggable=false`, 제출용 자체 서명
- Android 9 / API 28 이상, targetSdk 35
- SHA-256: `28ff21461f42f461645c677bcf5ad0d769fb5c59b0f8a570b19aa816014b9690`

## 설치 및 실행

Android API 28 이상 기기 또는 Pleos 에뮬레이터에 APK를 다운로드해 설치합니다. 수동 설치 시 해당 기기의 APK 설치 허용 설정이 필요할 수 있습니다. ADB를 사용하면 저장소 루트에서 다음을 실행합니다.

```sh
adb install -r apk/SA_페달체크_release.apk
adb shell am start -n io.pedalcheck.app/.MainActivity
```

서명이 다른 이전 `io.pedalcheck.app` 시연 앱이 설치되어 있으면 해당 앱을 삭제한 뒤 설치해야 합니다. 삭제하면 이전 앱의 설정도 지워집니다. 별도 debug 패키지 `io.pedalcheck.app.overlaydemo`와는 함께 설치할 수 있습니다.

1. 앱을 열고 상단 **시연 도구 열기**를 누릅니다.
2. **액셀**, **브레이크**, **동시 입력**, **깊은 액셀**을 선택해 상태 표시를 확인합니다.
3. 동시 입력 300ms 또는 깊은 액셀 3초 지속 시 빨간 경고와 APK에 포함된 한국어 남성 음성이 나옵니다. 경고가 계속되면 8초 간격으로 음성을 반복합니다.
4. **신호 끊김**을 선택하면 확인 불가로 전환하고 경고 음성을 중단합니다.
5. **화면 설정**에서 글씨 크기, 어두운 화면, 경고 음성 사용을 바꿀 수 있습니다. **20초 자동 순환**도 시연 도구에서 선택할 수 있습니다.

수치는 모의 시연을 위한 실험 설정이며 운전자의 의도나 사고 여부를 판정하지 않습니다. 음성은 기기의 미디어 음량·오디오 출력·오디오 포커스에 영향을 받습니다. 인터넷 연결, 계정, API 키, 별도 TTS 엔진은 필요하지 않습니다.

## 소스 빌드

`android-app/`을 Android Studio에서 엽니다.

| 항목 | 버전 |
|---|---|
| JDK | 17 |
| Gradle wrapper | 8.9 |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 |
| Android SDK / Build Tools | API 35 / 35.0.0 |
| minSdk / targetSdk | 28 / 35 |

Android SDK를 설치한 뒤 `ANDROID_HOME` 또는 로컬 `android-app/local.properties`의 `sdk.dir`를 설정합니다. 첫 빌드는 의존성 다운로드를 위한 인터넷 연결이 필요합니다.

```sh
cd android-app
./gradlew testReleaseUnitTest lintRelease assembleRelease
./gradlew assembleDebug
```

`assembleRelease` 산출물은 `app/build/outputs/apk/release/app-release-unsigned.apk`입니다. 직접 설치하려면 소유자의 키로 서명해야 합니다. 이 저장소의 `apk/SA_페달체크_release.apk`는 이미 서명되어 바로 설치할 수 있습니다.

재서명은 [서명 스크립트 안내](android-app/README.md)를 참고하세요. 서명 키·암호·SDK 경로·로컬 캐시는 저장소에 포함하지 않습니다.

## 테스트 환경과 결과

2026년 11월 5일 KST 기준, Apple Silicon Mac / JDK 17 / Android SDK 35에서 release 빌드·단위시험·Lint·서명 검증을 수행했습니다. 실행 환경은 **Pleos API 34 ARM64 에뮬레이터**입니다. 상세 실행 결과와 화면은 [release 검증 기록](docs/RELEASE_VALIDATION.md)에 정리합니다.

```sh
# 저장소 루트에서 웹 모의 신호 엔진 검사
node demo/engine.test.cjs
# Android release 검사
cd android-app
./gradlew testReleaseUnitTest lintRelease
```

- Android release 단위시험: 14개 통과.
- release Lint: No issues found.
- 웹 모의 상태엔진: 15개 통과.
- APK v3 서명 검증 통과, 디버깅 비활성 확인.

모의 화면의 경고 표시·음성은 기본 켜짐입니다. 실제 차량 연결 기능은 미구현 상태를 명시하고 모의 값을 실제 신호로 가장하지 않습니다. 다른 앱 위 작은 오버레이는 debug 실험 기능으로, 제출 release에는 해당 권한·서비스·조작 버튼이 없습니다. 시연 영상의 오버레이 장면은 debug 실험을 보여 줍니다.

## 주요 자료

| 경로 | 내용 |
|---|---|
| [`android-app/`](android-app/) | Kotlin Android 앱, 테스트, Gradle wrapper, 음성·글꼴 리소스와 라이선스 |
| [`apk/`](apk/) | 제출용 release APK와 해시 안내 |
| [`docs/`](docs/) | 제출 release 검증 기록 |
| [`demo/index.html`](demo/index.html) | 서버 없이 브라우저에서 여는 오프라인 웹 목업 |
| [`submission/페달체크_심사위원용_모션그래픽_시연.mp4`](submission/페달체크_심사위원용_모션그래픽_시연.mp4) | 최신 모의 시연 영상, debug 오버레이 실험 포함 |
| [`research/`](research/) / [`safety/`](safety/) | 차량 신호 연동 검토, 안전 요구와 후속 검증 계획 |

Android 폴더의 이전 버전 변경 기록은 개발 이력입니다. 현재 제출 버전·APK 경로·검증 범위는 이 README와 `docs/RELEASE_VALIDATION.md`를 기준으로 합니다. 실제 운전 중 시연 도구를 조작하지 않습니다.
