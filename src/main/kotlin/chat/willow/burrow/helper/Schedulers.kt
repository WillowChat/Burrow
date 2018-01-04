package chat.willow.burrow.helper

import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.Executors

class BurrowSchedulers {
    companion object {
        fun unsharedSingleThread(name: String? = null): Scheduler {
            val scheduler = Schedulers.from(Executors.newSingleThreadExecutor())

            if (name != null) {
                Observable.just(name)
                    .observeOn(scheduler)
                    .subscribe {
                        Thread.currentThread().name = it
                    }
            }

            return scheduler
        }
    }
}