package unit.chat.willow.burrow.configuration

import chat.willow.kale.helper.INamed

fun networkName(name: String = "Burrow"): INamed {
    return object : INamed {
        override val name = name
    }
}