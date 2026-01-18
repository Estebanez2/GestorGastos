package com.example.gestorgastos.ui

import android.view.LayoutInflater
import android.view.View
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
    var mapaCategorias: Map<String, String?> = emptyMap(),
    private val onItemClicked: (Gasto) -> Unit
) : ListAdapter<Gasto, GastoAdapter.GastoViewHolder>(DiffCallback) {

    class GastoViewHolder(val binding: ItemGastoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GastoViewHolder {
        val binding = ItemGastoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GastoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GastoViewHolder, position: Int) {
        val gasto = getItem(position)

        holder.binding.tvNombre.text = gasto.nombre
        holder.binding.tvCantidad.text = Formato.formatearMoneda(gasto.cantidad)
        val dateFormat = SimpleDateFormat("dd MMM", Locale("es", "ES"))
        holder.binding.tvFecha.text = dateFormat.format(Date(gasto.fecha))

        // --- LÓGICA HÍBRIDA (FOTO vs ICONO) ---
        val uriCategoria = mapaCategorias[gasto.categoria]

        if (uriCategoria != null) {
            // TIENE FOTO: La mostramos tal cual (sin tinte)
            holder.binding.ivIconoCategoria.clearColorFilter()
            Glide.with(holder.itemView)
                .load(uriCategoria)
                .circleCrop()
                .into(holder.binding.ivIconoCategoria)
        } else {
            // NO TIENE FOTO: Usamos el icono del Helper (Hamburguesa, Coche...)
            val iconoRes = CategoriasHelper.obtenerIcono(gasto.categoria)
            holder.binding.ivIconoCategoria.setImageResource(iconoRes)

            // IMPORTANTE: Como quitaste el tint del XML, aquí lo forzamos a BLANCO
            // para que se vea bien sobre el fondo oscuro de la tarjeta.
            holder.binding.ivIconoCategoria.setColorFilter(android.graphics.Color.WHITE)
        }

        // ... (resto del código de la foto del ticket igual) ...
        if (gasto.uriFoto != null) {
            holder.binding.cardThumb.visibility = View.VISIBLE
            holder.binding.ivThumb.visibility = View.VISIBLE
            Glide.with(holder.itemView).load(gasto.uriFoto).centerCrop().into(holder.binding.ivThumb)
        } else {
            holder.binding.cardThumb.visibility = View.GONE
            holder.binding.ivThumb.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onItemClicked(gasto) }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Gasto>() {
            override fun areItemsTheSame(oldItem: Gasto, newItem: Gasto) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Gasto, newItem: Gasto) = oldItem == newItem
        }
    }
}