package chat.willow.burrow.connection.listeners.preparing

import chat.willow.burrow.helper.loggerFor
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.schedulers.Schedulers
import java.net.InetAddress
import java.util.concurrent.TimeUnit

interface IHostLookupUseCase {

    fun lookUp(address: InetAddress): Observable<String>

}

class HostLookupUseCase(val lookupScheduler: Scheduler = Schedulers.io(), val timerScheduler: Scheduler = Schedulers.io()): IHostLookupUseCase {

    private val LOGGER = loggerFor<HostLookupUseCase>()

    private val HOSTNAME_LOOKUP_TIMEOUT_SECONDS: Long = 10

    object ForwardLookupNotFound: RuntimeException()

    override fun lookUp(address: InetAddress): Observable<String> {
        val hostname = reverseLookupHostname(address)

        val hostnameChecks = hostname
            .doOnError { LOGGER.warn("Hostname reverse lookup failed: $address") }
            .flatMap(
                { forwardLookupHostnameMatches(it, address) },
                { it: String, validated: Boolean -> it to validated })
            .flatMap {
                if (it.second) {
                    LOGGER.info("Forward lookup verified hostname ${address.hostAddress} maps to ${it.first}")
                    Observable.just(it)
                } else {
                    LOGGER.warn("Forward lookup mismatch $address ${it.first}")
                    Observable.error(ForwardLookupNotFound)
                }
            }

        return hostnameChecks
            .subscribeOn(lookupScheduler)
            .map { it.first }
            .timeout(HOSTNAME_LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS, timerScheduler)
    }

    private fun reverseLookupHostname(address: InetAddress): Observable<String> {
        return Observable.fromCallable {
            LOGGER.info("Looking up hostname: $address")
            canonicalHostName(address)
        }
    }

    private fun forwardLookupHostnameMatches(host: String, original: InetAddress): Observable<Boolean> {
        return Observable.fromCallable {
            val addresses = InetAddress.getAllByName(host).map { it.address }
            addresses.any { it.contentEquals(original.address) }
        }
    }

    private fun canonicalHostName(address: InetAddress): String {
        return address.canonicalHostName
    }

}