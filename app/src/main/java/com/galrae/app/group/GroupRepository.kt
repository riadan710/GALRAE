package com.galrae.app.group

import com.galrae.app.car.energy.EnergyLevel
import com.galrae.app.car.navi.NavState
import com.galrae.app.group.model.TextMessage
import com.galrae.app.group.model.Answer
import com.galrae.app.group.model.MeetSummary
import com.galrae.app.group.model.Member
import com.galrae.app.group.model.MemberStatus
import com.galrae.app.group.model.Proposal
import com.galrae.app.group.model.ProposalKind
import com.galrae.app.group.model.ProposalTrigger
import com.galrae.app.place.Place
import kotlinx.coroutines.flow.StateFlow

/**
 * 그룹(방) 데이터의 단일 창구. 화면과 Flow 로직은 이 인터페이스만 쓴다.
 *
 * - 1차: [com.galrae.app.group.mock.MockGroupRepository] (앱 안 가상 멤버)
 * - 2차 PT: FirebaseGroupRepository (Realtime DB, docs 9-1)로 교체
 */
interface GroupRepository {

    /** 내 멤버 id. [members]에서 내 차를 찾을 때 쓴다. */
    val myId: String

    /** 나를 포함한 그룹 멤버 전체. */
    val members: StateFlow<List<Member>>

    /** 진행 중인 제안 (내가 보낸 것, 받은 것 모두). 만료 판단은 [Proposal.expiresAt]으로 각 화면이 한다. */
    val proposals: StateFlow<List<Proposal>>

    val messages: StateFlow<List<TextMessage>>

    fun sendText(text: String, toId: String? = null): TextMessage

    /** 내 차 상태를 그룹에 올린다. 내 차 상태가 바뀔 때마다 부른다. */
    fun publishMyState(nav: NavState, energy: EnergyLevel?, status: MemberStatus? = null)

    /** 그룹에 제안을 보낸다. 같은 사람이 보낸 이전 진행 중 제안은 대체된다 (docs 9-1). */
    fun sendProposal(
        kind: ProposalKind,
        place: Place,
        trigger: ProposalTrigger? = null,
        summary: MeetSummary? = null,
        toId: String? = null,
    ): Proposal

    /** 받은 제안에 응답한다. */
    fun respond(proposalId: String, answer: Answer)
}
