package com.example.gestorgastos.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.gestorgastos.data.Gasto
import com.example.gestorgastos.databinding.ItemDiaCalendarioBinding
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class CalendarioAdapter(
    private val mes: YearMonth,
    private val gastos: List<Gasto>
) : RecyclerView.Adapter<CalendarioAdapter.DiaViewHolder>() {

    // Calculamos qué día de la semana cae el 1 (Lunes=1, ... Domingo=7)
    // Restamos 1 porque en programación el array empieza en 0
    private val primerDiaSemana = mes.atDay(1).dayOfWeek.value - 1
    private val diasEnMes = mes.lengthOfMonth()

    // El total de celdas es: Huecos vacíos iniciales + Días del mes
    override fun getItemCount(): Int = primerDiaSemana + diasEnMes

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DiaViewHolder {
        val binding = ItemDiaCalendarioBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DiaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DiaViewHolder, position: Int) {
        // Si estamos en los huecos vacíos del principio
        if (position < primerDiaSemana) {
            holder.binding.root.visibility = View.INVISIBLE
            return
        }

        holder.binding.root.visibility = View.VISIBLE

        // Calculamos el número de día real (1, 2, 3...)
        val diaNumero = position - primerDiaSemana + 1
        holder.binding.tvDiaNumero.text = diaNumero.toString()

        // Filtrar gastos de ESTE día concreto
        val fechaDia = mes.atDay(diaNumero)
        val gastosDelDia = gastos.filter {
            val fechaGasto = java.time.Instant.ofEpochMilli(it.fecha).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            fechaGasto == fechaDia
        }

        val totalDia = gastosDelDia.sumOf { it.cantidad }

        if (totalDia > 0) {
            holder.binding.tvGastoDia.text = Formato.formatearMoneda(totalDia)
            holder.binding.tvGastoDia.visibility = View.VISIBLE

            // Colorear si hay gasto (Verde suave)
            holder.binding.layoutFondoDia.setBackgroundColor(Color.parseColor("#E0F2F1"))
        } else {
            holder.binding.tvGastoDia.visibility = View.GONE
            holder.binding.layoutFondoDia.setBackgroundColor(Color.WHITE)
        }

        // Marcar el día de HOY si coincide
        if (fechaDia == LocalDate.now()) {
            holder.binding.tvDiaNumero.setTextColor(Color.BLUE)
            // Borde o algo distintivo opcional
        } else {
            holder.binding.tvDiaNumero.setTextColor(Color.BLACK)
        }
    }

    class DiaViewHolder(val binding: ItemDiaCalendarioBinding) : RecyclerView.ViewHolder(binding.root)
}