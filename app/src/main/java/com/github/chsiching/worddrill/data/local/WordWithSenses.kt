package com.github.chsiching.worddrill.data.local

import androidx.room.Embedded
import androidx.room.Relation
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.local.entity.Word

data class WordWithSenses(
    @Embedded val word: Word,
    @Relation(
        parentColumn = "wordId",
        entityColumn = "wordId"
    )
    val senses: List<Sense>
)
