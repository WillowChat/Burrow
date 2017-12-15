package chat.willow.burrow.state

interface IChannelMessagesUseCase {

    fun track(client: ClientTracker.ConnectedClient)

}

class ChannelMessagesUseCase: IChannelMessagesUseCase {

    override fun track(client: ClientTracker.ConnectedClient) {
        TODO("not implemented")
    }

}