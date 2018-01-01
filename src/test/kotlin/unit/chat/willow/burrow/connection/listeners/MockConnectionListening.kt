package unit.chat.willow.burrow.connection.listeners

import chat.willow.burrow.connection.listeners.IConnectionListening
import chat.willow.burrow.connection.listeners.preparing.IConnectionPreparing
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject

class MockConnectionListening(val preparing: IConnectionPreparing):
    IConnectionListening, IConnectionPreparing by preparing {

    override val read: Observable<IConnectionListening.Read>
    val readSubject = PublishSubject.create<IConnectionListening.Read>()

    override val accepted: Observable<IConnectionListening.Accepted>
    val acceptedSubject = PublishSubject.create<IConnectionListening.Accepted>()

    override val closed: Observable<IConnectionListening.Closed>
    val closedSubject = PublishSubject.create<IConnectionListening.Closed>()

    init {
        read = readSubject
        accepted = acceptedSubject
        closed = closedSubject
    }

    var didStart = false
    override fun start() {
        didStart = true
    }

    var didTearDown = false
    override fun tearDown() {
        didTearDown = true
    }
}