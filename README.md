# Duo Fold Effect

삼성 폴더블 기기에서 접기·펴기 전환 효과와 센서 접근 가능성을 조사하는 Android 실험 프로젝트입니다. 삼성·Apple의 공식 프로젝트가 아닙니다.

**현재 상태: 실험용 프로토타입. 연속 힌지 각도 접근과 자연스러운 화면 전환은 아직 해결되지 않았습니다.**

## 구현 및 확인 범위

- 접근성 화면 캡처 또는 MediaProjection으로 화면 이미지를 확보하고 다른 앱 위에 블러 효과를 표시합니다.
- 왼쪽 강한 블러 → 오른쪽 선명한 화면의 그라데이션을 적용합니다. 효과 중에는 고정된 캡처 이미지가 표시됩니다.
- 시험 기기의 표준 힌지 센서 관측값은 **0 / 90 / 180**, 보고 분해능은 **90°**입니다. 중간 상태에서 효과를 유지하고 끝점에서 해제합니다.
- 삼성 정밀 센서 `65686` 및 보조 IMU는 존재하지만 `SSENSOR` 권한으로 접근이 제한됩니다.
- ADB 도우미를 통한 내부·커버 화면 조기 점등은 시험했습니다. 독립 실행 앱으로 구현된 기능은 아닙니다.
- 영상 원본과 같은 효과, 모든 앱 호환성, 연속 각도 추적을 보장하지 않습니다.

## 빌드

필요 도구: JDK, Android SDK Platform 36, Build Tools 36.0.0. 기본 경로는 macOS Android Studio 설치 기준입니다. 다른 환경에서는 `JAVA_HOME`과 `ANDROID_SDK_ROOT`를 설정하세요.

```sh
bash build.sh
adb install -r artifacts/duo-probe.apk
```

패키지: `dev.duo.probe`. 로컬 디버그 키는 빌드 시 생성하며 저장소에 포함하지 않습니다.

앱에서 캡처 방식을 선택하고 해당 시스템 권한 화면에서 승인합니다. 접근성 방식은 Android 접근성 설정에서 서비스 활성화가 필요합니다. 앱의 중지 기능 또는 시스템 설정에서 서비스를 끌 수 있습니다.

화면 이미지는 앱 메모리에서 처리하며 전송하지 않습니다. 기본 센서 진단 CSV는 앱 내부에 저장될 수 있습니다. 조사용 기기 로그·스크린샷·APK·추출된 타사 파일은 이 저장소에 포함하지 않습니다.

## 개발 도구

- `scripts/early_screen_bridge.py`: 연결된 ADB 기기의 상태를 일시적으로 변경하는 조기 점등 실험. `--serial`을 지정하며 `--adb` 또는 `ADB` 환경 변수로 실행 파일을 설정합니다. 기기별 상태 번호가 다를 수 있습니다.
- `scripts/sensor-probe/`: Android shell에서 실행할 센서·디스플레이 진단 Java 소스.
- `scripts/ui_target.py`: UI 노드 조회/탭 도구. `ADB`, `ANDROID_SERIAL` 환경 변수를 사용합니다.

도우미는 별도 컴파일한 dex가 필요합니다. 상세한 과거 실험은 아래 기록을 참고하세요. 기록의 `artifacts/` 경로는 비공개 로컬 증거를 가리키며 저장소에서 열리지 않습니다.

## 기록

- [대체 센서 조사](docs/alternative-sensors-review.md)
- [힌지·효과 품질 분석](docs/quality-and-hinge-review.md)
- [Wonderland 실기기 조사](docs/wonderland-device-review.md)
- [실험 이력](docs/experiment-history.md) — 이전 구현과 폐기한 접근도 포함하며, 최신 상태는 위 요약과 후속 기록을 우선합니다.

## 상태 모델 테스트

```sh
mkdir -p build/test-classes
javac -d build/test-classes app/src/dev/duo/probe/FoldMotion.java tests/FoldMotionTest.java
java -cp build/test-classes dev.duo.probe.FoldMotionTest
```

이 테스트는 중간 상태 유지·끝점 해제·반전 동작을 확인합니다. 물리 센서 정확도나 실제 화면 전환 품질을 검증하지는 않습니다.
