package com.example.gestorgastos.ui

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.gestorgastos.data.Gasto
import com.example.gestorgastos.databinding.ActivityMainBinding

/**
 * Clase encargada de configurar los RecyclerViews, Adaptadores y Gestos (Swipe).
 * Mantiene el MainActivity limpio y ordenado.
 */
class MainViewConfigurator(
    private val binding: ActivityMainBinding,
    private val context: Context,
    private val uiManager: UIManager // Necesario para consultar el bloqueo de la UI
) {

    lateinit var adapterLista: GastoAdapter
    lateinit var adapterGastosCategoria: GastoAdapter
    lateinit var adapterGastosCalendario: GastoAdapter
    lateinit var adapterGastosGrafica: GastoAdapter

    fun setupRecyclerViews(
        onEditGasto: (Gasto) -> Unit,
        onSelectionChanged: (Int) -> Unit,
        onSwipeDelete: (Gasto, GastoAdapter, Int) -> Unit
    ) {
        // 1. LISTA PRINCIPAL
        adapterLista = GastoAdapter(onEditGasto).apply { this.onSelectionChanged = onSelectionChanged }
        binding.rvGastos.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = adapterLista
        }
        configurarSwipe(binding.rvGastos, adapterLista, onSwipeDelete)

        // 2. DETALLE CATEGORÍAS (QUESITOS)
        adapterGastosCategoria = GastoAdapter(onEditGasto).apply { this.onSelectionChanged = onSelectionChanged }
        binding.rvGastosCategoria.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = adapterGastosCategoria
        }
        configurarSwipe(binding.rvGastosCategoria, adapterGastosCategoria, onSwipeDelete)

        // 3. DETALLE CALENDARIO (NUEVO)
        adapterGastosCalendario = GastoAdapter(onEditGasto).apply { this.onSelectionChanged = onSelectionChanged }
        binding.rvGastosCalendario.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = adapterGastosCalendario
        }
        configurarSwipe(binding.rvGastosCalendario, adapterGastosCalendario, onSwipeDelete)

        // 4. DETALLE GRÁFICA BARRAS (NUEVO)
        adapterGastosGrafica = GastoAdapter(onEditGasto).apply { this.onSelectionChanged = onSelectionChanged }
        binding.rvGastosGrafica.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = adapterGastosGrafica
        }
        configurarSwipe(binding.rvGastosGrafica, adapterGastosGrafica, onSwipeDelete)

        // 5. CALENDARIO GRID (LayoutManager)
        binding.rvCalendario.layoutManager = GridLayoutManager(context, 7)
    }

    // --- LÓGICA DE GESTOS (SWIPE) ---
    private fun configurarSwipe(
        recyclerView: RecyclerView,
        adapter: GastoAdapter,
        onDelete: (Gasto, GastoAdapter, Int) -> Unit
    ) {
        // Permitimos deslizar a IZQUIERDA o DERECHA
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                // BLOQUEO DE SEGURIDAD: Si hay selección múltiple activa, CONGELAMOS la lista (return 0)
                if (uiManager.estaBarraSeleccionVisible()) return 0

                // Si no, permitimos movimiento libre
                return makeMovementFlags(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
            }

            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val gasto = adapter.currentList[position]
                    onDelete(gasto, adapter, position)
                }
            }
        }

        ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerView)
    }
}