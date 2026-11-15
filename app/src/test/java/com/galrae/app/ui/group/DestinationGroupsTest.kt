package com.galrae.app.ui.group

import com.galrae.app.car.navi.NavState
import com.galrae.app.group.model.Member
import com.galrae.app.group.model.Profile
import com.galrae.app.place.Place
import org.junit.Assert.assertEquals
import org.junit.Test

class DestinationGroupsTest {

    private val gyeongpo = Place("경포해수욕장", lat = 37.805, lon = 128.908)
    private val station = Place("강릉역", lat = 37.764, lon = 128.899)
    private val noodle = Place("강릉순두부장칼국수", lat = 37.790, lon = 128.914)

    private fun member(id: String, destination: Place?) =
        Member(id, Profile(id, id.take(1), 0xFF000000), nav = NavState(destination = destination))

    @Test fun `my group comes first with me at the top`() {
        val groups = groupByDestination(
            listOf(member("sister", noodle), member("parents", gyeongpo), member("me", gyeongpo)), myId = "me",
        )
        assertEquals(listOf(gyeongpo, noodle), groups.map { it.destination })
        assertEquals(listOf("me", "parents"), groups[0].members.map { it.id })
        assertEquals(listOf(true, false), groups.map { it.isMine })
    }

    @Test fun `same place with a different name object is one group`() {
        val groups = groupByDestination(
            listOf(member("me", gyeongpo), member("parents", gyeongpo.copy(address = "강원 강릉시"))), myId = "me",
        )
        assertEquals(1, groups.size)
        assertEquals(2, groups[0].members.size)
    }

    @Test fun `bigger groups first and unset destination last`() {
        val groups = groupByDestination(
            listOf(
                member("me", station), member("a", null), member("b", noodle), member("c", gyeongpo), member("d", gyeongpo),
            ),
            myId = "me",
        )
        assertEquals(listOf(station, gyeongpo, noodle, null), groups.map { it.destination })
    }
}
