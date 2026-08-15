package online.xiaoxi.chathub.data

/**
 * 轻量内存缓存：导航重建（Tab/返回）时先显示缓存（瞬时），后台静默刷新。
 * 数据量小（会话/模型/服务商列表），进程内单例即可。
 */
object UiCache {
    @Volatile
    var conversations: List<ConversationDto>? = null

    @Volatile
    var enabledModels: List<ModelDto>? = null

    @Volatile
    var providers: List<ProviderDto>? = null
}
