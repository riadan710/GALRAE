package com.galrae.app

import android.app.Application
import com.galrae.app.car.MyCar

/**
 * 앱 전역 객체를 한 번만 만들어 들고 있는 곳.
 * 화면(Activity)이 다시 만들어져도 SDK 연결과 상태가 유지되게 Application에 둔다.
 */
class GalraeApp : Application() {

    lateinit var myCar: MyCar
        private set

    override fun onCreate() {
        super.onCreate()
        myCar = MyCar(this).also { it.start() }
    }
}
