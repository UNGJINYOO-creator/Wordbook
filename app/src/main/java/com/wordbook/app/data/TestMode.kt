package com.wordbook.app.data

import android.content.Context
import java.io.File

/**
 * 테스트 모드 for the parent's phone: no password prompts, and 오늘의 낚시 / 원정 최종전 open any time without using a try.
 * Kept as marker files in noBackupFilesDir, so it never syncs to the family and never comes along in a backup:
 * the child's phone stays locked.
 */
object TestMode {
    private fun file(context: Context, name: String) = File(context.applicationContext.noBackupFilesDir, name)

    fun isOn(context: Context): Boolean = file(context, "test_mode").exists()

    fun set(context: Context, on: Boolean) {
        val f = file(context, "test_mode")
        if (on) runCatching { f.createNewFile() } else f.delete()
    }

    /** With test mode on, games are practice by default: fish, records, stamps and the giant are left untouched. */
    fun keepsRecords(context: Context): Boolean = file(context, "test_mode_keep").exists()

    fun setKeepsRecords(context: Context, keep: Boolean) {
        val f = file(context, "test_mode_keep")
        if (keep) runCatching { f.createNewFile() } else f.delete()
    }

    /** True when a game played now should not be saved. */
    fun practice(context: Context): Boolean = isOn(context) && !keepsRecords(context)
}
