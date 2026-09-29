package dev.jeonsi.blects.service

import kotlinx.coroutines.flow.MutableStateFlow

/** 서비스 → UI 로 흐르는 휘발성 상태. 프로세스 수명. */
object ServiceState {
    val running = MutableStateFlow(false)

    /** 지금 GATT 서버에 붙어 있는 기기 주소들 */
    val connected = MutableStateFlow<Set<String>>(emptySet())

    /** 최근 페어링에 실패한 기기 주소. 기기 추가 시트가 힌트를 띄우는 데 쓴다. */
    val pairingFailed = MutableStateFlow<String?>(null)

    /**
     * 주소별 마지막 Current Time 읽기 시각. 등록 전(본딩 연결 중)에 읽어 간 것을
     * 등록 직후 lastSyncAt 에 반영하기 위해 둔다.
     */
    val lastRead = MutableStateFlow<Map<String, Long>>(emptyMap())
}
