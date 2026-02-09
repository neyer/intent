package com.intentevolved.com.intentevolved

class IntentImpl(
    private val text: String,
    private val id: Long,
    internal val participantIds: MutableList<Long> = mutableListOf(),
    private val stateProvider: IntentStateProvider,
    private val createdTimestamp: Long? = null,
    private val lastUpdatedTimestamp: Long? = null,
    private val fields: MutableMap<String, FieldDetails> = mutableMapOf(),
    private val values: MutableMap<String, Any> = mutableMapOf(),
    private val isMeta: Boolean = true
) : Intent {
    override fun text() = text
    override fun id() = id
    override fun parent() = participantIds.firstOrNull()?.let { stateProvider.getById(it) }
    override fun participantIds(): List<Long> = participantIds.toList()
    override fun children(): List<Intent> = listOf()
    override fun createdTimestamp() = createdTimestamp
    override fun lastUpdatedTimestamp() = lastUpdatedTimestamp
    override fun fields(): Map<String, FieldDetails> = fields
    override fun fieldValues(): Map<String, Any> = values
    override fun isMeta() = isMeta

    internal fun addField(name: String, details: FieldDetails) {
        fields[name] = details
    }

    internal fun setFieldValue(name: String, value: Any) {
        values[name] = value
    }

    internal fun addParticipant(participantId: Long, index: Int? = null) {
        if (index != null && index in 0..participantIds.size) {
            participantIds.add(index, participantId)
        } else {
            participantIds.add(participantId)
        }
    }
}
