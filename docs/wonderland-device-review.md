# Wonderland 실기기 대조 — 2026-09-10

## 커스텀 확장 경로 상세 감사

- **적용 범위:** 삼성 공식 설명은 홈/잠금 라이브 배경화면과 AOD→잠금 전환을 구분한다. 기기 UI도 움직이는 배경화면과 잠금화면 효과로 분리된다. ReloadedService 미리보기는 잠금화면 효과라고 표시한다. 일반 앱 창을 변형하는 기능으로 볼 근거는 없다.
- 공식 출처: https://www.samsung.com/hk_en/news/product/exploring-good-lock-3-3-features-recommended-by-samsung-developers-and-newsroom-editors/
- **파일:** 설치 APK의 Landscape-Blur.wrf, Alice-Warp.wrf를 실제로 열었다. ZIP 안에 썸네일과 wonderland.json이 있다. 이미지 Base64, layer_type_, icc_transition_speed, icc_mask_*, icc_xform_* 및 효과별 수치로 구성된다. Landscape는 layer_type14/blur_radius, Alice는 layer_type12/warp_direction_angle이다. artifacts/Landscape-Blur-schema.json 및 Alice-Warp-schema.json은 이미지 데이터 없이 구조를 보존한다.
- **파서:** D6.l의 읽기/쓰기 코드는 알려진 필드를 개별 처리하고 효과별 분기를 사용한다. 확인한 파일과 파서는 내장 효과 설정이며 임의 프로그램 실행 형식으로 확인되지 않았다. 모든 숨겨진 확장의 부재를 증명한 것은 아니다.
- **셰이더:** assets/shaders에 GLSL 효과 코드가 포함돼 있다. 별도로 발견한 JSONShaderComponentView/agslShaderCode의 J5.d.f(Context,int)는 Resources.openRawResource를 사용한다. 확인된 호출자는 sesl.outerGlow.AGSLShaderView/FrameLayout이다. 이를 사용자 WRF의 임의 셰이더 가져오기 지원으로 해석하면 안 된다. WRF 파서와 연결된 사용자 코드 로더는 확인하지 못했다.
- **각도 전달:** dynamicdepthview.e.onSensorChanged는 values[0]을 내부 필드에 저장하고 r6.e.accept→r6.n.m(float)로 전달한다. m은 기존 애니메이터를 취소하고 이전/새 각도를170으로 나눠0..1로 제한한 후40ms 보간한다. 이 경로에 외부 앱 각도 브로드캐스트/제공은 없다.
- **표시 구조:** Manifest의 네 WallpaperService는 BIND_WALLPAPER로 보호된다. SYSTEM_ALERT_WINDOW, MediaProjection 서비스, 접근성 서비스 선언은 없다. 이는 배경화면 중심 구조의 보조 근거이지 다른 모든 삼성 특권 경로의 부재 증명은 아니다.
- **판단:** 사용자 이미지/내장 효과 설정은 가능하지만, 설치 버전1.6.22에서 임의 앱 화면에 효과를 적용하거나 정밀 각도를 제3앱에 제공할 지원 경로는 확보하지 못했다. 따라서 다른 앱 위 전환이라는 목표의 해결책으로 Wonderland 파일 편집을 추천하지 않는다.
- **한계:** 임의 셰이더 키를 주입하는 시험, 배경화면 실제 변경, APK 패치/설치는 수행하지 않았다. 공식 범위, 설치 코드 데이터 흐름, 실제 센서등록을 대조한 판단이다.

- ADB 연결 SM-F971N 확인. 설치 버전 1.6.22.
- dumpsys package: com.samsung.permission.SSENSOR: granted=true. 우리 앱 및 shell은 기존 시험에서 거부됨.
- 설치 APK를 로컬에서 읽기 전용 분석. DEX의 B6.f.accept 및 r6.n 경로가 getDefaultSensor(0x10096 = 65686)를 호출하고 registerListener로 등록하는 코드 확인. 공개 TYPE36과 다른 정밀 Folding Angle 센서다. 증거: artifacts/wonderland-sensor-code.txt, artifacts/wonderland-package.txt.
- UI 설정의 동적 애니메이션은 처음 OFF였으며 시험을 위해 ON으로 변경. 반응형 애니메이션은 OFF 유지. 배경화면 적용 및 편집 저장은 하지 않음.
- Alice-Warp 미리보기는 검은 화면/경고 아이콘. 편집 화면에서는 원본 그림이 표시됨. 해당 단계 sensorservice 덤프에서 Wonderland 활성 구독은 아직 확인되지 않음. 따라서 코드 경로와 권한은 확인했으나 실제 연속 센서 수신/각도별 시각 반응은 미검증.
- 사용자 움직임 완료 후 덤프: 정밀 센서 최근 이벤트는 있으나 Wonderland 구독 없음. 편집 화면으로는 센서 반응 시험이 성립하지 않았음.
- 원본 Fold8 데모 제작자의 GitHub 저장소는 재검색에서도 식별하지 못함. 셰이더 원본을 확보했다고 주장하지 않음.

## 실제 엔진 등록 확인 (23:20)

- CHANGE_LIVE_WALLPAPER와 ReloadedService 컴포넌트로 시스템 미리보기만 실행. 배경화면 설정 버튼은 누르지 않음.
- sensorservice 23:20:01 등록 result=OK: Folding Angle handle0x9c5, listener com.samsung.android.view.dynamicdepthview.e, PID16920 UID10542. ps로 해당 PID가 Wonderland :externalProcess임을 대조. has sensor access:true, suspended0.
- 이제 코드뿐 아니라 실제 정밀 센서 구독 성공까지 확인됨. 우리 shell의 등록 false와 실증적으로 다름.
- 센서 덤프 값은 [value masked]. 수치별 각도 정확도나 시각적 부드러움을 검증했다고 주장할 수 없음. 시스템 미리보기는 검은 화면과 Wonderland 앱에서 설정해야 한다는 안내가 표시됨.
- 증거 artifacts/wonderland-engine-sensors.txt, wonderland-engine-preview.png.
