package com.nuvio.tv.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.data.mediaserver.ServerCatalog
import com.nuvio.tv.data.mediaserver.ServerItemRef
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.mergeCatalogPage
import com.nuvio.tv.domain.model.nextCatalogSkip
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ServerCollectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serverCatalog: ServerCatalog
) : ViewModel() {
    private val ref = ServerItemRef.parse(savedStateHandle.get<String>("itemId"))
    private val _row = MutableStateFlow<CatalogRow?>(null)
    private val _failed = MutableStateFlow(false)
    val row: StateFlow<CatalogRow?> = _row.asStateFlow()
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    init {
        viewModelScope.launch {
            _row.value = ref?.let { loadOrNull { serverCatalog.collectionRow(it) } }
            _failed.value = _row.value == null
        }
    }

    fun loadMore() {
        val current = _row.value ?: return
        if (current.isLoading || !current.hasMore) return
        _row.value = current.copy(isLoading = true)
        viewModelScope.launch {
            val page = loadOrNull { serverCatalog.page(current, current.nextCatalogSkip()) }
            _row.update { row ->
                row ?: return@update null
                page?.let(row::mergeCatalogPage)?.copy(isLoading = false) ?: row.copy(isLoading = false, hasMore = false)
            }
        }
    }

    private suspend fun loadOrNull(block: suspend () -> CatalogRow): CatalogRow? =
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
}
