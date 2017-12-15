package chat.willow.burrow.unit.state

import chat.willow.burrow.state.ChannelMessagesUseCase
import org.junit.Before

class ChannelMessagesUseCaseTests {

    lateinit var sut: ChannelMessagesUseCase

    @Before fun setUp() {
        sut = ChannelMessagesUseCase()
    }



}