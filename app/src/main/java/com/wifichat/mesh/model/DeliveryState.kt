package com.wifichat.mesh.model

/**
 * The four distinct points in a message's journey. The UI must only ever show
 * a state whose corresponding event has actually been observed on this device.
 *
 *  - ACCEPTED_LOCALLY : this device created/queued the packet.
 *  - TRANSMITTED      : bytes were handed to a directly connected neighbour.
 *  - DELIVERED_TO_DEVICE : the *final recipient device* returned an ack.
 *  - READ             : the recipient's app reported the message as read.
 *
 * A successful RFCOMM write is NOT proof of any of the latter three.
 */
enum class DeliveryState {
    PENDING,             // created, not yet queued
    QUEUED,              // in the store-and-forward queue, waiting for a route
    TRANSMITTED,         // sent to a neighbour (may still be multi-hop away)
    DELIVERED_TO_DEVICE, // destination device acknowledged receipt
    READ,                // recipient opened/read the message
    FAILED,              // expired or retries exhausted
    EXPIRED;             // past its expiry timestamp

    val isTerminal: Boolean
        get() = this == READ || this == FAILED || this == EXPIRED
}
