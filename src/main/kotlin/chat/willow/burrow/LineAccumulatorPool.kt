package chat.willow.burrow

interface ILineAccumulatorPool {

    fun next(id: ClientId, listener: ILineAccumulatorListener): ILineAccumulator

}

class LineAccumulatorPool(private val bufferSize: Int): ILineAccumulatorPool {

    override fun next(id: ClientId, listener: ILineAccumulatorListener): ILineAccumulator {
        // TODO: actually recycle accumulators
        return LineAccumulator(bufferSize = bufferSize, listener = listener, connectionId = id)
    }

}