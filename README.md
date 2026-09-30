# Android BLE CTS Server

안드로이드 폰을 **BLE CTS(Current Time Service, 0x1805) 시간 서버**로 만들어 주는 앱입니다.

iOS는 BLE 액세서리와 페어링하면 OS가 CTS를 자동으로 제공하지만, 안드로이드에는 그런
기능이 없습니다. 이 앱이 그 빈자리를 채워, [esp32-c3-clock](https://github.com/jeonsi/esp32-c3-clock)
같은 CTS 클라이언트 기기가 **아이폰과 동일한 방식**으로 안드로이드에서 시간을 동기화할 수
있게 합니다. CTS는 표준 서비스이므로 특정 기기 전용이 아니라 범용으로 쓸 수 있습니다.

## 동작 개요 (계획)

기존 아이폰 흐름을 그대로 재현합니다:

1. 기기(ESP32)가 CTS 솔리시테이션을 실어 광고
2. 폰이 스캔해서 연결 (central) — iOS에서는 사용자가 설정 > Bluetooth에서 탭, 여기서는 앱이 수행
3. 같은 연결 위에서 폰이 `BluetoothGattServer`로 CTS(0x1805)의
   Current Time 특성(0x2A2B)을 서빙 → 기기가 GATT 클라이언트로 읽어 시스템 클럭을 맞춤

핵심 과제는 BLE 자체보다 **백그라운드 생존**입니다. 클라이언트 기기의 주기적(예: 매시간)
재동기화에 응답하려면 foreground service와 Doze 대응이 필요합니다.

## 문서

- [사용성 설계](docs/usability.md) — 화면, 사용자 시나리오, 권한 순서, 백그라운드 생존이 사용자에게 보이는 방식

## 빌드

Android Studio 로 열거나, 터미널에서:

```
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # 시간 특성 인코딩 단위 테스트
./gradlew :app:lintDebug
```

- Kotlin + Jetpack Compose(Material 3), 단일 `app` 모듈, 패키지 `dev.jeonsi.blects`
- minSdk 29 / targetSdk 36 / compileSdk 37, AGP 9(내장 Kotlin), Room(KSP), DataStore
- 실기기가 필요하다. BLE 는 에뮬레이터에서 동작하지 않는다.

## 구조

| 경로 | 역할 |
|---|---|
| `ble/CtsGattServer.kt` | CTS(0x1805) + ANCS 스텁 GATT 서버. Current Time / Local Time Information 인코딩은 `TimeCodec.kt` |
| `ble/CtsScanner.kt` | 솔리시테이션 UUID 0x1805 필터 스캔 |
| `ble/DeviceLink.kt` | 등록 기기별 `connectGatt(autoConnect)` 재연결 유지 |
| `service/TimeServerService.kt` | foreground service(`connectedDevice`). 블루투스 on/off, 시간 변경, 본딩 상태를 로그로 남김 |
| `service/BootReceiver.kt` | 부팅·앱 업데이트 후 자동 시작 |
| `data/` | Room(기기 목록, 7일 이벤트 로그) + DataStore(설정) |
| `ui/home` | 홈: 상태 배너, 서비스 스위치, 기기 카드, 기기 추가 바텀 시트 |
| `ui/detail` | 기기 상세 로그, 내보내기, 제거 |
| `ui/settings` | 자동 시작, 알림·배터리 설정 진입, 서빙 서비스 목록 |

## 상태

실기기(Galaxy S10e, Android 12)에서 esp32-c3-clock 과 페어링하고, 앱을 닫고 화면을 끈 채 밤새
매시간 재동기화가 빠짐없이 되는 것을 확인했다. 페어링은 앱이 `createBond()`로 먼저 본딩하고 GATT 특성은
모두 암호화 전용이어야 한다는 두 가지를 실측으로 배웠다 — [사용성 설계](docs/usability.md)의
"기기 추가"와 "실측 결과" 참고. 폰 재부팅 뒤에는 잠금을 풀기 전에 서비스가 떠서 바로 동기화한다.
