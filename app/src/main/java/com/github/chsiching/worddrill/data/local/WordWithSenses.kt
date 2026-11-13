package com.github.chsiching.worddrill.data.local

import androidx.room.Embedded
import androidx.room.Relation
import com.github.chsiching.worddrill.data.local.entity.Sense
import com.github.chsiching.worddrill.data.local.entity.Word

/**
 * One word plus all its senses, used for "query word entries by book (with sense lists)".
 * @Relation attaches the sense child table back to the word via wordId, no hand-written JOIN flattening needed.
 */
data class WordWithSenses(
    @Embedded val word: Word,
    @Relation(
        parentColumn = "wordId",
        entityColumn = "wordId"
    )
    val senses: List<Sense>
)
