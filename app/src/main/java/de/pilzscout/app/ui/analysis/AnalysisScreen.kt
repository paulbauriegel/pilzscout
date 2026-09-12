package de.pilzscout.app.ui.analysis

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import de.pilzscout.app.R
import de.pilzscout.app.identify.Draft
import de.pilzscout.app.identify.DraftRepository
import de.pilzscout.app.identify.IdentifyUseCase
import de.pilzscout.app.ui.identify.slotShape
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AnalysisState {
    data class Running(val done: Int, val total: Int) : AnalysisState
    data class Done(val observationId: String) : AnalysisState
    data class Failed(val message: String) : AnalysisState
}

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val drafts: DraftRepository,
    private val identify: IdentifyUseCase,
) : ViewModel() {
    val draft: Draft = drafts.draft.value
    private val _state = MutableStateFlow<AnalysisState>(AnalysisState.Running(0, draft.photos.size))
    val state: StateFlow<AnalysisState> = _state
    private var started = false

    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            try {
                val id = identify.run(draft) { done, total -> _state.value = AnalysisState.Running(done, total) }
                drafts.clear()
                _state.value = AnalysisState.Done(id)
            } catch (e: Exception) {
                _state.value = AnalysisState.Failed(e.message ?: e.toString())
            }
        }
    }

    fun retry() {
        started = false
        _state.value = AnalysisState.Running(0, draft.photos.size)
        start()
    }
}

/** Photos converge into one loading indicator: the many-photos-to-one-result moment. */
@Composable
fun AnalysisScreen(onDone: (String) -> Unit, onBack: () -> Unit, viewModel: AnalysisViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.start() }
    LaunchedEffect(state) { (state as? AnalysisState.Done)?.let { onDone(it.observationId) } }
    val draft = viewModel.draft
    val n = draft.photos.size

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        val collapse by animateFloatAsState(if (state is AnalysisState.Running) 0.55f else 1f, label = "collapse")
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            draft.photos.forEachIndexed { i, photo ->
                val angle = (i.toFloat() / n) * 2 * Math.PI
                val radius = 70 * collapse
                AsyncImage(
                    model = photo.file,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .offset(x = (radius * Math.cos(angle)).dp, y = (radius * Math.sin(angle)).dp)
                        .size(84.dp)
                        .scale(0.9f)
                        .clip(photo.viewType.slotShape()),
                )
            }
            if (state is AnalysisState.Running) LoadingIndicator(modifier = Modifier.size(96.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.analysis_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        val withLocation = draft.includeLocation && draft.location != null
        Text(
            if (withLocation) pluralStringResource(R.plurals.analysis_combining, n, n) else pluralStringResource(R.plurals.analysis_combining_no_location, n, n),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        when (val s = state) {
            is AnalysisState.Running -> if (s.total > 1) Text(stringResource(R.string.analysis_progress, s.done.coerceAtLeast(1).coerceAtMost(s.total), s.total), style = MaterialTheme.typography.labelLarge)
            is AnalysisState.Failed -> {
                Text(stringResource(R.string.analysis_failed, s.message), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Row {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                    Button(onClick = viewModel::retry, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.analysis_retry)) }
                }
            }
            is AnalysisState.Done -> Unit
        }
        Spacer(Modifier.height(32.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.analysis_offline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
