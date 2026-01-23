package com.example.gestorgastos.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.gestorgastos.databinding.ItemConflictoBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConflictosAdapter : ListAdapter<DataTransferManager.ConflictoGasto, ConflictosAdapter.ViewHolder>(DiffCallback()) {

    // Set para guardar las posiciones seleccionadas
    private val selectedItems = mutableSetOf<Int>()

    inner class ViewHolder(private val binding: ItemConflictoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DataTransferManager.ConflictoGasto, position: Int) {
            val gasto = item.nuevoImportado // Mostramos los datos del nuevo (son iguales al viejo)

            binding.tvNombreConflicto.text = gasto.nombre
            binding.tvCantidadConflicto.text = Formato.formatearMoneda(gasto.cantidad)
            binding.tvCategoriaConflicto.text = gasto.categoria

            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            binding.tvFechaConflicto.text = sdf.format(Date(gasto.fecha))

            // Gestión del Checkbox
            binding.cbSeleccion.setOnCheckedChangeListener(null) // Evitar rebotes al reciclar
            binding.cbSeleccion.isChecked = selectedItems.contains(position)

            binding.cbSeleccion.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selectedItems.add(position)
                else selectedItems.remove(position)
            }

            // Clic en toda la tarjeta también marca el check
            binding.root.setOnClickListener {
                binding.cbSeleccion.isChecked = !binding.cbSeleccion.isChecked
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemConflictoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    // --- FUNCIONES DE SELECCIÓN ---

    fun seleccionarTodo() {
        for (i in 0 until itemCount) selectedItems.add(i)
        notifyDataSetChanged()
    }

    fun deseleccionarTodo() {
        selectedItems.clear()
        notifyDataSetChanged()
    }

    fun obtenerSeleccionados(): List<DataTransferManager.ConflictoGasto> {
        return currentList.filterIndexed { index, _ -> selectedItems.contains(index) }
    }

    // Clase para diferenciar items
    class DiffCallback : DiffUtil.ItemCallback<DataTransferManager.ConflictoGasto>() {
        override fun areItemsTheSame(oldItem: DataTransferManager.ConflictoGasto, newItem: DataTransferManager.ConflictoGasto) =
            oldItem.existente.id == newItem.existente.id
        override fun areContentsTheSame(oldItem: DataTransferManager.ConflictoGasto, newItem: DataTransferManager.ConflictoGasto) =
            oldItem == newItem
    }
}