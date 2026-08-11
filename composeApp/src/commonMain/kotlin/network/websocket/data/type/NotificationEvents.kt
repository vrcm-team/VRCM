package io.github.vrcmteam.vrcm.network.websocket.data.type

object NotificationEvents {
    /**
     * 通知
     * 携带一个Notification对象，并被邀请、好友请求和其他游戏内通知使用
     */
    data object Notification: WebSocketEventType("notification",Unit::class)

    /** 新版通知；Boop、群组公告等当前收件箱事件会通过这条通道推送。 */
    data object NotificationV2: WebSocketEventType("notification-v2", Unit::class)

    /** 已存在的新版聚合通知发生更新，例如同一发送者再次 Boop。 */
    data object NotificationV2Update: WebSocketEventType("notification-v2-update", Unit::class)

    /**
     * 响应通知
     * 用于响应先前发送的事件
     */
    data object ResponseNotification: WebSocketEventType("response-notification",Unit::class)

    /**
     * 查看通知
     * 当客户端将特定通知标记为已看到时，将发送此事件
     */
    data object SeeNotification: WebSocketEventType("see-notification",Unit::class)

    /**
     * 隐藏通知
     * 当客户端隐藏通知时，将发送此事件
     */
    data object HideNotification : WebSocketEventType("hide-notification",Unit::class)

    /**
     * 清除通知
     * 当客户端清除所有通知时，将发送此事件
     */
    data object ClearNotification : WebSocketEventType("clear-notification",Unit::class)
}
