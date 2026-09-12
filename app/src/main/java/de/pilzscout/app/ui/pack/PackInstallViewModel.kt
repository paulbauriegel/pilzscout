package de.pilzscout.app.ui.pack

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.pilzscout.app.pack.PackInstallWorker
import de.pilzscout.app.pack.PackRepository
import de.pilzscout.app.pack.PackState
import de.pilzscout.core.model.PackComponent
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PackInstallViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val packRepository: PackRepository,
) : ViewModel() {

    val state: StateFlow<PackState> = packRepository.state

    /** Installs every bundled component. The worker keeps the process alive; the repository does the copying. */
    fun installAll() {
        val request = OneTimeWorkRequestBuilder<PackInstallWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(PackInstallWorker.UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
        packRepository.installAllBundled()
    }

    fun install(component: PackComponent) = packRepository.install(listOf(component))

    fun uninstall(component: PackComponent) = packRepository.uninstall(component)
}
