package org.commcare.app.model

data class Opportunity(
    val id: String,
    val name: String,
    val organization: String,
    val description: String,
    val shortDescription: String,
    val isActive: Boolean,
    val currency: String,
    val maxPayPerVisit: String?,
    val totalBudget: String?,
    val endDate: String?,
    val learnAppId: String?,
    val deliverAppId: String?,
    val claimed: Boolean = false,
    val learnProgress: Int = 0,
    val deliveryProgress: Int = 0
)

data class LearnModule(
    val id: String,
    val name: String,
    val description: String,
    val completionStatus: String  // "not_started", "in_progress", "completed"
)

data class DeliveryStatus(
    val totalDeliveries: Int,
    val completedDeliveries: Int,
    val pendingDeliveries: Int,
    val approvedDeliveries: Int
)

data class PaymentInfo(
    val id: String,
    val amount: String,
    val currency: String,
    val status: String,  // "pending", "approved", "paid"
    val date: String
)

data class MessageThread(
    val id: String,
    val participantName: String,
    val lastMessage: String,
    val lastMessageDate: String,
    val unreadCount: Int
)

data class Message(
    val id: String,
    val threadId: String,
    val senderName: String,
    val content: String,
    val timestamp: String,
    val isFromMe: Boolean
)
