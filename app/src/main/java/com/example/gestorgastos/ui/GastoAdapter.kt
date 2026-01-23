package com.example.gestorgastos.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.gestorgastos.data.Gasto
import com.example.gestorgastos.databinding.ItemGastoBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GastoAdapter(
    private val onGastoClick: (Gasto) -> Unit
) : ListAdapter<Gasto, GastoAdapter.GastoViewHolder>(DiffCallback()) {

    // Mapa para saber qué foto tiene cada categoría ("Comida" -> "content://...")
    var mapaCategorias: Map<String, String?> = emptyMap()

    // --- MULTISELECCIÓN ---
    private var isSelectionMode = false
    private val selectedIds = mutableSetOf<Long>()

    var onSelectionChanged: ((Int) -> Unit)? = null

    fun salirModoSeleccion() {
        isSelectionMode = false
        selectedIds.clear()
        notifyDataSetChanged()
        onSelectionChanged?.invoke(0)
    }

    fun obtenerGastosSeleccionados(): List<Gasto> {
        return currentList.filter { selectedIds.contains(it.id) }
    }

    inner class GastoViewHolder(private val binding: ItemGastoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(gasto: Gasto) {
            val context = binding.root.context

            // 1. DATOS DE TEXTO
            binding.tvNombre.text = gasto.nombre
            binding.tvCantidad.text = Formato.formatearMoneda(gasto.cantidad)
            val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
            binding.tvFecha.text = sdf.format(Date(gasto.fecha))

            // 2. LÓGICA DE FOTO DE CATEGORÍA
            // Buscamos si esta categoría tiene foto personalizada en el mapa
            val uriFotoCategoria = mapaCategorias[gasto.categoria]

            if (uriFotoCategoria != null) {
                // A. Si tiene foto personalizada -> Usamos Glide
                Glide.with(context)
                    .load(uriFotoCategoria)
                    .circleCrop() // La recortamos en círculo para que quede bonito
                    .into(binding.ivIconoCategoria)

                // Clic para ver en grande (solo si no estamos seleccionando)
                binding.ivIconoCategoria.setOnClickListener {
                    if (!isSelectionMode) ImageZoomHelper.mostrarImagen(context, uriFotoCategoria)
                    else toggleSelection(gasto.id) // Si estamos seleccionando, el clic selecciona
                }
            } else {
                // B. Si no tiene foto -> Usamos Icono por defecto (CategoriasHelper)
                val iconoRes = CategoriasHelper.obtenerIcono(gasto.categoria)
                binding.ivIconoCategoria.setImageResource(iconoRes)
                binding.ivIconoCategoria.clearColorFilter()

                // Quitamos el clic de zoom para los iconos por defecto
                binding.ivIconoCategoria.setOnClickListener {
                    if (isSelectionMode) toggleSelection(gasto.id)
                }
            }

            // 3. LÓGICA DE FOTO DEL GASTO (Miniatura)
            if (gasto.uriFoto != null) {
                binding.cardThumb.visibility = android.view.View.VISIBLE
                binding.ivThumb.visibility = android.view.View.VISIBLE

                Glide.with(context)
                    .load(gasto.uriFoto)
                    .centerCrop()
                    .into(binding.ivThumb)

                // Clic para ver en grande
                binding.ivThumb.setOnClickListener {
                    if (!isSelectionMode) ImageZoomHelper.mostrarImagen(context, gasto.uriFoto)
                    else toggleSelection(gasto.id)
                }
            } else {
                binding.cardThumb.visibility = android.view.View.GONE
            }

            // 4. COLORES DE SELECCIÓN
            if (selectedIds.contains(gasto.id)) {
                binding.cardGasto.setCardBackgroundColor(Color.parseColor("#E0E0E0")) // Gris si seleccionado
            } else {
                binding.cardGasto.setCardBackgroundColor(Color.WHITE) // Blanco normal
            }

            // 5. CLIC EN LA TARJETA (Editar o Seleccionar)
            binding.root.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(gasto.id)
                } else {
                    onGastoClick(gasto)
                }
            }

            // 6. LONG CLICK (Iniciar selección)
            binding.root.setOnLongClickListener {
                if (!isSelectionMode) {
                    isSelectionMode = true
                    toggleSelection(gasto.id)
                    true
                } else {
                    false
                }
            }
        }

        private fun toggleSelection(id: Long) {
            if (selectedIds.contains(id)) {
                selectedIds.remove(id)
            } else {
                selectedIds.add(id)
            }

            if (selectedIds.isEmpty()) {
                isSelectionMode = false
            }

            notifyItemChanged(adapterPosition)
            onSelectionChanged?.invoke(selectedIds.size)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GastoViewHolder {
        val binding = ItemGastoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GastoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GastoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<Gasto>() {
        override fun areItemsTheSame(oldItem: Gasto, newItem: Gasto) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Gasto, newItem: Gasto) = oldItem == newItem
    }
}