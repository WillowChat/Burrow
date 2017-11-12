package chat.willow.burrow.unit

import chat.willow.burrow.ILineAccumulatorListener
import chat.willow.burrow.LineAccumulatorPool
import com.nhaarman.mockito_kotlin.mock
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class LineAccumulatorPoolTests {

    private lateinit var sut: LineAccumulatorPool

    @Before fun setUp() {
        val bufferSize = 1

        sut = LineAccumulatorPool(bufferSize)
    }

    @Test fun `pool always returns new accumulators for now`() {
        val listener: ILineAccumulatorListener = mock()

        val accumulatorOne = sut.next(id = 0, listener = listener)
        val accumulatorTwo = sut.next(id = 1, listener = listener)

        assertFalse(accumulatorOne === accumulatorTwo)
    }

}