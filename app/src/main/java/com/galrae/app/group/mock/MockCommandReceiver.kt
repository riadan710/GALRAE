package com.galrae.app.group.mock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.galrae.app.group.model.ProposalKind
import com.galrae.app.place.Place

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
 *
 * 멤버는 이름(언니네·부모님) 또는 id(sister·parents), 장소는 [MockScenario.demoPlaces]의 이름.
 * 예: `--es c propose-dest --es a "경포해수욕장"`, `--es c soc --es a "부모님:15"`
 */
class MockCommandReceiver(private val mock: MockGroupRepository) : BroadcastReceiver() {

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
        Log.i(TAG, "command $command \"$arg\"")
        runCatching { handle(command, arg) }
            .onFailure { Log.e(TAG, "command failed: $command \"$arg\" — ${it.message}") }
    }

    private fun handle(command: String, arg: String) {
        when (command) {
            "reset" -> mock.reset()
            "state" -> mock.logState()
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
            else -> error("모르는 명령")
        }
    }

    /** "멤버:장소" 또는 "장소"(기본 멤버). */
    private fun memberAndPlace(arg: String, defaultMember: String?): Pair<String, Place> {
        val parts = split(arg)
        val member = parts?.first?.let(::memberId) ?: defaultMember ?: error("멤버:장소 형식이어야 함")
        val placeName = parts?.second ?: arg
        val place = MockScenario.findPlace(placeName)
            ?: error("모르는 장소 '$placeName' (가능: ${MockScenario.demoPlaces.joinToString { it.name }})")
        return member to place
    }

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
