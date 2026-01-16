package com.example.gestorgastos.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.gestorgastos.data.Gasto
import com.example.gestorgastos.databinding.ItemGastoBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Recibimos una función "onItemClicked" para saber qué hacer cuando el usuario toque un gasto
class GastoAdapter(private val onItemClicked: (Gasto) -> Unit) :
    ListAdapter<Gasto, GastoAdapter.GastoViewHolder>(DiffCallback) {

    // Esta clase interna maneja la "tarjeta" visual
    class GastoViewHolder(private val binding: ItemGastoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(gasto: Gasto, onItemClicked: (Gasto) -> Unit) {
            binding.tvNombre.text = gasto.nombre
            // Usamos nuestra nueva utilidad Formato
            binding.tvCantidad.text = Formato.formatearMoneda(gasto.cantidad)

            // Formateamos la fecha (De milisegundos a texto legible)
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            binding.tvFecha.text = sdf.format(Date(gasto.fecha))

            if (gasto.uriFoto != null) {
                // Si hay foto: la hacemos visible y la cargamos
                binding.ivThumb.visibility = android.view.View.VISIBLE
                // Quitamos el padding que pusimos por defecto para el icono
                binding.ivThumb.setPadding(0,0,0,0)

                com.bumptech.glide.Glide.with(binding.root.context)
                    .load(gasto.uriFoto) // Cargamos la ruta de la imagen
                    .centerCrop() // La recortamos para que llene el cuadrado
                    .into(binding.ivThumb) // La ponemos en la imagen
            } else {
                // Si no hay foto: ocultamos la imagen para que no ocupe espacio
                binding.ivThumb.visibility = android.view.View.GONE
            }

            // Configurar el click en toda la tarjeta
            binding.root.setOnClickListener {
                onItemClicked(gasto)
            }
        }
    }

    // Crea la "cáscara" visual vacía usando el archivo XML
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GastoViewHolder {
        val binding = ItemGastoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return GastoViewHolder(binding)
    }

    // Rellena la "cáscara" con los datos reales
    override fun onBindViewHolder(holder: GastoViewHolder, position: Int) {
        val gastoActual = getItem(position)
        holder.bind(gastoActual, onItemClicked)
    }

    // Objeto para optimizar la lista: Compara si los datos han cambiado para no recargar todo
    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Gasto>() {
            override fun areItemsTheSame(oldItem: Gasto, newItem: Gasto): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Gasto, newItem: Gasto): Boolean {
                return oldItem == newItem
            }
        }
    }
}