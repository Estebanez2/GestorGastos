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

        // ... (código de textos nombre, cantidad, fecha igual) ...
        holder.binding.tvNombre.text = gasto.nombre
        holder.binding.tvCantidad.text = Formato.formatearMoneda(gasto.cantidad)
        val dateFormat = SimpleDateFormat("dd MMM", Locale("es", "ES"))
        holder.binding.tvFecha.text = dateFormat.format(Date(gasto.fecha))

        // --- 1. ICONO CATEGORÍA (Con Zoom) ---
        val uriCategoria = mapaCategorias[gasto.categoria]

        if (uriCategoria != null) {
            // FOTO PERSONALIZADA
            holder.binding.ivIconoCategoria.clearColorFilter()
            Glide.with(holder.itemView).load(uriCategoria).circleCrop().into(holder.binding.ivIconoCategoria)

            // CLICK PARA ZOOM (Nuevo)
            holder.binding.ivIconoCategoria.setOnClickListener {
                ImageZoomHelper.mostrarImagen(holder.itemView.context, uriCategoria)
            }
        } else {
            // ICONO POR DEFECTO (Sin zoom, o zoom deshabilitado)
            val iconoRes = CategoriasHelper.obtenerIcono(gasto.categoria)
            holder.binding.ivIconoCategoria.setImageResource(iconoRes)
            holder.binding.ivIconoCategoria.setColorFilter(android.graphics.Color.WHITE)

            // Deshabilitamos click para que funcione el click de la fila (editar)
            holder.binding.ivIconoCategoria.setOnClickListener(null)
        }

        // --- 2. FOTO TICKET (Con Zoom) ---
        if (gasto.uriFoto != null) {
            holder.binding.cardThumb.visibility = View.VISIBLE
            holder.binding.ivThumb.visibility = View.VISIBLE
            Glide.with(holder.itemView).load(gasto.uriFoto).centerCrop().into(holder.binding.ivThumb)

            // CLICK PARA ZOOM (Nuevo)
            holder.binding.ivThumb.setOnClickListener {
                ImageZoomHelper.mostrarImagen(holder.itemView.context, gasto.uriFoto)
            }
        } else {
            holder.binding.cardThumb.visibility = View.GONE
            holder.binding.ivThumb.visibility = View.GONE
        }

        // Click en el resto de la tarjeta -> Editar Gasto
        holder.itemView.setOnClickListener { onItemClicked(gasto) }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Gasto>() {
            override fun areItemsTheSame(oldItem: Gasto, newItem: Gasto) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Gasto, newItem: Gasto) = oldItem == newItem
        }
    }
}