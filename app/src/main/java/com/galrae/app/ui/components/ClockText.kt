package com.galrae.app.ui.components

import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * 도착 시각 같은 시:분 표기. 기기 언어 순서를 따른다 ("오후 3:40" / "3:40 PM").
 * `h:mm a` 고정 패턴은 한국어에서 "3:40 오후"처럼 순서가 뒤집혀서 쓰지 않는다.
 */
fun formatClock(epochMs: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(epochMs))
