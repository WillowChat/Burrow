package chat.willow.burrow

import chat.willow.burrow.connection.ConnectionId

interface ILineAccumulatorPool {

    fun next(id: ConnectionId, listener: ILineAccumulatorListener): ILineAccumulator

}

class LineAccumulatorPool(private val bufferSize: Int): ILineAccumulatorPool {

    override fun next(id: ConnectionId, listener: ILineAccumulatorListener): ILineAccumulator {
        // TODO: actually recycle accumulators
        return LineAccumulator(bufferSize = bufferSize, listener = listener, connectionId = id)
    }

}