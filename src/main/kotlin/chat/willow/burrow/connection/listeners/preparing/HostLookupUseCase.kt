package chat.willow.burrow.connection.listeners.preparing

import chat.willow.burrow.helper.loggerFor
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.schedulers.Schedulers
import java.net.InetAddress
import java.util.concurrent.TimeUnit

interface IHostLookupUseCase {

    fun lookUp(address: InetAddress, default: String): Observable<String>

}

class HostLookupUseCase(val timerScheduler: Scheduler = Schedulers.io()): IHostLookupUseCase {

    private val LOGGER = loggerFor<HostLookupUseCase>()

    private val HOSTNAME_LOOKUP_TIMEOUT_SECONDS: Long = 10

    override fun lookUp(address: InetAddress, default: String): Observable<String> {
        LOGGER.info("Looking up hostname: $address")
        val hostname = tryResolveHostname(address)

        return hostname
            .timeout(HOSTNAME_LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS, timerScheduler)
            .doOnError { LOGGER.warn("Hostname lookup failed: $address") }
            .onErrorReturnItem(default)
    }

    private fun tryResolveHostname(address: InetAddress): Observable<String> {
        return Observable.fromCallable {
            canonicalHostName(address)
        }
    }

    private fun canonicalHostName(address: InetAddress): String {
        return address.canonicalHostName
    }

}