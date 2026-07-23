package com.github.chsiching.worddrill.data.local

import androidx.room.Embedded
import androidx.room.Relation
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.local.entity.Word

/**
 * 一个 word 及其全部 sense，用于"按词书查词条（含义项列表）"。
 * @Relation 通过 wordId 把 sense 子表挂回 word，无需手写 JOIN 拍平。
 */
data class WordWithSenses(
    @Embedded val word: Word,
    @Relation(
        parentColumn = "wordId",
        entityColumn = "wordId"
    )
    val senses: List<Sense>
)
