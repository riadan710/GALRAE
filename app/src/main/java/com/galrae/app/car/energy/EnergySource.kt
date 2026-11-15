package com.galrae.app.car.energy

import kotlinx.coroutines.flow.StateFlow

/** 차량이 쓰는 에너지 종류. 멤버 카드 칩("충전 필요" / "주유 필요")과 Flow 2 트리거 문구가 이걸로 갈린다. */
enum class EnergyType { EV, FUEL }

/** 남은 에너지. [percent]는 0~100. */
data class EnergyLevel(
    val type: EnergyType,
    val percent: Int,
)

/**
 * 내 차의 남은 에너지(배터리 또는 연료)를 내보내는 출처.
 *
 * 차종마다 읽는 SDK가 달라서 인터페이스로 나눈다. 쓰는 쪽(멤버 카드, Flow 2 트리거)은 차종을 몰라도 된다.
 * - EV: [EvBatterySource] — Vehicle `EvBattery` SOC 구독
 * - 내연기관: FuelSource (미구현, 아래 참고)
 *
 * TODO(내연기관): FuelSource 구현 (docs 1-5, 3장 차종 분기 규칙)
 *  - Pleos `Fuel.checkFuelPercentCapabilities`로 지원 여부 확인 → 지원하면 `getFuelPercent` + `registerFuelPercent` (`CAR_ENERGY`)
 *  - 지원 안 하면 AAOS `FUEL_LEVEL ÷ INFO_FUEL_CAPACITY` (`android.car.permission.CAR_ENERGY`, `CAR_INFO`)
 *  - 에뮬 차량이 EV라 실값 검증 불가 (`Fuel`은 `ERROR_NOT_SUPPORTED_PROPERTY`). 시연의 "주유 필요"는 가상 멤버로 보여준다
 */
interface EnergySource {
    /** null이면 아직 값을 받지 못함. */
    val level: StateFlow<EnergyLevel?>

    fun start()

    fun stop()
}
