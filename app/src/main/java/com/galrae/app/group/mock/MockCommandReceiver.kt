package com.galrae.app.group.mock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.galrae.app.group.model.ProposalKind
import com.galrae.app.group.model.ProposalTrigger
import com.galrae.app.meetup.MeetupRecommender
import com.galrae.app.meetup.MeetupResult
import com.galrae.app.meetup.PlaceType
import com.galrae.app.place.Place
import com.galrae.app.route.RouteManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 시연 중 가상 멤버를 adb 명령으로 조작한다. 화면에 디버그 UI를 띄우지 않기 위해서다 (1차계획 1장).
 *
 * ```
 * adb shell am broadcast -a com.galrae.app.MOCK --es c <명령> [--es a "<인자>"]
 * ```
 *
 * | 명령 | 인자 | 동작 |
 * |---|---|---|
 * | `reset` | — | 가상 멤버·제안을 처음 상태로 (녹화 다시 찍을 때) |
 * | `state` | — | 현재 상태를 로그로 (`adb logcat -s GALRAE/MockGroup`) |
 * | `propose-dest` | `[멤버:]장소` | 멤버가 목적지 변경 제안 (기본 언니네) |
 * | `meetup` | `[멤버:]장소` | 멤버가 중간에 모이기 제안 (기본 언니네) |
 * | `dest` | `멤버:장소` | 멤버가 혼자 목적지 변경 |
 * | `add-wp` | `멤버:장소` | 멤버가 경유지 추가 |
 * | `soc` | `멤버:퍼센트` | 멤버 배터리·연료 잔량 변경 (20% 이하면 충전/주유 필요) |
 * | `long-drive` | `멤버` | 해당 가상 차량을 2시간 연속 운전 상태로 설정 |
 * | `recommend` | `cafe/food/rest/charger/fuel` | 카카오 검색·추정 추천 (선택: trigger, member extras) |
 * | `my-dest` | `장소` | **내 차** 목적지 변경 (경유지 유지). RouteManager 확인용 |
 * | `my-wp` | `장소` | **내 차** 경유지 추가 (꽉 차면 교체 필요 로그) |
 * | `my-wp-replace` | `1|2:장소` | **내 차** 경유지 1·2 교체 |
 *
 * 멤버는 이름(언니네·부모님) 또는 id(sister·parents), 장소는 [MockScenario.demoPlaces]의 이름.
 * 예: `--es c propose-dest --es a "경포해수욕장"`, `--es c soc --es a "부모님:15"`
 */
class MockCommandReceiver(
    private val mock: MockGroupRepository,
    private val route: RouteManager,
    private val recommender: MeetupRecommender,
    private val onRecommendation: (MeetupResult?) -> Unit,
) : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var recommendationJob: kotlinx.coroutines.Job? = null

    fun register(context: Context) {
        // adb(shell)에서 보내야 하므로 exported. 대신 보내는 쪽에 DUMP 권한을 요구해
        // shell·시스템만 보낼 수 있게 한다 (일반 앱은 DUMP를 받을 수 없음)
        ContextCompat.registerReceiver(
            context,
            this,
            IntentFilter(ACTION),
            SENDER_PERMISSION,
            null,
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        val command = intent.getStringExtra("c").orEmpty()
        val arg = intent.getStringExtra("a").orEmpty()
        if (command == "recommend") {
            recommend(arg, intent.getStringExtra("trigger"), intent.getStringExtra("member"))
            return
        }
        Log.i(TAG, "command $command \"$arg\"")
        runCatching { handle(command, arg) }
            .onFailure { Log.e(TAG, "command failed: $command \"$arg\" — ${it.message}") }
    }

    /** 카카오 호출은 비동기로 수행한다. 실제 데이터 연결 전 내 차의 누락 좌표·목적지만 명시적 목업으로 채운다. */
    private fun recommend(typeName: String, triggerName: String?, memberName: String?) {
        if (recommendationJob?.isActive == true) {
            Log.i(TAG, "recommend: 이미 검색 중입니다")
            return
        }
        // 최대 9회의 네트워크 조회가 가능하므로 BroadcastReceiver의 제한 시간 밖에서 앱 작업으로 수행한다.
        recommendationJob = scope.launch {
            onRecommendation(null)
            try {
                val type = PlaceType.valueOf(typeName.uppercase())
                val trigger = triggerName?.let { ProposalTrigger.valueOf(it.uppercase()) }
                val member = memberName?.let(::memberId)
                val snapshot = MockScenario.recommendationMembers(mock.members.value, mock.myId)
                Log.i(TAG, "recommend: PoC 목업 위치 사용, 모든 ETA는 추정값입니다")
                val result = recommender.recommend(snapshot, type, trigger, member)
                onRecommendation(result)
                Log.i(TAG, "recommend requests=${result.requestCount} centers=${result.centers.size} to=${result.toId ?: "all"}")
                result.warnings.forEach { Log.w(TAG, "recommend: $it") }
                if (result.recommendations.isEmpty()) Log.i(TAG, "recommend: 후보 없음")
                result.recommendations.forEach { r ->
                    Log.i(TAG, "recommend ${r.reason} ${r.candidate.place.name} 추정 ${r.summary.allArriveMin}분 뒤 모두 도착 / 최대 대기 ${r.summary.maxWaitMin}분 / 시설·도달 검증 필요=${r.requiresFacilityVerification}")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "recommend failed: ${e.message}")
            }
        }
    }

    private fun handle(command: String, arg: String) {
        when (command) {
            "reset" -> mock.reset()
            "state" -> mock.logState()
            "long-drive" -> mock.setLongDrive(memberId(arg))
            "propose-dest" -> {
                val (member, place) = memberAndPlace(arg, defaultMember = MockScenario.SISTER_ID)
                mock.proposeFrom(member, ProposalKind.DESTINATION, place)
            }
            "meetup" -> {
                val (member, place) = memberAndPlace(arg, defaultMember = MockScenario.SISTER_ID)
                mock.proposeFrom(member, ProposalKind.MEETUP, place)
            }
            "dest" -> {
                val (member, place) = memberAndPlace(arg, defaultMember = null)
                mock.setDestination(member, place)
            }
            "add-wp" -> {
                val (member, place) = memberAndPlace(arg, defaultMember = null)
                mock.addWaypoint(member, place)
            }
            "soc" -> {
                val (name, value) = split(arg) ?: error("멤버:퍼센트 형식이어야 함")
                val percent = value.toIntOrNull()?.takeIf { it in 0..100 } ?: error("퍼센트는 0~100")
                mock.setEnergy(memberId(name), percent)
            }
            "my-dest" -> Log.i(TAG, "my-dest → ${route.setDestination(place(arg))}")
            "my-wp" -> Log.i(TAG, "my-wp → ${route.addWaypoint(place(arg))}")
            "my-wp-replace" -> {
                val (slot, name) = split(arg) ?: error("1|2:장소 형식이어야 함")
                val index = slot.toIntOrNull()?.minus(1) ?: error("슬롯은 1 또는 2")
                Log.i(TAG, "my-wp-replace → ${route.replaceWaypoint(index, place(name))}")
            }
            else -> error("모르는 명령")
        }
    }

    /** "멤버:장소" 또는 "장소"(기본 멤버). */
    private fun memberAndPlace(arg: String, defaultMember: String?): Pair<String, Place> {
        val parts = split(arg)
        val member = parts?.first?.let(::memberId) ?: defaultMember ?: error("멤버:장소 형식이어야 함")
        return member to place(parts?.second ?: arg)
    }

    private fun place(name: String): Place = MockScenario.findPlace(name)
        ?: error("모르는 장소 '$name' (가능: ${MockScenario.demoPlaces.joinToString { it.name }})")

    private fun split(arg: String): Pair<String, String>? {
        val i = arg.indexOf(':')
        return if (i < 0) null else arg.substring(0, i).trim() to arg.substring(i + 1).trim()
    }

    private fun memberId(nameOrId: String): String =
        mock.members.value.find { it.id == nameOrId || it.profile.name == nameOrId }?.id
            ?: error("모르는 멤버 '$nameOrId'")

    private companion object {
        const val TAG = "GALRAE/MockCmd"
        const val ACTION = "com.galrae.app.MOCK"
        const val SENDER_PERMISSION = "android.permission.DUMP"
    }
}
