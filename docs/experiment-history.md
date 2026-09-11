# Duo fold feasibility probe

실기기 검토: 2026-09-10 / SM-F971N / Android 17.

## 확인 결과

1. **캡처 기반 다른 앱 위 블러·굴절 성공.** MediaProjection → ImageReader → Bitmap → TYPE_APPLICATION_OVERLAY로 실제 삼성 홈 화면을 가공해 표시했다. `artifacts/fixed-overlay-1.png`가 실기기 증거다.
2. **작은 각도에서 내부 화면 점등 성공(ADB).** 사용자가 약 45~60도에서 유지한 상태에서 `cmd device_state state 3`을 요청했다. 물리 mBaseState=TENT는 그대로이고 mCommittedState=OPENED가 됐으며 내부 패널 (device-local display ID omitted)가 state ON, committedState ON으로 전환됐다. 8초 뒤 state reset으로 자동 감지 복원, override가 비었음을 확인했다. 이는 일반 설정의 임계 각도 변경이 아닌 일시적 개발용 상태 override다.
3. **연속 힌지 각도는 아직 미확보.** 표준 센서 type 36 등록 성공. 초기에는 0/90만 관측했으나 후속 실기기 시험에서 180도도 수신했다. 현재 관측값은 0/90/180이며 센서가 보고하는 resolution은 90도다. 앱에서 값을 반올림하거나 필터링한 결과가 아니다.
4. **정밀 각도 후보는 권한 제한.** 삼성 Folding Angle, type 65686 / com.samsung.sensor.folding_angle가 존재한다. 요구 권한 com.samsung.permission.SSENSOR는 이 기기에서 signature|privileged. 일반 앱용 허용 스위치가 아니다. sensorservice에서도 해당 센서 값은 [value masked]로 출력된다. ADB shell 패키지의 permission dump에서 SSENSOR grant를 찾지 못했다. Shizuku가 이 권한까지 해결한다고 검증된 것은 아니다.
5. **공개 cross-window blur 미지원.** wm disable-blur 조회 결과 supported=false/enabled=false, 앱 조회도 false. 이는 캡처 기반 블러 가능 여부와 무관하다.

## 캡처 효과 동작과 한계

- 효과가 없을 때 현재 화면을 긴 변 720px의 이미지로 수집한다(복사 간격 최소 80ms).
- 효과 직전 이미지 한 장을 고정하고, 짧은 블러·픽셀 변위 애니메이션을 다른 앱 위에 표시한다.
- 오버레이 표시 중에는 캡처 이미지를 갱신하지 않아 자기 자신이 반복해서 캡처되는 피드백을 방지한다. **효과 중 동영상이 실시간 갱신되는 블러는 아니다.**
- 윈도 alpha 0.8 및 NOT_TOUCHABLE/NOT_FOCUSABLE을 사용한다. 정상 화면이 일부 비쳐 보이는 합성이다. 터치 통과 조건은 설정했지만 효과 중 터치의 별도 실측은 아직 하지 않았다.
- 센서 값 변화로 700ms, 수동 테스트로 1400ms 효과를 실행한다. 실제 연속 각도와 1:1로 연결한 효과가 아니다.
- 내부 화면 크기 변경 후 캡처 크기 재설정과 효과 실행을 실기기 로그에서 확인했다.
- Activity가 백그라운드일 때 ValueAnimator가 진행하지 않고 투명 오버레이가 남는 현상이 발생했다. foreground service의 짧은 elapsed-time 타이머로 수정 후 실제 표시와 종료를 확인했다.
- 21:02:33에 접힘으로 화면이 꺼졌고(mLastSleepReason=device_folded), 곧 MediaProjection이 시스템에서 종료됐다. 화면 잠금/꺼짐 시 세션 종료와 재승인을 처리해야 한다.
- 장기 배터리/발열, 모든 앱, 보안 콘텐츠, 지속적인 동영상 블러는 미검증이다.

## 설정 검토

- system/secure/global 설정 키, 실제 SecSettings.apk의 리소스, /vendor/etc/devicestate/device_state_configuration.xml, dumpsys device_state를 확인했다.
- 접었을 때 앱을 계속 사용하는 fold_lock_behavior_setting이 존재한다. 초기 selective_stay_awake_key였으나 후속 확인 시 사용자가 선택한 항상(stay_awake_on_fold_key)이었다. 이 작업에서 설정값을 변경하지 않았다.
- 화면 점등 임계 각도를 사용자가 정하는 옵션은 확인하지 못했다. 리소스가 없다는 것만으로 모든 비공개 경로가 없다고 단정하지 않는다.
- vendor 설정은 lid-switch 기반 CLOSE/TENT/OPEN 상태로 구성되어 있다. 런타임은 삼성 FoldingSensorPolicy를 사용한다.
- ADB의 CONTROL_DEVICE_STATE 권한을 통한 임시 전환은 검증했다. 일반 배포 앱 또는 Shizuku 앱으로 제품화하는 것은 별도 검토가 필요하다.

## 참고 영상

https://www.youtube.com/shorts/Tfw0exXUANo

브라우저에서 실제 프레임을 탐색해 확인했다. 약 20초 지점에 갤럭시 효과 장면, 24~25초 지점에 내부·외부 디스플레이 스크린샷을 연결한 방식이라는 자막이 있다. 열어 본 설명란에는 구체적인 센서 API/소스 링크가 없었다. 영상의 센서 활용 설명만으로 이 기기에서 일반 앱이 연속값을 받는다고 입증되지는 않는다.

## 빌드 및 테스트

`bash build.sh` (이 Mac의 Android SDK 36 / Android Studio 번들 JDK).

출력: artifacts/duo-probe.apk. 패키지: dev.duo.probe, Activity: .MainActivity, 로그 태그: DuoProbe, DuoCapture.

앱에서 **다른 앱 위 캡처 효과 시작** → 화면 공유 → **5초 뒤 효과 테스트 · 설정 앱으로 이동**. 홈 또는 다른 앱 위에 효과가 표시된다. 접기·펴기 변화도 foreground service에서 감지한다. **캡처 및 효과 중지** 또는 시스템 화면 공유 중지로 종료한다.

캡처는 메모리에서 처리하며 네트워크 전송 또는 영상 파일 저장 코드는 없다. 검증용 스크린샷은 ADB로 Mac의 artifacts에 저장했다. 기본 센서 진단의 CSV는 Activity pause 시 앱 내부에 저장된다.

검토 종료 시 MediaProjection은 시스템에 의해 종료된 상태였으며, ADB 접힘 상태 override는 복원했다. 오버레이 권한은 테스트를 위해 허용된 상태다.

## 공식 근거

- https://developer.android.com/media/grow/media-projection
- https://source.android.com/docs/core/display/window-blurs
- https://developer.android.com/reference/android/hardware/Sensor
- https://developer.android.com/reference/android/view/WindowManager.LayoutParams

기기별 판단은 공식 문서의 일반론보다 위 실기기 결과를 우선했다.


## 후속 실기기 검증 (21:40 이후)

- 기존 무선 ADB 연결이 살아 있어 재페어링 없이 진행했다.
- 표준 센서에서 180도도 관측했다. 현재 확인한 값 집합은 **0, 90, 180**이다.
- 일반 앱에서 삼성 전용 센서 type 65686, 65695, 65697에 직접 등록을 시도했으며 모두 false를 반환했다. 표준 type 36은 true였다.
- 삼성 프레임워크의 별도 컨텍스트 경로도 조사했다. 실제 앱에서 SemContextManager의 Context/Looper 생성자는 reflection으로 보이지 않았다. 이 결과는 해당 시도가 실패했다는 뜻이며 모든 비공개 경로가 없다는 증명은 아니다.
- 실제 설정 UI에서 '커버 화면에서 앱 이어서 사용 → 항상'이 이미 선택되어 있었으며, 이 작업에서는 변경하지 않았다. system 설정값은 stay_awake_on_fold_key였다.
- 홈 화면에서는 '항상'이어도 접으면 잠금이 발생했다. 시스템 로그의 명시적인 캡처 종료 사유는 **STOP_REASON_KEYGUARD**였다.
- **설정 앱을 실행한 상태에서는 같은 MediaProjection 세션으로 내부↔커버 전환을 여러 번 통과했다.** 21:44:17 시작 세션에서 21:44:37/43/45/48/50에 캡처 해상도가 정상 전환됐다.
- 앱에 캡처 실행/종료 상태 표시를 추가했다. 종료된 토큰을 재사용하지 않고 새 화면 공유 동의로 재시작한다.
- 화면 전환 직전 효과와 전환 후 효과가 중복 방지 시간에 충돌하는 문제를 발견해 수정했다. 새 크기의 실제 캡처 프레임을 기다린 뒤 효과를 실행하도록 변경했다.
- scripts/early_screen_bridge.py는 외부 Mac의 ADB가 필요한 별도 개발용 실험이다. 정밀 각도 추적이 아니라 90 단계 이벤트에 내부 화면 OPENED를 요청하고 0 단계에서 자동 감지로 복원한다. 120초 기본 제한 및 종료 시 복원을 포함한다.

## 접근성 캡처 경로 (21:53 이후)

- 사용자의 명시적 승인 후 시스템 설정에서 Duo 화면 전환 실험 접근성 서비스를 켰다. 실제 연결과 표준 힌지 센서 등록을 확인했다.
- dumpsys accessibility에서 bound/enabled 및 capabilities=128(스크린샷)을 확인했다. 화면 UI 구조 읽기·자동 터치는 요청하지 않는다.
- 접힘 이벤트 때 AccessibilityService.takeScreenshot으로 현재 화면 한 장을 받아 TYPE_ACCESSIBILITY_OVERLAY에 700ms 블러 효과를 표시하는 별도 경로를 추가했다. 연결 시 기존 MediaProjection 서비스를 중지한다.
- 이 경로는 화면 공유 세션을 사용하지 않는다. 잠금 화면에서는 캡처를 요청하지 않으며, 실제 잠금 이후 복귀와 캡처 성공 여부는 실기기 시험 중이다.
- 서비스는 설정 → 접근성 → 설치된 앱 → Duo 화면 전환 실험에서 끌 수 있다. 로그 태그는 DuoAccess다.

- 21:53:45 접근성 스크린샷 1248×1972, 21:53:48 이후 2448×1848 캡처 성공을 확인했다. 이때 dumpsys media_projection은 null이었다. 오버레이 추가 오류는 없었다.
- 21:54:01 홈 접힘으로 device_folded 화면 꺼짐, 21:54:03 전원 버튼으로 깨어남을 확인했다. 이후 새 접힘 이벤트에서의 재캡처는 아직 확인 중이다. 증거 로그: artifacts/accessibility-fold-test.log.

## 연속 각도 추가 검증 (22:03 이후)

- scripts/sensor-probe/ShellSensorProbe.java를 별도 DEX로 빌드하여 ADB app_process에서 UID 2000으로 15초 실행했다. 기기 설정이나 권한 정책은 변경하지 않았다.
- shell의 SSENSOR permission check=-1. 표준 가속도계(1), 자이로(4), 힌지(36) 등록은 true. Folding Angle(65686), lid_angle_fusion(65695), folding_state_lpm(65697)은 false.
- 반대쪽 본체 IMU도 존재하지만 accelerometer_sub(65687/65688), gyroscope_sub(65689/65690) 모두 SSENSOR를 요구하며 shell 등록 false. 두 본체 IMU로 상대각도를 계산하는 경로도 현재 접근권한으로 사용할 수 없다.
- shell에서 SemContextManager 생성과 질의는 성공했다. Hall Sensor(43) isAvailableService=false, Free Fall(55)=true. getCurrentServiceList는 활성 서비스 목록으로 전체 지원목록과 다르다. dumpsys scontext의 전체 지원목록에도 Hall Sensor는 없었다.
- /sys/class/sensors는 shell 접근 거부. dumpsys input/motion_recognition에서 사용할 수 있는 현재 연속 힌지각 필드는 찾지 못했다.
- 증거: artifacts/shell-sensor-probe.log, artifacts/shell-dual-imu-probe.log. 두 진단 프로세스는 제한시간 후 종료되었다.
- 결론: 비루팅 Shizuku의 shell 직접 센서 구독만으로 정밀값이 열릴 것이라는 근거가 없다. 삼성 시스템 권한을 통한 경로 또는 별도 시스템 계층 연구는 미검증이다. 0/90/180 사이 보간은 연출을 매끄럽게 만들 수 있지만 실제 각도 추적을 복원하지 못한다.

## 유리판 전환 프로토타입 (22:09 이후)

- 사용자 방향 수정에 따라 실시간 투영 대신 전환용 정지 이미지 한 장과 실제 패널 전환 시점을 중심으로 접근성 효과를 다시 구현했다.
- 90도에서 이미지를 준비하고 DisplayManager의 크기/점등 변경 시 전환을 시작한다. 펼침 끝 180도 이벤트는 보조 시작 신호다. 잠금 상태에서는 표시하지 않는다.
- 커버 비율의 둥근 유리판을 축소·왼쪽 이동·블러·페이드하고, 아래 이미지의 확대/블러가 풀리며 실행 중 앱을 드러낸다. 접기는 반대 방향이다.
- 감쇠 스프링으로 약 0.6초에 수렴하며, 효과 도중 반대 센서 이벤트를 받으면 현재 위치와 속도를 유지해 목표를 바꾼다. 이는 실제 연속 각도 측정이 아닌 이벤트 기반 연출이다.
- 앱에 펼치기/접기 미리보기 버튼을 추가했다. 접근성 경로는 버튼을 누르고 설정 앱으로 이동한 뒤 3초 후 표시한다. 중지 버튼은 접근성 서비스도 disableSelf로 끈다.
- 빌드와 설치 성공. 설정 앱 위 펼치기 효과의 실행/종료 로그 및 artifacts/glass-open-preview.png를 확인했다. 실제 접힘에 따른 타이밍·방향 체감은 사용자 실기기 피드백 대기 중이다.
- 앱 자체의 조기 화면 점등은 이번 변경에 포함되지 않았다. 기존 ADB 조기 점등 실험과 별개다. 홈에서 접으면 꺼지는 시스템 동작도 그대로다.

## 펼침 오류 수정 (22:14)

사용자 실기기 피드백 후 기존 버전 중지. 로그에서 패널 변경으로 첫 효과 실행 후 180 이벤트가 두 번째 효과를 실행하고 종료 로그가 없는 문제를 확인했다.
- 같은 펼침에서 효과가 이미 실행됐으면 180 endpoint는 재실행하지 않는다.
- postOnAnimation 대신 Handler 타이머로 진행해 비활성 Activity의 프레임 콜백에 의존하지 않으며, 별도 1초 제거 타이머를 추가했다.
- 다른 화면 비율의 캡처를 배경 전체로 늘리지 않는다. 같은 비율도 균일 스케일로 그린다.
- 자체 진단 그림의 텍스트가 펼친 화면 폭에 비례해 과도하게 커지는 문제를 수정했다.
- 내부 화면 2448x1848에서 수정 효과 start/end(22:14:03.572~04.317), 종료 후 dumpsys window에 Duo glass transition 없음 확인.
- 수정 APK 설치 후 앱을 force-stop하여 자동 효과는 중지했다. 물리 접힘 반복과 미적 완성도는 검증 완료로 주장하지 않는다.

## 조기 점등 분리 (후속)

- scripts/early_screen_bridge.py에서 MediaProjection 및 DuoCapture 로그 의존성을 제거했다.
- HingeSignal.java를 shell UID로 실행하여 표준 힌지값만 읽는다. 0→90에서 ADB OPENED(3), 180 또는 0 도달 시 자동 감지 reset. 180→90의 닫기 방향에서는 새 override를 걸지 않는다.
- 기존 override가 있으면 중단, 최대 300초 제한, 종료 시 소유한 override 복원을 유지한다. 방향 판정 및 복원 분기를 로컬 검증했다.
- 외부 Mac ADB 도우미이며 앱 단독 기능/연속 각도 측정은 아니다. Shizuku 통합은 아직 구현하지 않았다.

- 60초 실기기 실행에서 HINGE 0→90→180→90→0 수신, inner_requested 및 automatic_restored 확인. 이후 dumpsys device_state의 override empty 확인. 체감 점등 각도는 사용자 확인 대기.

## 각도 상태 유지 구현 (22:33 이후, 현재 버전)

이 절이 앞선 일회성 유리판 효과 설명을 대체한다.

- FoldMotion이 0/90/180을 0/0.5/1 목표값으로 유지한다. 90에서 스프링이 수렴하면 프레임 타이머만 정지하고 오버레이는 유지한다. 끝점 센서 입력 없이 스스로 완료되지 않는다.
- 0/180에 수렴하면 오버레이를 제거한다. 오버레이가 없는 상태의 끝점 이벤트는 효과를 재생하지 않는다. 반전은 현재 위치·속도를 유지한다.
- 렌더링은 현재 패널의 화면 한 장을 균일 확대(+최대 4.5%), 블러, 각도에 고정된 반사로 표현한다. 별도 축소 카드/화면비 늘리기는 제거했다. 중간 상태는 정지 이미지이며 실시간 화면 갱신은 아니다.
- 패널이 바뀌면 이전 패널 이미지는 제거하고 새 패널 캡처를 사용하며, 각도 진행 상태는 유지한다. 캡처 요청이 진행 중일 때 끝점 또는 화면 변경이 발생하면 오래된 결과를 표시하지 않는다.
- 제어용 앱을 열면 오버레이를 잠시 숨겨 중지 버튼 접근을 보장한다. 화면 잠금/꺼짐에는 숨긴다.
- tests/FoldMotionTest.java: 10초 중간 유지, 같은 신호 반복, 양 끝점, 진행 중 반전, NaN 입력 통과.
- 실기기 합성 단계 시험: 22:33:23 target0.5 → 24.179 HOLD → 29.371 target1 → 30.089 endpoint removed. artifacts/hold-early.png와 hold-late.png SHA1 일치(c853666c1ded221400723239986c7df604517dab). hold-end.png는 정상 앱 화면, dumpsys window에 오버레이 없음.
- UiWindowProbe.java는 접근성 서비스를 중지시키지 않는 UiAutomation 연결로 현재 창 트리를 읽는 개발 도구다. 서비스 자체는 노드 읽기 권한을 추가하지 않았다.

## 사용자 확인 및 그라데이션 블러 (22:37, 최신)

- 사용자가 실제 접힘·펼침에서 각도 유지 동작이 잘 된다고 확인했다. 실제 로그에서도 22:34:06.635 중간 HOLD 이후 11.455의 180 입력까지 유지, 이후 endpoint removed가 관측됐다. 닫을 때도 90 HOLD 뒤 0/패널전환으로 해제됐다.
- 후속 요청에 따라 왼쪽 강한 블러 → 오른쪽 선명한 화면으로 변경했다. 기본 화면 위에 안드로이드 기본 Gaussian blur 두 단계(9/30px)를 부드러운 공간 마스크로 합성한다.
- 마스크의 경계는 FoldMotion.position에 연결된다. 펼칠수록 오른쪽의 선명한 영역이 왼쪽으로 넓어지고, 90에서는 경계도 정지하며, 접을 때 반대로 돌아간다. 얇은 반사도 같은 경계를 따른다.
- 초기 희소 샘플링 셰이더에서 격자 흔적을 발견하여 제거했다. 최신 artifacts/gradient-middle-clean.png에서 왼쪽 블러와 선명한 오른쪽, 격자 흔적 제거를 시각 확인했다.
- 최신 APK 설치 완료, 단계 미리보기 중간 HOLD 및 endpoint 제거 로그 확인. 물리 센서 동작은 기존 검증된 상태 유지 모델을 그대로 사용한다.

## 흰 반사 제거, 블러 대비 강화 및 동시 점등 (22:41 이후)

- 사용자 요청대로 반사/흰 조명 띠를 그리는 코드를 완전히 제거했다.
- 왼쪽 최대 블러 반경을 30→60px로 강화하고 약한 층은 14px로 변경했다. 마스크 전이 구간을 좁혀 오른쪽 선명한 구역을 넓혔다. 각도 상태 유지·역방향 진행은 동일하다.
- 최신 APK 빌드/설치 및 artifacts/gradient-strong.png 시각 확인.
- ADB device_state 4(CONCURRENT_INNER_DEFAULT)와 5(CONCURRENT_OUTER_DEFAULT)를 각각 임시 시험해 두 물리 패널 모두 state ON / committedState ON 확인. 원래 state3, override empty로 복원했다.
- state4에서 기본 설정 앱은 내부 화면에 남고 전면 캡처는 검정이었다. 동시 패널 전원과 양쪽 콘텐츠 표시를 구분해야 한다. 증거: concurrent-4-display.txt, concurrent-5-display.txt, concurrent4-inner/outer.png.
- 동시 모드4에서 ADB로 display1에 우리 앱 Activity를 실행하는 시험은 SecurityException(Permission Denial, launchDisplayId=1)으로 거부됐다. 두 패널의 전원은 켤 수 있으나, 이 경로로 전면에 앱 콘텐츠를 표시하는 데는 성공하지 못했다. 시험 finally에서 state reset, 원래 state3 복원 확인.


## 확대 제거, 가장자리 암부 및 원근 (후속 최신)

- 블러 강도에 따른 전체 +4.5% 확대를 제거했다. 캡처를 1200px로 축소하던 처리도 제거해 오른쪽은 원본 픽셀과 위치를 유지한다.
- 처음 적용한 넓은 98% 암부는 사용자 피드백으로 폐기했다. 현재 암부 마스크는 왼쪽 1.5%까지 최대 94%, 13% 지점에서 완전히 투명해진다. 나머지 영역은 기존 14/60px 그라데이션 블러로 내용이 보인다.
- 사용자 제공 YouTube Shorts를 브라우저에서 59/61/64% 지점으로 이동해 중간/완전 펼침/갤럭시 장면을 직접 확인했다. 촬영 시점에 따른 물리 원근과 소프트웨어 효과를 이 영상만으로 정량 분리할 수는 없다.
- 원근의 첫 구현은 왼쪽 절반의 힌지를 고정한 사영변환이다. 중간 상태에서 바깥쪽 가로 위치를 전체 폭의 7.5%, 위아래를 높이의 3.5% 안으로 넣는다. 오른쪽 절반은 변형하지 않는다. 원근·블러·암부 모두 같은 FoldMotion 상태에 묶여 멈추고 반전한다. 실제 연속 각도를 복원하거나 영상의 원근을 정확히 측정한 값은 아니다.
- 양 끝점에는 변형이 0으로 돌아가며, 패널이 바뀌면 새 패널의 이미지를 사용한다. 닫힌 기기의 전면 화면까지 같은 앱 레이아웃을 투영하는 기능은 아니다.
- 빌드/실기기 설치 완료. 중간 화면은 artifacts/perspective-edge-middle.png, 종료는 perspective-edge-end.png. 이번 수정의 물리 접힘 체감은 추가 사용자 확인이 필요하다.
- 닫힘 합성 시험에서도 90 중간 HOLD → 0 endpoint removed, 종료 후 오버레이 없음 확인. 증거: perspective-edge-close-middle/end.png 및 perspective-edge-log.txt. 실물 각도 조작을 대신하는 합성 입력 시험이다.


## 2026-09-10 follow-up

SSENSOR is signature|privileged on this device; precise angle access remains unresolved. See docs/quality-and-hinge-review.md.
The user confirmed early cover illumination during the 90-second --early-cover trial. This requires the Mac ADB bridge.
Removed the incorrect perspective matrix and installed the APK. Cover preview capture-to-first-draw measured 54ms; physical panel wake latency is excluded.
The bridge now waits for the matching base state before releasing an endpoint override. The follow-up trace recorded base_ready=0 before reset, avoiding an immediate reset against the previous base state.
