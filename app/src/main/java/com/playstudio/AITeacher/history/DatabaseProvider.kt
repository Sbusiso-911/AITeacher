package com.playstudio.aiteacher.history

import android.content.Context

object DatabaseProvider {
    lateinit var database: AppDatabase
        private set

    fun init(context: Context) {
        database = AppDatabase.getInstance(context)
    }
}