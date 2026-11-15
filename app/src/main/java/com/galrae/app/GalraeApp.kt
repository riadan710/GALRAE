package com.galrae.app

import android.app.Application
import android.util.Log
import com.galrae.app.car.MyCar
import com.galrae.app.group.GroupRepository
import com.galrae.app.group.mock.MockCommandReceiver
import com.galrae.app.group.mock.MockGroupRepository
import com.galrae.app.group.mock.MockScenario
import com.galrae.app.group.model.ProposalKind
import com.galrae.app.group.model.ProposalTrigger
import com.galrae.app.meetup.AutoStopProposer
import com.galrae.app.meetup.KakaoPlaceSearch
import com.galrae.app.meetup.MeetupRecommender
import com.galrae.app.meetup.MeetupResult
import com.galrae.app.route.RouteManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 앱 전역 객체를 한 번만 만들어 들고 있는 곳.
 * 화면(Activity)이 다시 만들어져도 SDK 연결과 상태가 유지되게 Application에 둔다.
 */
class GalraeApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var myCar: MyCar
        private set

    /** 내 차 경로(목적지·경유지)를 바꿀 때는 이것만 쓴다 (docs 5-0). */
    lateinit var route: RouteManager
        private set

    /** 1차는 앱 안 가상 멤버. 시연용 adb 명령이 Mock 전용 조작을 쓸 수 있게 구현 타입으로도 들고 있는다. */
    lateinit var mockGroup: MockGroupRepository
        private set

    /** 화면·Flow 로직은 이것만 쓴다. Firebase로 바꿀 때 여기만 바꾼다. */
    val group: GroupRepository get() = mockGroup

    val meetupRecommender = MeetupRecommender(KakaoPlaceSearch(BuildConfig.KAKAO_REST_API_KEY)::search)
    private val _meetupResult = MutableStateFlow<MeetupResult?>(null)
    val meetupResult = _meetupResult.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        myCar = MyCar(this).also { it.start() }
        route = RouteManager(navi = myCar.nav, naviLoaded = myCar.naviLoaded, send = myCar::requestRoute)
        mockGroup = MockGroupRepository()
        MockCommandReceiver(mockGroup, route, meetupRecommender) { _meetupResult.value = it }.register(this)
        publishMyState()
        startAutoStopProposals()
    }

    private fun startAutoStopProposals() {
        val proposer = AutoStopProposer(
            group = group,
            recommend = { members, request ->
                meetupRecommender.recommend(
                    MockScenario.recommendationMembers(members, group.myId),
                    request.type, request.trigger, request.memberId,
                ).also { _meetupResult.value = it }
            },
            send = { request, recommendation ->
                val toId = if (request.trigger == ProposalTrigger.GAP) request.memberId else null
                val proposal = if (request.memberId == group.myId) {
                    group.sendProposal(
                        ProposalKind.AUTO_STOP, recommendation.candidate.place,
                        request.trigger, recommendation.summary,
                        toId,
                    )
                } else {
                    // shortcut: 한 앱에서 가상 차량의 감지·제안을 대신 실행, Firebase 연동 시 각 차량이 자기 상태만 처리
                    mockGroup.proposeFrom(
                        request.memberId, ProposalKind.AUTO_STOP, recommendation.candidate.place,
                        request.trigger, recommendation.summary,
                        toId,
                    )
                }
                Log.i("GALRAE/AutoStop", "created ${proposal.id} trigger=${proposal.trigger} from=${proposal.fromId} to=${proposal.toId ?: "all"} place=${proposal.place.name} summary=${proposal.summary}")
            },
            onFailure = { Log.w("GALRAE/AutoStop", it) },
        )
        appScope.launch { group.members.collect { proposer.check() } }
    }

    /** 내 차 상태가 바뀔 때마다 그룹에 올린다 (Firebase에선 members/{uid} 쓰기). */
    private fun publishMyState() = appScope.launch {
        // 내비 원본(myCar.nav)이 아니라 RouteManager.state: 경로를 보낸 직후의 "경로 없음" 공백을 임시 경로로 메운 값
        combine(route.state, myCar.energyLevel) { nav, energy -> nav to energy }
            .collect { (nav, energy) -> group.publishMyState(nav, energy) }
    }
}
