package io.github.vrcmteam.vrcm.network.websocket.data.type

object FriendEvents {

    /**
     * 朋友添加
     * 当用户接受了或同意了好友请求时，会发送此事件
     */
    data object FriendAdd : WebSocketEventType("friend-add",Unit::class)

    /**
     * 朋友删除
     * 当用户已被删除或把好友时，会发送此事件
     */
    data object FriendDelete : WebSocketEventType("friend-delete",Unit::class)

    /**
     * 朋友上线
     * 当用户的一位朋友在游戏中上线时，会发送此事件
     */
    data object FriendOnline : WebSocketEventType("friend-online",Unit::class)


    /**
     * 朋友活跃
     * 当用户的一位朋友在网站上处于活动状态时，会发送此事件
     */
    data object FriendActive : WebSocketEventType("friend-active",Unit::class)

    /**
     * 朋友离线
     * 当用户的一位朋友离线时，会发送此事件
     */
    data object FriendOffline : WebSocketEventType("friend-offline",Unit::class)

    /**
     * 朋友信息更新
     * 当用户的一位朋友的个人资料发生变化时，会发送此事件
     */
    data object FriendUpdate : WebSocketEventType("friend-update",Unit::class)

    /**
     * 朋友位置变化
     * 当用户的一位朋友更改实例时，会发送此事件
     */
    data object FriendLocation : WebSocketEventType("friend-location",Unit::class)
}