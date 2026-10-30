package com.fredypot

import android.app.Application
import com.fredypot.data.BackupFiles
import com.fredypot.data.CycleRepository
import com.fredypot.data.db.CycleDatabase

class MoonCalendarApp : Application() {
    val repository: CycleRepository by lazy {
        CycleRepository(CycleDatabase.create(this).cycleDao())
    }

    val backupFiles: BackupFiles by lazy { BackupFiles(contentResolver) }
}
