package pl.wolter.usos

import android.app.Application
import pl.wolter.usos.data.Repository
import pl.wolter.usos.work.Notifier
import pl.wolter.usos.work.SyncWorker

class UsosApp : Application() {
    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repo = Repository(this)
        Notifier.createChannels(this)
        SyncWorker.schedule(this)
    }

    companion object {
        lateinit var instance: UsosApp
            private set
        val repo: Repository get() = instance.repo
    }
}
