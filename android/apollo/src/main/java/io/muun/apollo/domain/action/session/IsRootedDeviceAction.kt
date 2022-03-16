package io.muun.apollo.domain.action.session

import android.content.Context
import com.scottyab.rootbeer.RootBeer
import io.muun.apollo.domain.action.base.BaseAsyncAction0
import rx.Observable
import javax.inject.Inject

class IsRootedDeviceAction @Inject constructor(
    private val context: Context
) : BaseAsyncAction0<Boolean>() {

    override fun action(): Observable<Boolean> {
        return Observable.defer {
            Observable.just(RootBeer(context).isRooted)
        }
    }
}