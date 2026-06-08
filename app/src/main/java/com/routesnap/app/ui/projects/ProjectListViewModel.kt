package com.routesnap.app.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.routesnap.app.data.repository.TripRepository
import com.routesnap.app.domain.model.RenderStatus
import com.routesnap.app.domain.model.TripManifest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ProjectListViewModel
    @Inject
    constructor(
        private val tripRepository: TripRepository,
    ) : ViewModel() {
        val trips: StateFlow<List<TripManifest>> =
            tripRepository
                .getAllTrips()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        fun deleteTrip(tripId: String) {
            viewModelScope.launch {
                tripRepository.deleteTrip(tripId)
            }
        }
    }
