# 폴드 효과 품질·전면 점등·각도 조사 (2026-09-10)

## 현재 코드에서 확인한 원인

1. ScreenshotAccessibilityService.recordScreen은 패널 종류를 구분하지 않고 width/2를 힌지로 가정한다. 접히지 않는 전면 패널에도 내부 화면처럼 절반 변형을 적용하는 설계 오류다.
2. 임의의 inset=width*0.075, depth=height*0.035는 시점·카메라 거리·실제 힌지각에서 계산한 투영이 아니다. 실제 접힌 화면에 추가 왜곡을 얹는다. 영상 속 물리 원근과 소프트웨어 보정을 분리하지 않고 구현했다.
3. setAlpha(material)로 원근 변형된 스냅샷 전체를 원래 앱 화면과 합성하므로 이동한 글자와 실제 글자가 겹치는 구간이 생긴다. 어두운 다크모드 설정 화면만으로 시각 검증한 것도 대비 문제를 놓쳤다.
4. displayChanged가 기존 이미지를 버린 뒤 90ms 대기하고, 새 패널이 ON인 때에만 화면 캡처를 요청한다. 화면 첫 점등 전에 준비한 전환이 아니므로 정상 화면 노출 후 효과가 나타날 수 있다. takeScreenshot/복사/창 추가 시간도 추가되지만 현재 정량 측정하지 않았다.
5. FoldMotion은 0/90/180 목표 사이에 스프링을 적용한다. 중간 범위에서 움직임·멈춤·반전을 관측할 수 없으며 보간은 측정이 아니다. 합성 단계 입력 시험 통과는 물리 접힘의 시각적 완성도를 증명하지 않는다.
6. early_screen_bridge.py 기존 버전은 0→90에서 OPENED(3) 요청만 구현했다. 180→90 닫기에는 아무 작업도 하지 않았다. 전면이 빨리 켜지지 않은 것은 이 누락과 관계있다.

## 인터넷 조사와 실측의 대조

- [Android Sensor](https://developer.android.com/reference/android/hardware/Sensor#getResolution()): resolution은 센서 단위의 분해능. registerListener 샘플링 주기와 각도 분해능을 혼동하면 안 된다. 우리 실측은 TYPE36 resolution=90, 삼성 Folding Angle65686 resolution=0.01.
- [삼성 Flex Mode](https://developer.samsung.com/galaxy-z/flex-mode.html): 공개 WindowManager 경로는 디스플레이 특징/접힘 상태를 제공한다. 조사한 공개 문서에서 센서 분해능 또는 점등 임계각을 사용자가 바꾸는 설정은 찾지 못했다. 모든 비공개 방법의 부재를 증명한 것은 아니다.
- [B4X 개발자의 Fold4/5 실험](https://www.b4x.com/android/forum/threads/catching-events-for-folding-devices.161814/): 같은 36/65686/65695/65697 타입을 시험했고 36 외 이벤트를 받지 못했다고 보고. Fold8에 그대로 일반화하지 않고 우리 로그와 대조했다.
- [Axiom 제작자와 사용자 피드백](https://www.reddit.com/r/galaxyzflip/comments/1ubq2f4/your_hinge_is_the_most_underused_feature_on_your/): Flip6 사용자도 중간 범위가 90에 고정된다고 보고. 제작자의 감도 곡선 조정 약속은 원시 각도 분해능 향상의 증거가 아니다.
- [참고 영상 제작자 원문](https://www.reddit.com/r/GalaxyFold/comments/1wcacld/tried_to_recreate_the_iphone_duo_animation_on_my/): 제작자는 Presentation API와 AGSL로 내부/외부 스크린샷을 힌지값에 따라 보간하는 개념증명이라고 설명했다. 게시글/검색 결과에서 구체적인 정밀 센서 권한 경로와 배포 소스는 확인하지 못했다. 영상이 가짜라고 단정할 근거도 없다.
- [Shizuku API 문서](https://github.com/RikkaApps/Shizuku-API/blob/master/README.md): ADB 모드는 UID2000 권한을 사용한다. 우리 shell 실측 SSENSOR=-1, vendor 센서 및 보조 IMU 등록 false이므로 Shizuku 설치만으로 이 권한이 생긴다고 볼 수 없다. 디스플레이 제어 앱 통합에는 별도 활용 가능성이 있다.
- [삼성 전면 화면 안내](https://www.samsung.com/us/support/answer/ANS10003219/): Continue apps 설정은 접은 뒤 앱을 계속 쓸지에 관한 항목이다. 조기 점등 임계각 설정의 근거가 아니다. 기기에는 stay_awake_on_fold_key가 이미 적용돼 있다.
- 추가로 로컬 framework에서 hinge_angle_lidevent_enabled 키를 찾고 [AOSP 정의](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/provider/Settings.java)를 대조했다. 정의는 lid event 사용 여부이며 감도 수치가 아니다. 기기 설정값은 세 namespace 모두 null. 효과가 확인되지 않은 값을 임의로 기록하지 않았다.

## 이번 실제 작업과 한계

- TENT(1) 임시 요청: 전면 ON 및 설정 앱 콘텐츠 표시, 이후 finally에서 state reset 성공. 그러나 당시 base=CLOSED였으므로 조기 점등 성공 증거가 아니다. artifacts/early-outer-tent-* 및 early-outer-restored.txt.
- 완전히 펼친 상태에서 TENT 요청으로 전면 콘텐츠를 옮기는 검증을 위해 사용자에게 자세 유지 요청을 보냈다. 해당 준비 응답 전에는 이 시험을 진행하지 않는다.
- scripts/early_screen_bridge.py에 --early-cover 실험 옵션 추가. 180→90에서 TENT(1), 0/180에서 reset. 기본 동작은 기존 내부 조기 점등만 유지. 분기·중복·미지 시작·끝점 복원 로컬 검증 통과. 실제 조기 전면 모드로 상시 실행하지 않았다.
- 이번 조사에서는 추가 시각 효과를 APK에 덧붙이거나 설치하지 않았다. 현행 APK의 원근/합성 결함이 해결됐다는 주장은 하지 않는다.

## 다음 구현의 기준

- 내부/전면 패널을 식별하고 각각의 좌표계·방향을 사용한다. 전면에 가상의 중앙 힌지를 만들지 않는다.
- 정상 화면과 변형된 복제본의 이중상이 생기지 않는 합성으로 바꾼다.
- 전면 모드 전환과 앱 재배치가 검증된 뒤 첫 프레임 준비/표시 지연을 측정한다.
- 연속값을 얻기 전에는 물리 각도에 정확히 붙는 원근으로 표현하지 않는다. 과한 3D보다 제한된 상태 기반 효과를 평가한다.
- 밝은 콘텐츠/어두운 콘텐츠, 실제 물리 펼침/접힘/중간 멈춤, 외부 촬영에서 각각 확인한다.

## 추가 진행: 권한 확정 및 전면 조기 점등 확인

- 기기의 dumpsys package permissions에서 SSENSOR의 sourcePackage=android, prot=signature|privileged를 재확인했다. 일반 사용자 승인/일반 ADB grant 대상이 아니다. [Android 권한 정의](https://developer.android.com/guide/topics/manifest/permission-element).
- 입력/센서/기기상태/context 관리자 메서드를 shell에서 읽기 전용 조사했다. InputManager의 semGetLidState, lid callback/time은 존재하나 조사한 관리자에서 연속 각도 getter는 찾지 못했다. scripts/sensor-probe/FoldApiProbe.java 및 artifacts/fold-api-probe.txt.
- [삼성 Sensor Extension SDK](https://developer.samsung.com/galaxy-sensor-extension/overview.html)는 2021-03-01 폐기됐으며 문서의 대상은 HRM/UV다. 정밀 힌지 권한 해법으로 볼 수 없다. 옛 Screen Fold API 자료는 제안/폴리필 설명이므로 실행 가능한 정밀 센서 경로로 취급하지 않았다.
- 실제 90초 ADB 시험에서 0→90→180→90→0 수신. 닫는 90 수신 34.9156초, cover_requested 34.9698초, 0 수신 37.3124초. 요청 완료는 0 신호보다 약 2.34초 전. 사용자도 '닫히기 전에 켜졌다'고 확인했다. artifacts/early-cover-live.log. 시험 완료 후 override empty. Mac 도우미 의존이며 앱 단독 상시 기능은 아니다.
- 마지막 원근 실험의 행렬을 제거하고 원본 좌표를 그대로 그리도록 수정/설치했다. 전면의 가짜 힌지와 변형 복제본의 위치 차이를 제거했다. 블러·좁은 가장자리 암부·각도 상태 유지 모델은 남긴다. 영상 같은 원근 재현 완료를 뜻하지 않는다.
- 전면 합성 미리보기에서 캡처 요청→첫 draw 54ms, 중간 HOLD, endpoint 제거 확인. artifacts/fixed-geometry-cover-middle.png 및 fixed-geometry-log.txt. 패널 점등 지연은 이 54ms에 포함되지 않는다.
- 실제 시험 로그에서 0 이벤트 직후 전면→내부→전면 크기 전환이 관측됐다. endpoint 센서와 정책 baseState 갱신 순서 차이가 원인일 가능성이 있다. 도우미는 endpoint 복원 전에 해당 baseState를 두 번 연속 확인하도록 수정했다(50ms 간격, 최대1초). 타임아웃이면 자동 복원한다. 이 수정의 물리 시험은 별도로 기록한다.

- Second 60s physical trial: TENT committed while base remained OPENED (early-cover-before-closed-state.txt), the immediate cover screenshot was black during the display switch and is not evidence of content readiness. User confirmation from the first trial remains the evidence for early visible content. Endpoint base_ready=0 at37.0816s, reset at37.1351s. DuoAccess logs show no cover-to-inner bounce after the final hinge0 in this trial. Override empty after completion.
- Real folding capture-request-to-first-draw: 34ms cover, 50ms inner in the second trial. The cover first frame followed its ON callback by343ms because a capture of the previous panel had consumed the400ms capture interval. Remaining visual timing issue identified; not fixed by the geometry removal.
