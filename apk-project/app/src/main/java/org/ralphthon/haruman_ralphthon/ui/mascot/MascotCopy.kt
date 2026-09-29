package org.ralphthon.haruman_ralphthon.ui.mascot

import org.ralphthon.haruman_ralphthon.data.AcornStage
import org.ralphthon.haruman_ralphthon.data.Desire

/** Friendly polite copy. Values are supplied from saved data, never invented by the character. */
object MascotCopy {
    const val ONBOARDING_MOA = "갖고 싶은 마음을 잠시 모아둘게요."
    const val ONBOARDING_TORI = "때가 되면 함께 다시 살펴봐요."
    const val ADD = "어떤 상품을 발견했나요?"
    const val SHARED_LINK = "링크 받았어요! 함께 살펴볼게요."
    const val DESIRE_QUESTION = "얼마나 갖고 싶나요?"
    const val DUPLICATE_LINK = "이미 보관함에 담아둔 상품이에요."
    const val UNSUPPORTED_SHARE = "상품 링크를 보내 주세요."
    const val SAVED = "받았어요! 보관함에 넣어뒀어요."
    const val BUY = "사기로 결정했어요. 원래 보던 곳에서 확인해 주세요."
    const val SKIP_UNKNOWN_PRICE = "마음을 한 번 더 확인했어요."
    const val UNDO = "다시 꺼내왔어요. 천천히 골라 주세요."
    const val REVIEW_DONE = "오늘의 정리를 마쳤어요!"
    const val REVIEW_NO_SKIP = "한 번 더 살펴봤어요. 내 속도로 결정해요."
    const val FOREST_EMPTY = "보내기로 한 마음이 나무로 자라요."

    private val lines = mapOf(
        "vault_empty" to listOf("갖고 싶은 게 생기면 불러 주세요.", "잠시 쉬고 있어요. 천천히 둘러봐요."),
        "vault_waiting" to listOf("마음을 차곡차곡 모아두고 있어요.", "잠시 담아두고, 나중에 다시 봐요."),
        "vault_stuffed" to listOf("많이 모였어요. 함께 정리해 볼까요?", "토리와 하나씩 다시 살펴봐요."),
        "moa_tap" to listOf("헤헤, 간지러워요!", "오늘은 어떤 상품을 발견했나요?", "마음에 든 상품을 잘 모아둘게요."),
        "tori_tap" to listOf("도토리를 하나씩 세고 있어요.", "다시 볼 때까지 잘 보관할게요.", "우리 숲을 천천히 가꿔봐요.")
    )

    /** Call with a screen-entry seed; recomposition with the same seed is deterministic. */
    fun pick(key: String, seed: Long = 0L): String {
        val options = lines[key] ?: return SAVED
        return options[Math.floorMod(seed, options.size.toLong()).toInt()]
    }

    fun vault(pendingCount: Int, seed: Long = 0L): String = when {
        pendingCount <= 0 -> pick("vault_empty", seed)
        pendingCount < 5 -> "지금 ${pendingCount}개를 모아뒀어요."
        else -> pick("vault_stuffed", seed)
    }

    fun ready(count: Int): String = "${count.coerceAtLeast(0)}개를 다시 볼 시간이에요."

    fun saved(time: String? = null): String =
        if (time.isNullOrBlank()) SAVED else "받았어요! $time 다시 살펴봐요."

    fun review(desire: Desire?, date: String): String = when (desire) {
        Desire.FIRE -> "그때는 많이 갖고 싶었어요. 지금은 어떤가요?"
        Desire.MEH -> "그때도 조금 고민했어요. 지금은 어떤가요?"
        else -> "${date}에 담아뒀어요. 지금도 갖고 싶나요?"
    }

    fun detail(stage: AcornStage, time: String): String = when (stage) {
        AcornStage.BURIED -> "잘 묻어뒀어요. $time 뒤에 다시 봐요."
        AcornStage.SPROUT -> "새싹이 났어요. 조금만 더 기다려봐요."
        AcornStage.READY -> "다 자랐어요. 함께 꺼내볼까요?"
    }

    fun skipped(price: String?): String = if (price.isNullOrBlank()) SKIP_UNKNOWN_PRICE
        else "${price}를 쓰지 않기로 했어요."

    fun result(skipped: Boolean, price: String? = null): String =
        if (skipped) if (price.isNullOrBlank()) "한 번 더 생각한 마음이 나무가 됐어요."
        else "쓰지 않기로 한 마음이 나무가 됐어요." else BUY

    fun extended(time: String): String = "$time 더 생각해 봐요."
    fun forest(count: Int): String = if (count <= 0) FOREST_EMPTY else "이번 달 숲에 나무 ${count}그루가 자랐어요."
    fun reviewSkipped(count: Int): String = "나무 ${count.coerceAtLeast(0)}그루를 심었어요. 숲에서 만나요."
}
