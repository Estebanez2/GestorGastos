package com.example.gestorgastos.ui

import android.net.Uri
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
        val context = holder.itemView.context

        holder.binding.tvNombre.text = gasto.nombre
        holder.binding.tvCantidad.text = Formato.formatearMoneda(gasto.cantidad)
        val dateFormat = SimpleDateFormat("dd MMM", Locale("es", "ES"))
        holder.binding.tvFecha.text = dateFormat.format(Date(gasto.fecha))

        // --- FUNCIÓN DE SEGURIDAD INTERNA ---
        fun esUriSegura(uri: String): Boolean {
            return try {
                context.contentResolver.openInputStream(Uri.parse(uri))?.close()
                true
            } catch (e: Exception) { false }
        }

        // --- 1. ICONO CATEGORÍA ---
        val uriCategoria = mapaCategorias[gasto.categoria]
        val iconoRes = CategoriasHelper.obtenerIcono(gasto.categoria)

        // Solo cargamos con Glide si existe la URI y es SEGURA
        if (uriCategoria != null && esUriSegura(uriCategoria)) {
            holder.binding.ivIconoCategoria.clearColorFilter()
            Glide.with(context).load(uriCategoria).circleCrop().into(holder.binding.ivIconoCategoria)

            holder.binding.ivIconoCategoria.setOnClickListener {
                ImageZoomHelper.mostrarImagen(context, uriCategoria)
            }
        } else {
            // Si no hay foto o está rota (permiso caducado) -> Icono por defecto
            holder.binding.ivIconoCategoria.setImageResource(iconoRes)
            holder.binding.ivIconoCategoria.setColorFilter(android.graphics.Color.WHITE)
            holder.binding.ivIconoCategoria.setOnClickListener(null)
        }

        // --- 2. FOTO TICKET ---
        if (gasto.uriFoto != null && esUriSegura(gasto.uriFoto)) {
            holder.binding.cardThumb.visibility = View.VISIBLE
            holder.binding.ivThumb.visibility = View.VISIBLE
            Glide.with(context).load(gasto.uriFoto).centerCrop().into(holder.binding.ivThumb)

            holder.binding.ivThumb.setOnClickListener {
                ImageZoomHelper.mostrarImagen(context, gasto.uriFoto)
            }
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