package com.playstudio.aiteacher.history

import android.content.Context

object DatabaseProvider {
    lateinit var database: InMemoryDatabase
        private set

    fun init(context: Context) {
        database = InMemoryDatabase.getDatabase(context)
    }
}