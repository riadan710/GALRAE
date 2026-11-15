package com.galrae.app.group.model

import com.galrae.app.place.Place

/**
 * 멤버가 그룹에 보낸 제안 (docs 9-1 `proposals/{pid}`, 처리 흐름 9-2).
 *
 * 받는 앱은 시트·카드로 보여주고 [responses]에 응답을 남긴다.
 * 수락한 앱이 자기 내비에 직접 반영한다. 서버나 다른 차량이 대신 경로를 바꾸지 않는다.
 */
data class Proposal(
    val id: String,
    val kind: ProposalKind,
    /** [ProposalKind.AUTO_STOP]일 때 제안을 만든 원인. */
    val trigger: ProposalTrigger? = null,
    val fromId: String,
    /** null이면 모두에게. 앞·뒤차 간격 제안은 특정 멤버에게만 보낸다. */
    val toId: String? = null,
    val place: Place,
    /** 추천 결과 요약 ("28분 뒤 모두 도착 · 대기 최대 12분"). 목적지 제안에는 없음. */
    val summary: MeetSummary? = null,
    val createdAt: Long,
    /** 이 시각이 지나면 응답하지 않은 멤버는 거절로 본다. */
    val expiresAt: Long,
    val status: ProposalStatus = ProposalStatus.OPEN,
    /** 멤버 id → 응답. 제안자 본인은 처음부터 수락으로 들어간다. */
    val responses: Map<String, Answer> = emptyMap(),
) {
    val acceptedCount: Int get() = responses.count { it.value == Answer.ACCEPTED }
}

/** 제안 종류. 카운트다운 길이가 종류마다 다르다 (Figma: Flow 1 하단 시트 5초, Flow 2 상단 카드 15초). */
enum class ProposalKind(val countdownMs: Long) {
    /** Flow 1 목적지 변경 제안하기. */
    DESTINATION(5_000),

    /** Flow 1 중간에 모이기. */
    MEETUP(5_000),

    /** Flow 2 차량 상태(배터리·연료·장시간 운전·간격)로 앱이 자동으로 만든 경유지 제안. docs 9-1의 `flow2`. */
    AUTO_STOP(15_000),
}

enum class ProposalTrigger { LOW_EV, LOW_FUEL, LONG_DRIVE, GAP }

enum class ProposalStatus { OPEN, CANCELLED, REPLACED, DONE }

enum class Answer { ACCEPTED, DECLINED, PENDING }

data class MeetSummary(
    /** 지금부터 모든 차량이 도착할 때까지 걸리는 시간(분). */
    val allArriveMin: Int,
    /** 먼저 도착한 차량이 가장 오래 기다리는 시간(분). */
    val maxWaitMin: Int,
)
