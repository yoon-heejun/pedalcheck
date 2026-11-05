# 페달체크 · PedalCheck

**현재 밟고 있는 페달을 큰 글씨·색·페달 그림으로 보여 주고, 위험할 수 있는 입력 패턴을 화면과 한국어 음성으로 알리는 차량용 보조 앱 프로토타입입니다.**

- 팀: SA
- 버전: 0.4.3 (versionCode 7)

> 현재 버전은 실제 차량 신호 대신 모의 신호로 동작하는 정차·에뮬레이터 시연용입니다. 실제 Pleos 차량 신호 연동과 실차 안전 효과는 검증하지 않았습니다.

## APK 파일 위치

[`apk/SA_페달체크_release.apk`](apk/SA_페달체크_release.apk)

| 항목 | 값 |
|---|---|
| 패키지 / Activity | `io.pedalcheck.app` / `io.pedalcheck.app.MainActivity` |
| 빌드 | 서명된 release, 디버깅 비활성 |
| 지원 환경 | Android 9 (API 28) 이상, targetSdk 35 |
| SHA-256 | `2c818eec0ac51dc10cf2f901f38e77a3a4c6c225b0f9dfb15944adfda89b9d05` |

## 실행 방법

APK를 기기나 에뮬레이터에 설치한 뒤 **페달체크**를 실행합니다. ADB를 쓰면 저장소 루트에서 다음을 실행합니다.

```sh
adb install -r apk/SA_페달체크_release.apk
adb shell am start -n io.pedalcheck.app/.MainActivity
```

앱에서 다음 순서로 확인할 수 있습니다.

1. 상단의 **시연 도구 열기**를 누릅니다.
2. **액셀**, **브레이크**를 선택해 현재 페달 표시를 확인합니다.
3. **동시 입력**을 선택하면 0.3초 뒤, **깊은 액셀**을 선택하면 3초 뒤 빨간 경고와 음성이 나옵니다. 경고가 이어지면 음성은 8초 간격으로 반복됩니다.
4. **신호 끊김**을 선택하면 확인 불가 상태로 바뀌고 경고와 음성이 멈춥니다.
5. **화면 설정**에서 글씨 크기, 어두운 화면, 경고 음성 사용, 경고 목소리(남성·여성)를 바꿉니다.

## 테스트 환경 및 필요한 설정

**필요한 설정:** APK 설치 외에 별도 설정은 없습니다. 계정, API 키, 인터넷, 차량 연결이 필요하지 않고 모든 입력은 시연 도구의 모의 신호입니다. 수동 설치 시 APK 설치 허용이 필요할 수 있으며, 음성을 들으려면 기기 음량을 켜 두어야 합니다.

**테스트 환경:** Pleos API 34 ARM64 에뮬레이터(2560×1440), Apple Silicon Mac

2026년 11월 5일에 제출 APK로 확인한 결과입니다.

- 에뮬레이터 실행 검사 29개 통과: 설치, 페달 표시, 경고 화면과 음성, 8초 반복, 해제 시 중단, 신호 끊김, 목소리 선택과 유지
- 단위 테스트 16개 통과, release Lint 문제 없음, APK v3 서명 검증 통과

실제 운전 중에는 시연 도구를 조작하지 마세요. 앱은 운전자의 의도나 페달 오조작을 판정하지 않고 차량을 제어하지 않습니다. 경고 기준값은 시연을 위한 실험 설정입니다.

## 소스 빌드

`android-app/`을 Android Studio에서 열거나 다음을 실행합니다. JDK 17과 Android SDK 35(Build Tools 35.0.0)가 필요하고, SDK 경로는 `ANDROID_HOME` 또는 `android-app/local.properties`의 `sdk.dir`로 지정합니다.

```sh
cd android-app
./gradlew testReleaseUnitTest lintRelease assembleRelease
```

`assembleRelease` 결과물은 서명되지 않은 APK입니다. 서명은 `android-app/scripts/sign-release.sh`로 하며, 서명 키와 암호는 저장소에 포함하지 않습니다.

## 음성과 글꼴

경고 음성은 [Qwen3-TTS](https://github.com/QwenLM/Qwen3-TTS)(Apache-2.0)로 미리 합성해 APK에 넣었습니다. 앱은 모델을 포함하거나 내려받지 않으며 인터넷 없이 재생합니다. 생성 설정은 `android-app/app/src/main/assets/tts-provenance.json`에 있습니다. 글꼴은 Pretendard(SIL Open Font License)입니다.
