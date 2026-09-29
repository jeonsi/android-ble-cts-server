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

## 상태

초기 스캐폴딩 전 단계. 사용성 설계 완료, Android Studio 프로젝트 생성 예정.
